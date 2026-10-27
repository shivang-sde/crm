package com.shivang.crm.modules.deal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.deal.dto.DealCreateRequest;
import com.shivang.crm.modules.deal.dto.DealLineItemCreateRequest;
import com.shivang.crm.modules.deal.dto.DealResponse;
import com.shivang.crm.modules.deal.dto.DealStageChangeRequest;
import com.shivang.crm.modules.deal.dto.DealUpdateRequest;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealCustomField;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.ForecastCategory;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.mapper.DealMapper;
import com.shivang.crm.modules.deal.repository.DealCustomFieldRepository;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.deal.repository.DealSpecifications;
import com.shivang.crm.modules.deal.repository.DealStageRepository;
import com.shivang.crm.modules.entitlement.service.EntitlementProvisioningService;
import com.shivang.crm.modules.lead.service.EntityHistoryService;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.util.UserUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DealService {

    private final DealRepository dealRepository;
    private final DealCustomFieldRepository dealCustomFieldRepository;
    private final DealLineItemRepository dealLineItemRepository;
    private final DealStageRepository dealStageRepository;
    private final DealLineItemService dealLineItemService;
    private final com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository entitlementRepository;
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;
    private final DealMapper dealMapper;

    private final ActivityService activityService;
    private final EntityHistoryService entityHistoryService;
    private final EntitlementProvisioningService entitlementProvisioningService;

    private final UserRepository userRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final com.shivang.crm.modules.rbac.service.RecordScopeGuard recordScopeGuard;
    private final com.shivang.crm.shared.service.CustomerRelationshipValidator customerRelationshipValidator;

/**
     * Create a new deal, together with its initial line items.
     *
     * <p>L3.0-P5: this whole method is one transaction (the class is
     * {@code @Transactional}, and DealLineItemService joins it rather than
     * starting its own). Deal, its lines and, for a won Deal, its entitlements
     * are therefore all-or-nothing: a line that fails validation takes the Deal
     * back down with it, so no partially sold opportunity can survive.
     *
     * <p>Ordering matters. Lines are created before provisioning, because
     * provisioning derives entitlements from the lines that exist at that
     * moment. Doing it the other way round would produce a won Deal whose
     * coverage silently misses the lines it was born with.
     */
    public DealResponse createDeal(UUID tenantId, UUID userId, DealCreateRequest request) {
        log.info("Creating deal for tenant: {}", tenantId);

        // L3.0-P2: every customer reference must resolve to a live record in this
        // tenant before anything is persisted. The deals.account_id /
        // deals.contact_id / deals.lead_id foreign keys are tenant-blind.
        customerRelationshipValidator.requireLiveAccount(request.getAccountId(), tenantId);
        customerRelationshipValidator.requireLiveContact(request.getContactId(), tenantId);
        customerRelationshipValidator.requireLiveLead(request.getLeadId(), tenantId);
        customerRelationshipValidator.requireContactBelongsToAccount(
                request.getContactId(), request.getAccountId(), tenantId);
        customerRelationshipValidator.requireTenantOwner(request.getOwnerUserId(), tenantId);

        // Map request to entity
        Deal deal = dealMapper.toEntity(request);
        deal.setTenantId(tenantId);
        deal.setCreatedBy(userId);
        deal.setOwnerId(request.getOwnerUserId() != null ? request.getOwnerUserId() : userId);
        deal.setUpdatedBy(userId);
        validateCustomData(tenantId, request.getCustomData());

        // Set stage
        if (request.getStageId() != null) {
            DealStage stage = dealStageRepository.findByIdAndTenantId(request.getStageId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Stage not found"));
            deal.setStage(stage);
        } else {
            // Use default stage if not provided
            DealStage defaultStage = dealStageRepository.findByTenantIdAndIsDefault(tenantId, true)
                .orElseThrow(() -> new RuntimeException("No default stage found for tenant"));
            deal.setStage(defaultStage);
        }

        applyStageLifecycle(deal, deal.getStage(), false, request.getClosedDate(), request.getWonReason(), request.getLostReason());

        // Authoritative initial stage entry (creation enters the initial stage)
        deal.setStageEnteredAt(Instant.now());

        // L3.0-P5: reject an incomplete won Deal before writing anything, so the
        // rejection costs nothing and leaves no trace.
        requireCommercialLinesForWonDeal(deal.getRecordCategory(), request.getLineItems());

// Save deal
        Deal savedDeal = dealRepository.save(deal);

        // L3.0-P6: DEAL.CREATED is published immediately after the Deal row is
        // written and BEFORE its initial line items, so the outbox order for an
        // atomic CLOSED_WON creation reads
        //   DEAL.CREATED -> DEAL_LINE_ITEM.CREATED... -> ENTITLEMENT.CREATED...
        // rather than announcing the Deal after its own children.
        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("source", "MANUAL");
        eventMetadata.put("actorId", userId.toString());
        eventMetadata.put("actorType", "USER");
        canonicalCrmEventPublisher.publish(
            savedDeal.getTenantId(),
            CanonicalCrmEvent.DEAL_ENTITY_TYPE,
            CanonicalCrmEvent.CREATED_EVENT_TYPE,
            savedDeal.getId(),
            eventMetadata
        );

        // L3.0-P5: initial lines, created inside this transaction. Each one goes
        // through the same validation as a standalone line item, so an Offering
        // from another tenant, an inactive Offering, or a malformed line fails
        // here and rolls the whole creation back.
        createInitialLineItems(tenantId, userId, savedDeal.getId(), request.getLineItems());

        // L3.0-P4-A: a Deal created directly in a CLOSED_WON stage is the same
        // lifecycle edge as an OPEN -> CLOSED_WON transition, so it must run the
        // identical provisioning step. Previously createDeal omitted this, so a
        // directly-created won deal silently had no entitlements.
        //
        // The prior state for a newly created deal is OPEN by definition: there
        // is no earlier stage to have been CLOSED_WON. shouldProvisionEntitlements
        // is therefore evaluated against OPEN, and provisionFromWonDeal remains
        // idempotent (it skips any line item that already has an entitlement),
        // so re-running this is safe. It runs after the lines so the entitlements
        // are derived from the complete commercial position.
        RecordCategory createdCategory = savedDeal.getRecordCategory();
        if (shouldProvisionEntitlements(RecordCategory.OPEN, createdCategory)) {
            entitlementProvisioningService.provisionFromWonDeal(tenantId, savedDeal.getId(), userId);
        }


        // Log activity
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", savedDeal.getName());
        metadata.put("stageId", savedDeal.getStage().getId());
        metadata.put("amount", savedDeal.getAmount());
        logDealActivity(tenantId, savedDeal.getId(), "DEAL_CREATED", 
            "Deal created: " + savedDeal.getName(), userId, metadata);

        return dealMapper.toResponse(savedDeal);
    }

    /**
     * Get deal by ID with tenant isolation
     */
    @Transactional(readOnly = true)
    public DealResponse getDealById(UUID id, UUID tenantId) {
        log.info("Fetching deal: {} for tenant: {}", id, tenantId);

        UUID currentUserId = com.shivang.crm.util.UserUtil.currentUserId();
        String scope = recordScopeGuard.requireScope(tenantId, currentUserId, "deal", "read");

        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));

        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, currentUserId, deal.getOwnerId(), deal.getCreatedBy());

        return dealMapper.toResponse(deal);
    }

    /**
     * List deals with filtering and pagination
     */
    @Transactional(readOnly = true)
    public Page<DealResponse> listDeals(
            UUID tenantId,
            UUID stageId,
            UUID accountId,
            UUID contactId,
            UUID ownerUserId,
            String searchTerm,
            Boolean isWon,
            Boolean isLost,
            LocalDate expectedCloseDateFrom,
            LocalDate expectedCloseDateTo,
            int page,
            int size) {

        log.info("Listing deals for tenant: {} with filters", tenantId);

        UUID currentUserId = UserUtil.currentUserId();

        String accessScope = permissionEvaluatorService.getAccessScope(currentUserId, tenantId, "deal", "read");
        log.info("Access scope for user {} on deals: {}", currentUserId, accessScope);

        List<UUID> teamUserIds = "TEAM".equals(accessScope)
            ? userRepository.findTeamUserIdsByManagerAndTenant(tenantId, currentUserId)
            : Collections.emptyList();

        Specification<Deal> spec = DealSpecifications.buildSpecification(
            tenantId,
            stageId,
            accountId,
            contactId,
            ownerUserId,
            searchTerm,
            isWon,
            isLost,
            expectedCloseDateFrom,
            expectedCloseDateTo,
            accessScope,
            currentUserId,
            teamUserIds
        );

        Pageable pageable = PageRequest.of(page, size);
        Page<Deal> deals = dealRepository.findAll(spec, pageable);

        return deals.map(dealMapper::toResponse);
    }

    /**
     * Update a deal
     */
    public DealResponse updateDeal(UUID id, UUID tenantId, UUID userId, DealUpdateRequest request) {
        log.info("Updating deal: {} for tenant: {}", id, tenantId);

        String updateScope = recordScopeGuard.requireScope(tenantId, userId, "deal", "write");
        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                updateScope, tenantId, userId, deal.getOwnerId(), deal.getCreatedBy());

        // L3.0-P2: validate the values this request would persist, not just the
        // fields it happens to carry. A partial update that changes only the
        // account must still be rejected when it would leave the existing
        // contact pointing at a different account. Null request fields mean
        // "unchanged" because DealMapper uses NullValuePropertyMappingStrategy.
        UUID resultingAccountId = request.getAccountId() != null ? request.getAccountId() : deal.getAccountId();
        UUID resultingContactId = request.getContactId() != null ? request.getContactId() : deal.getContactId();
        customerRelationshipValidator.requireLiveAccount(resultingAccountId, tenantId);
        customerRelationshipValidator.requireLiveContact(resultingContactId, tenantId);
        customerRelationshipValidator.requireContactBelongsToAccount(
                resultingContactId, resultingAccountId, tenantId);
        boolean ownerChanged = request.getOwnerUserId() != null
                && !Objects.equals(request.getOwnerUserId(), deal.getOwnerId());
        if (ownerChanged) {
            // Owner reassignment is a distinct permission from ordinary deal
            // write, exactly as it is on the dedicated assign endpoint.
            String assignScope = recordScopeGuard.requireScope(tenantId, userId, "deal", "assign");
            recordScopeGuard.assertWithinOwnerCreatorScope(
                    assignScope, tenantId, userId, deal.getOwnerId(), deal.getCreatedBy());
            customerRelationshipValidator.requireTenantOwner(request.getOwnerUserId(), tenantId);
        }

        // L3.0-P4-E: once a Deal has provisioned entitlements, its customer
        // identity is frozen. The entitlements snapshot accountId/contactId, so
        // moving the Deal would leave every provisioned entitlement pointing at
        // the previous customer. Reassigning to the same account is a no-op and
        // is deliberately allowed.
        requireAccountReassignable(tenantId, id, deal.getAccountId(), resultingAccountId);

        // Store old values for activity logging
        Map<String, Object> oldValues = new HashMap<>();
        RecordCategory previousCategory = categoryOf(deal.getStage());
        if (request.getStageId() != null && !request.getStageId().equals(deal.getStage().getId())) {
            oldValues.put("oldStageId", deal.getStage().getId());
            oldValues.put("oldStageName", deal.getStage().getName());
        }
        if (ownerChanged) {
            oldValues.put("oldOwnerId", deal.getOwnerId());
        }
        if (request.getName() != null && !request.getName().equals(deal.getName())) {
            oldValues.put("oldName", deal.getName());
        }

        // L3.0-P6: snapshot the mutable business fields so DEAL.UPDATED can be
        // emitted only when something actually changed. This mirrors the existing
        // LEAD.UPDATED convention (see LeadService.updateLead) rather than
        // inventing a second rule.
        //
        // Stage, owner and amount are deliberately excluded from the comparison:
        // stage has its own DEAL.STAGE_TRANSITIONED event, owner has
        // DEAL.OWNER_CHANGED, and amount/expectedRevenue are recalculated
        // internally whenever line items change. Emitting DEAL.UPDATED for those
        // would duplicate events the system already models more precisely.
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", deal.getName());
        before.put("description", deal.getDescription());
        before.put("accountId", deal.getAccountId());
        before.put("contactId", deal.getContactId());
        before.put("leadId", deal.getLeadId());
        before.put("expectedCloseDate", deal.getExpectedCloseDate());
        before.put("closedDate", deal.getClosedDate());
        before.put("probability", deal.getProbability());
        before.put("forecastCategory", deal.getForecastCategory());
        before.put("nextStep", deal.getNextStep());
        before.put("dealType", deal.getDealType());
        before.put("leadSource", deal.getLeadSource());
        before.put("campaignSource", deal.getCampaignSource());
        before.put("wonReason", deal.getWonReason());
        before.put("lostReason", deal.getLostReason());
        before.put("customData", deal.getCustomData());

        if (request.getAmount() != null && (deal.getAmount() == null || request.getAmount().compareTo(deal.getAmount()) != 0)
                && dealLineItemRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantId, id)) {
            throw new BusinessException("DEAL_AMOUNT_MANAGED_BY_LINE_ITEMS", "Deal amount is managed by line items");
        }

        // A partial update keeps the existing custom data when the request omits
        // it, so validation must run against what would actually be persisted.
        Map<String, Object> resultingCustomData = request.getCustomData() != null
                ? request.getCustomData()
                : deal.getCustomData();
        validateCustomData(tenantId, resultingCustomData);

        DealStage requestedStage = null;
        boolean stageChanged = false;
        if (request.getStageId() != null) {
            requestedStage = dealStageRepository.findByIdAndTenantId(request.getStageId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Stage not found"));
            stageChanged = !request.getStageId().equals(deal.getStage().getId());
        }
        RecordCategory resultingCategory = requestedStage != null
                ? categoryOf(requestedStage)
                : previousCategory;
        requireStageCommercialInvariants(tenantId, id, previousCategory, resultingCategory);

        // Update entity
        dealMapper.updateEntity(request, deal);
        if (ownerChanged) {
            deal.setOwnerId(request.getOwnerUserId());
        }
        deal.setUpdatedBy(userId);

        // Update stage if needed
        if (requestedStage != null) {
            deal.setStage(requestedStage);
            if (stageChanged) {
                // Actual stage transition -> authoritative current-stage entry
                deal.setStageEnteredAt(Instant.now());
            }
        }

        applyStageLifecycle(deal, deal.getStage(), stageChanged, request.getClosedDate(), request.getWonReason(), request.getLostReason());

        Deal updatedDeal = dealRepository.save(deal);

        RecordCategory newCategory = updatedDeal.getRecordCategory();
        if (shouldProvisionEntitlements(previousCategory, newCategory)) {
            entitlementProvisioningService.provisionFromWonDeal(tenantId, updatedDeal.getId(), userId);
        }

        // Log activity
        if (!oldValues.isEmpty()) {
            Map<String, Object> metadata = new HashMap<>(oldValues);
            metadata.put("newStageId", updatedDeal.getStage().getId());
            metadata.put("newStageName", updatedDeal.getStage().getName());
            metadata.put("newName", updatedDeal.getName());
            logDealActivity(tenantId, updatedDeal.getId(), "DEAL_UPDATED", 
                "Deal updated", userId, metadata);
        }

        // D1 stage-history consistency: an actual stage transition performed
        // through the PUT update path must write the same authoritative
        // STAGE_CHANGED history entry as changeStage (PATCH). Skipping this
        // left time-in-stage / stage-movement analytics dependent on which
        // update path moved the deal.
        if (stageChanged) {
            Map<String, Object> historyMetadata = stageChangeMetadata(
                    (UUID) oldValues.get("oldStageId"),
                    (String) oldValues.get("oldStageName"),
                    previousCategory,
                    updatedDeal);
            logDealHistory(tenantId, updatedDeal.getId(), "STAGE_CHANGED",
                    "Stage changed from " + oldValues.get("oldStageName") + " to " + updatedDeal.getStage().getName(),
                    userId, historyMetadata);
            publishStageTransition(tenantId, userId, updatedDeal,
                    (UUID) oldValues.get("oldStageId"), (String) oldValues.get("oldStageName"));
        }
        if (ownerChanged) {
            publishOwnerChanged(tenantId, userId, updatedDeal, (UUID) oldValues.get("oldOwnerId"));
        }

        publishDealUpdatedIfChanged(tenantId, userId, updatedDeal, before, stageChanged);

        return dealMapper.toResponse(updatedDeal);
    }

    /**
     * L3.0-P6: emits the single canonical {@code DEAL.UPDATED} event.
     *
     * <p>Authoritative producer for {@code DEAL.UPDATED}. Deliberately the only
     * place this event is published from, which is what keeps a Deal update from
     * emitting twice.
     *
     * <p>Not emitted when:
     * <ul>
     *   <li>nothing changed, so the event would describe a mutation that never
     *       happened;</li>
     *   <li>the stage moved. {@code applyStageLifecycle} rewrites probability,
     *       forecastCategory and the closed dates as a consequence of the stage,
     *       so a stage transition always "changes" several derived fields.
     *       {@code DEAL.STAGE_TRANSITIONED} already reports that transition with
     *       the before and after stage, and emitting UPDATED as well would make a
     *       single stage move look like two unrelated mutations. When a request
     *       changes the stage and a field such as the won reason together, the
     *       stage transition remains the authoritative record.</li>
     * </ul>
     * Owner changes are excluded from the compared field set entirely, because
     * {@code DEAL.OWNER_CHANGED} owns them.
     *
     * Creation is not routed through here at all, so a newly created Deal produces
     * {@code DEAL.CREATED} only and never a spurious {@code DEAL.UPDATED}.
     */
    private void publishDealUpdatedIfChanged(UUID tenantId, UUID userId, Deal updatedDeal,
            Map<String, Object> before, boolean stageChanged) {
        if (stageChanged) {
            return;
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", updatedDeal.getName());
        after.put("description", updatedDeal.getDescription());
        after.put("accountId", updatedDeal.getAccountId());
        after.put("contactId", updatedDeal.getContactId());
        after.put("leadId", updatedDeal.getLeadId());
        after.put("expectedCloseDate", updatedDeal.getExpectedCloseDate());
        after.put("closedDate", updatedDeal.getClosedDate());
        after.put("probability", updatedDeal.getProbability());
        after.put("forecastCategory", updatedDeal.getForecastCategory());
        after.put("nextStep", updatedDeal.getNextStep());
        after.put("dealType", updatedDeal.getDealType());
        after.put("leadSource", updatedDeal.getLeadSource());
        after.put("campaignSource", updatedDeal.getCampaignSource());
        after.put("wonReason", updatedDeal.getWonReason());
        after.put("lostReason", updatedDeal.getLostReason());
        after.put("customData", updatedDeal.getCustomData());

        List<String> changedFields = new ArrayList<>();
        before.forEach((field, oldValue) -> {
            if (!Objects.equals(oldValue, after.get(field))) {
                changedFields.add(field);
            }
        });
        if (changedFields.isEmpty()) {
            return;
        }

        boolean workflowCaused = com.shivang.crm.shared.event.CausalEventContext.get() != null;
        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("actorId", userId.toString());
        eventMetadata.put("actorType", workflowCaused ? "SYSTEM" : "USER");
        eventMetadata.put("source", workflowCaused ? "WORKFLOW" : "MANUAL");
        eventMetadata.put("changedFields", changedFields);
        // Stable identifiers only: enough for a consumer to resolve the Deal's
        // customer without the event carrying the entity itself.
        putIfPresent(eventMetadata, "accountId", updatedDeal.getAccountId());
        putIfPresent(eventMetadata, "contactId", updatedDeal.getContactId());
        putIfPresent(eventMetadata, "stageId", updatedDeal.getStage() == null ? null : updatedDeal.getStage().getId());

        canonicalCrmEventPublisher.publish(
                updatedDeal.getTenantId() != null ? updatedDeal.getTenantId() : tenantId,
                CanonicalCrmEvent.DEAL_ENTITY_TYPE,
                CanonicalCrmEvent.UPDATED_EVENT_TYPE,
                updatedDeal.getId(),
                eventMetadata);
    }

    /**
     * Omits absent optional identifiers rather than writing nulls, which keeps
     * event metadata compact and matches P1's null-tolerant metadata handling.
     */
    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value.toString());
        }
    }

    private void validateCustomData(UUID tenantId, Map<String, Object> customData) {
        List<DealCustomField> activeFields = dealCustomFieldRepository.findActiveFieldsByTenant(tenantId);
        Map<String, DealCustomField> fieldByKey = activeFields.stream()
            .collect(Collectors.toMap(DealCustomField::getFieldKey, Function.identity()));

        for (DealCustomField field : activeFields) {
            if (Boolean.TRUE.equals(field.getIsRequired())) {
                Object value = customData == null ? null : customData.get(field.getFieldKey());
                if (value == null || isBlankValue(value)) {
                    throw new BusinessException("CUSTOM_FIELD_REQUIRED", "Custom field '" + field.getFieldLabel() + "' is required");
                }
            }
        }

        if (customData == null || customData.isEmpty()) {
            return;
        }

        for (DealCustomField field : activeFields) {
            if (Boolean.TRUE.equals(field.getIsRequired())) {
                Object value = customData.get(field.getFieldKey());
                if (value == null || isBlankValue(value)) {
                    throw new BusinessException("CUSTOM_FIELD_REQUIRED", "Custom field '" + field.getFieldLabel() + "' is required");
                }
            }
        }

        for (Map.Entry<String, Object> entry : customData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            DealCustomField field = fieldByKey.get(key);
            if (field == null) {
                throw new BusinessException("INVALID_CUSTOM_FIELD", "Custom field '" + key + "' is not valid for deals");
            }

            if (value == null) {
                if (Boolean.TRUE.equals(field.getIsRequired())) {
                    throw new BusinessException("CUSTOM_FIELD_REQUIRED", "Custom field '" + field.getFieldLabel() + "' is required");
                }
                continue;
            }

            String fieldType = field.getFieldType() != null ? field.getFieldType().trim().toUpperCase() : "";
            switch (fieldType) {
                case "TEXT", "TEXTAREA", "EMAIL", "PHONE", "URL" -> validateStringField(field, value);
                case "NUMBER" -> validateNumberField(field, value);
                case "BOOLEAN" -> validateBooleanField(field, value);
                case "DATE" -> validateDateField(field, value);
                case "SELECT" -> validateSelectField(field, value);
                case "MULTISELECT" -> validateMultiSelectField(field, value);
                default -> throw new BusinessException("INVALID_CUSTOM_FIELD_TYPE", "Unsupported field type '" + field.getFieldType() + "' for custom field '" + field.getFieldLabel() + "'");
            }
        }
    }

    private boolean isBlankValue(Object value) {
        if (value instanceof String stringValue) {
            return stringValue.isBlank();
        }
        if (value instanceof Collection<?> collectionValue) {
            return collectionValue.isEmpty();
        }
        return false;
    }

    private void validateStringField(DealCustomField field, Object value) {
        if (!(value instanceof String stringValue)) {
            throw new BusinessException("INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + field.getFieldLabel() + "' must be a string");
        }
        if (Boolean.TRUE.equals(field.getIsRequired()) && stringValue.isBlank()) {
            throw new BusinessException("CUSTOM_FIELD_REQUIRED", "Custom field '" + field.getFieldLabel() + "' is required");
        }

        if ("EMAIL".equals(field.getFieldType()) && !stringValue.contains("@")) {
            throw new BusinessException("INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + field.getFieldLabel() + "' must be a valid email");
        }
        if ("URL".equals(field.getFieldType())) {
            try {
                new URL(stringValue);
            } catch (MalformedURLException e) {
                throw new BusinessException("INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + field.getFieldLabel() + "' must be a valid URL");
            }
        }
    }

    private void validateNumberField(DealCustomField field, Object value) {
        if (value instanceof Number) {
            return;
        }
        if (value instanceof String stringValue) {
            if (stringValue.isBlank()) {
                if (Boolean.TRUE.equals(field.getIsRequired())) {
                    throw new BusinessException("CUSTOM_FIELD_REQUIRED", "Custom field '" + field.getFieldLabel() + "' is required");
                }
                return;
            }
            try {
                new java.math.BigDecimal(stringValue);
                return;
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        throw new BusinessException("INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + field.getFieldLabel() + "' must be a number");
    }

    private void validateBooleanField(DealCustomField field, Object value) {
        if (value instanceof Boolean) {
            return;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim().toLowerCase();
            if ("true".equals(normalized) || "false".equals(normalized)) {
                return;
            }
        }
        throw new BusinessException("INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + field.getFieldLabel() + "' must be a boolean");
    }

    private void validateDateField(DealCustomField field, Object value) {
        if (value instanceof String stringValue) {
            try {
                LocalDate.parse(stringValue);
                return;
            } catch (DateTimeParseException e) {
                throw new BusinessException("INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + field.getFieldLabel() + "' must be a valid date");
            }
        }
        throw new BusinessException("INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + field.getFieldLabel() + "' must be a date string");
    }

    private void validateSelectField(DealCustomField field, Object value) {
        if (!(value instanceof String stringValue)) {
            throw new BusinessException("INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + field.getFieldLabel() + "' must be a string");
        }
        if (!isAllowedOption(field, stringValue)) {
            throw new BusinessException("INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + field.getFieldLabel() + "' contains an invalid selection");
        }
    }

    private void validateMultiSelectField(DealCustomField field, Object value) {
        if (!(value instanceof Collection<?> collection)) {
            throw new BusinessException("INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + field.getFieldLabel() + "' must be an array");
        }
        for (Object item : collection) {
            if (!(item instanceof String stringValue) || !isAllowedOption(field, stringValue)) {
                throw new BusinessException("INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + field.getFieldLabel() + "' contains an invalid selection");
            }
        }
    }

    private boolean isAllowedOption(DealCustomField field, String value) {
        if (field.getOptionsJson() == null || field.getOptionsJson().isEmpty()) {
            return false;
        }
        return field.getOptionsJson().stream()
            .anyMatch(option -> value.equals(option.get("value")));
    }

    /**
     * Change deal stage
     */
    public DealResponse changeStage(UUID id, UUID tenantId, UUID stageId, UUID userId) {
        return changeStage(id, tenantId, DealStageChangeRequest.builder().stageId(stageId).build(), userId);
    }

    /**
     * Change deal stage
     */
    public DealResponse changeStage(UUID id, UUID tenantId, DealStageChangeRequest request, UUID userId) {
        UUID stageId = request.getStageId();
        log.info("Changing stage of deal: {} to: {} for tenant: {}", id, stageId, tenantId);

        String stageScope = recordScopeGuard.requireScope(tenantId, userId, "deal", "write");
        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                stageScope, tenantId, userId, deal.getOwnerId(), deal.getCreatedBy());

        DealStage newStage = dealStageRepository.findByIdAndTenantId(stageId, tenantId)
            .orElseThrow(() -> new RuntimeException("Stage not found"));

        UUID oldStageId = deal.getStage().getId();
        String oldStageName = deal.getStage().getName();
        RecordCategory oldCategory = categoryOf(deal.getStage());
        requireStageCommercialInvariants(tenantId, id, oldCategory, categoryOf(newStage));
        deal.setStage(newStage);
        if (!oldStageId.equals(newStage.getId())) {
            // Actual stage transition -> authoritative current-stage entry
            deal.setStageEnteredAt(Instant.now());
        }
        deal.setUpdatedBy(userId);
        applyStageLifecycle(deal, newStage, true, request.getClosedDate(), request.getWonReason(), request.getLostReason());

        Deal updatedDeal = dealRepository.save(deal);

        boolean stageChanged = !oldStageId.equals(newStage.getId());
        if (stageChanged) {
            publishStageTransition(tenantId, userId, updatedDeal, oldStageId, oldStageName);
        }

        RecordCategory previousCategory = oldCategory;
        RecordCategory newCategory = updatedDeal.getRecordCategory();
        if (shouldProvisionEntitlements(previousCategory, newCategory)) {
            entitlementProvisioningService.provisionFromWonDeal(tenantId, updatedDeal.getId(), userId);
        }

        Map<String, Object> metadata = stageChangeMetadata(oldStageId, oldStageName, oldCategory, updatedDeal);
        if (stageChanged) {
            logDealActivity(tenantId, updatedDeal.getId(), "STAGE_CHANGED",
                "Stage changed from " + oldStageName + " to " + newStage.getName(), userId, metadata);
            logDealHistory(tenantId, updatedDeal.getId(), "STAGE_CHANGED",
                "Stage changed from " + oldStageName + " to " + newStage.getName(), userId, metadata);
        }

        if (newStage.isWonStage()) {
            logDealActivity(tenantId, updatedDeal.getId(), "DEAL_WON",
                "Deal won: " + updatedDeal.getName(), userId, metadata);
        } else if (newStage.isLostStage()) {
            logDealActivity(tenantId, updatedDeal.getId(), "DEAL_LOST",
                "Deal lost: " + updatedDeal.getName(), userId, metadata);
        }

        return dealMapper.toResponse(updatedDeal);
    }

    /**
     * Mark deal as won
     */
    public DealResponse markDealWon(UUID id, UUID tenantId, UUID userId) {
        return markDealWon(id, tenantId, userId, null);
    }

    /**
     * Mark deal as won
     */
    public DealResponse markDealWon(UUID id, UUID tenantId, UUID userId, String wonReason) {
        log.info("Marking deal as won: {} for tenant: {}", id, tenantId);

        String wonScope = recordScopeGuard.requireScope(tenantId, userId, "deal", "write");
        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                wonScope, tenantId, userId, deal.getOwnerId(), deal.getCreatedBy());

        if (deal.isWon()) {
            throw new BusinessException("ALREADY_WON", "Deal is already marked as won");
        }

        DealStage wonStage = dealStageRepository.findFirstByTenantIdAndRecordCategoryOrderByDisplayOrder(tenantId, RecordCategory.CLOSED_WON)
            .orElseThrow(() -> new BusinessException("CLOSED_WON_STAGE_NOT_FOUND", "No closed-won stage configured for this tenant"));

        return changeStage(id, tenantId, DealStageChangeRequest.builder()
            .stageId(wonStage.getId())
            .wonReason(wonReason)
            .build(), userId);
    }

    /**
     * Mark deal as lost
     */
    public DealResponse markDealLost(UUID id, UUID tenantId, UUID userId) {
        return markDealLost(id, tenantId, userId, null);
    }

    /**
     * Mark deal as lost
     */
    public DealResponse markDealLost(UUID id, UUID tenantId, UUID userId, String lostReason) {
        log.info("Marking deal as lost: {} for tenant: {}", id, tenantId);

        String lostScope = recordScopeGuard.requireScope(tenantId, userId, "deal", "write");
        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                lostScope, tenantId, userId, deal.getOwnerId(), deal.getCreatedBy());

        if (deal.isLost()) {
            throw new BusinessException("ALREADY_LOST", "Deal is already marked as lost");
        }

        DealStage lostStage = dealStageRepository.findFirstByTenantIdAndRecordCategoryOrderByDisplayOrder(tenantId, RecordCategory.CLOSED_LOST)
            .orElseThrow(() -> new BusinessException("CLOSED_LOST_STAGE_NOT_FOUND", "No closed-lost stage configured for this tenant"));

        return changeStage(id, tenantId, DealStageChangeRequest.builder()
            .stageId(lostStage.getId())
            .lostReason(lostReason)
            .build(), userId);
    }

    /**
     * Assign deal to a user
     */
    public DealResponse assignDeal(UUID id, UUID tenantId, UUID ownerUserId, UUID userId) {
        log.info("Assigning deal: {} to user: {} for tenant: {}", id, ownerUserId, tenantId);

        String assignScope = recordScopeGuard.requireScope(tenantId, userId, "deal", "assign");
        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                assignScope, tenantId, userId, deal.getOwnerId(), deal.getCreatedBy());

        UUID oldOwner = deal.getOwnerId();
        boolean ownerChanged = !java.util.Objects.equals(oldOwner, ownerUserId);
        if (ownerChanged) {
            customerRelationshipValidator.requireTenantOwner(ownerUserId, tenantId);
        }
        deal.setOwnerId(ownerUserId);
        deal.setUpdatedBy(userId);

        Deal updatedDeal = dealRepository.save(deal);

        if (ownerChanged) {
            publishOwnerChanged(tenantId, userId, updatedDeal, oldOwner);
        }

        // Log activity
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("oldOwnerId", oldOwner);
        metadata.put("newOwnerId", ownerUserId);
        logDealActivity(tenantId, updatedDeal.getId(), "OWNER_CHANGED", 
            "Deal owner changed", userId, metadata);

        return dealMapper.toResponse(updatedDeal);
    }

/**
     * L3.0-P4-H: Deal deletion is a soft delete.
 *
     * <p>This was a hard {@code repository.delete()} before P4. A Deal is the
     * commercial origin of its line items and of every entitlement provisioned
     * from them, and it is referenced by activities, notes and entity_history
     * rows that must remain queryable for customer history. A hard delete either
     * fails on the deal_line_items foreign key or destroys that history outright.
 *
     * <p>Line items and entitlements are deliberately NOT cascaded or mutated.
     * They keep pointing at this Deal id, which is what keeps the sold position
     * reconstructable; the P2 deleted-aware finders and specifications already
     * exclude this row from normal active reads, so nothing downstream can treat
     * a deleted Deal as live.
 */
    public void deleteDeal(UUID id, UUID tenantId, UUID userId) {
        log.info("Deleting deal: {} for tenant: {}", id, tenantId);

        String deleteScope = recordScopeGuard.requireScope(tenantId, userId, "deal", "delete");
        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                deleteScope, tenantId, userId, deal.getOwnerId(), deal.getCreatedBy());

        deal.softDelete(userId);
        deal.setUpdatedBy(userId);
        dealRepository.save(deal);
        log.info("Deal soft deleted: {}", id);
    }

    /**
     * RBAC-7: ensures the parent deal is within the caller's scope for the
     * given action before nested resources (notes, activities, line items)
     * are served or mutated. Used by controllers for nested endpoints.
     */
    public void assertDealAccessible(UUID tenantId, UUID dealId, String action) {
        UUID currentUserId = com.shivang.crm.util.UserUtil.currentUserId();
        String scope = recordScopeGuard.requireScope(tenantId, currentUserId, "deal", action);

        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));

        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, currentUserId, deal.getOwnerId(), deal.getCreatedBy());
    }

    /**
     * Get deals by account
     */
    @Transactional(readOnly = true)
    public List<DealResponse> getDealsByAccount(UUID accountId, UUID tenantId) {
        log.info("Fetching deals for account: {} in tenant: {}", accountId, tenantId);

        Specification<Deal> spec = DealSpecifications.byTenantId(tenantId)
            .and(DealSpecifications.notDeleted())
            .and(DealSpecifications.byAccountId(accountId));

        List<Deal> deals = dealRepository.findAll(spec);
        return dealMapper.toResponseList(deals);
    }

    /**
     * Get open deals for a user
     */
    @Transactional(readOnly = true)
    public List<DealResponse> getOpenDealsForUser(UUID userId, UUID tenantId) {
        log.info("Fetching open deals for user: {} in tenant: {}", userId, tenantId);

        Specification<Deal> spec = DealSpecifications.byTenantId(tenantId)
            .and(DealSpecifications.notDeleted())
            .and(DealSpecifications.byOwnerUserId(userId))
            .and(DealSpecifications.byIsWon(false))
            .and(DealSpecifications.byIsLost(false));

        List<Deal> deals = dealRepository.findAll(spec);
        return dealMapper.toResponseList(deals);
    }

    /**
     * Get won deals
     */
    @Transactional(readOnly = true)
    public Page<DealResponse> getWonDeals(UUID tenantId, int page, int size) {
        log.info("Fetching won deals for tenant: {}", tenantId);

        Specification<Deal> spec = DealSpecifications.byTenantId(tenantId)
            .and(DealSpecifications.notDeleted())
            .and(DealSpecifications.byIsWon(true));

        Pageable pageable = PageRequest.of(page, size);
        Page<Deal> deals = dealRepository.findAll(spec, pageable);

        return deals.map(dealMapper::toResponse);
    }

    /**
     * Get lost deals
     */
    @Transactional(readOnly = true)
    public Page<DealResponse> getLostDeals(UUID tenantId, int page, int size) {
        log.info("Fetching lost deals for tenant: {}", tenantId);

        Specification<Deal> spec = DealSpecifications.byTenantId(tenantId)
            .and(DealSpecifications.notDeleted())
            .and(DealSpecifications.byIsLost(true));

        Pageable pageable = PageRequest.of(page, size);
        Page<Deal> deals = dealRepository.findAll(spec, pageable);

        return deals.map(dealMapper::toResponse);
    }

    /**
     * Log deal activity
     */
    private void logDealActivity(UUID tenantId, UUID dealId, String activityType, 
                                  String description, UUID performedBy, Map<String, Object> metadata) {
        activityService.logActivity(tenantId, dealId, "DEAL", activityType, description, performedBy, metadata);
        log.debug("Deal activity logged: {} for deal: {}", activityType, dealId);
    }

    /**
     * Get total deal value for a user
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalDealValue(UUID userId, UUID tenantId) {
        List<Deal> deals = dealRepository.findAll(
            DealSpecifications.byTenantId(tenantId)
                .and(DealSpecifications.notDeleted())
                .and(DealSpecifications.byOwnerUserId(userId))
                .and(DealSpecifications.byIsWon(false))
                .and(DealSpecifications.byIsLost(false))
        );

        return deals.stream()
            .map(Deal::getAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * One commercial transition rule shared by PATCH and PUT stage changes:
     * a win must have lines to provision from, and a won Deal with provisioned
     * entitlements must not be silently reopened away from them.
     */
    private void requireStageCommercialInvariants(UUID tenantId, UUID dealId,
            RecordCategory oldCategory, RecordCategory newCategory) {
        if (oldCategory != RecordCategory.CLOSED_WON && newCategory == RecordCategory.CLOSED_WON
                && !dealLineItemRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId)) {
            throw new BusinessException("DEAL_WON_REQUIRES_LINE_ITEMS",
                    "A deal entering a won stage must have at least one active line item");
        }
        if (oldCategory == RecordCategory.CLOSED_WON && newCategory != RecordCategory.CLOSED_WON
                && entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId)) {
            throw new BusinessException("DEAL_REOPEN_BLOCKED_BY_ENTITLEMENTS",
                    "A won deal with provisioned entitlements cannot be reopened");
        }
    }

    private RecordCategory categoryOf(DealStage stage) {
        return stage == null || stage.getRecordCategory() == null
                ? RecordCategory.OPEN
                : stage.getRecordCategory();
    }

    private void publishStageTransition(UUID tenantId, UUID actorId, Deal updatedDeal,
            UUID oldStageId, String oldStageName) {
        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("previousStageId", oldStageId.toString());
        eventMetadata.put("newStageId", updatedDeal.getStage().getId().toString());
        eventMetadata.put("previousStage", oldStageName);
        eventMetadata.put("newStage", updatedDeal.getStage().getName());
        eventMetadata.put("actorId", actorId.toString());
        eventMetadata.put("actorType", "USER");
        canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.DEAL_ENTITY_TYPE,
                CanonicalCrmEvent.STAGE_TRANSITIONED_EVENT_TYPE,
                updatedDeal.getId(),
                eventMetadata);
    }

    private void publishOwnerChanged(UUID tenantId, UUID actorId, Deal updatedDeal, UUID oldOwner) {
        Map<String, Object> eventMetadata = new HashMap<>();
        if (oldOwner != null) {
            eventMetadata.put("previousOwnerId", oldOwner.toString());
        }
        if (updatedDeal.getOwnerId() != null) {
            eventMetadata.put("newOwnerId", updatedDeal.getOwnerId().toString());
        }
        eventMetadata.put("actorId", actorId.toString());
        eventMetadata.put("actorType", "USER");
        canonicalCrmEventPublisher.publish(
                updatedDeal.getTenantId() != null ? updatedDeal.getTenantId() : tenantId,
                CanonicalCrmEvent.DEAL_ENTITY_TYPE,
                CanonicalCrmEvent.OWNER_CHANGED_EVENT_TYPE,
                updatedDeal.getId(),
                eventMetadata);
    }

    private void applyStageLifecycle(
            Deal deal,
            DealStage stage,
            boolean stageTransition,
            LocalDate requestedClosedDate,
            String wonReason,
            String lostReason) {

        RecordCategory category = stage.getRecordCategory() == null ? RecordCategory.OPEN : stage.getRecordCategory();

        switch (category) {
            case CLOSED_WON -> applyClosedWonLifecycle(deal, requestedClosedDate, wonReason);
            case CLOSED_LOST -> applyClosedLostLifecycle(deal, requestedClosedDate, lostReason);
            case OPEN -> applyOpenLifecycle(deal, stage, stageTransition);
        }

        validateProbability(deal.getProbability());
        deal.setExpectedRevenue(calculateExpectedRevenue(deal.getAmount(), deal.getProbability()));
    }

    private void applyClosedWonLifecycle(Deal deal, LocalDate requestedClosedDate, String wonReason) {
        deal.setProbability(100);
        deal.setForecastCategory(ForecastCategory.CLOSED);
        deal.setClosedDate(requestedClosedDate != null ? requestedClosedDate : LocalDate.now());
        if (wonReason != null) {
            deal.setWonReason(wonReason);
        }
        deal.setLostReason(null);
    }

    private void applyClosedLostLifecycle(Deal deal, LocalDate requestedClosedDate, String lostReason) {
        if (lostReason != null) {
            deal.setLostReason(lostReason);
        }
        if (deal.getLostReason() == null || deal.getLostReason().isBlank()) {
            throw new BusinessException("LOST_REASON_REQUIRED", "Lost reason is required for closed-lost deals");
        }
        deal.setProbability(0);
        deal.setForecastCategory(ForecastCategory.CLOSED);
        deal.setClosedDate(requestedClosedDate != null ? requestedClosedDate : LocalDate.now());
        deal.setWonReason(null);
    }

    private void applyOpenLifecycle(Deal deal, DealStage stage, boolean stageTransition) {
        if (stageTransition || deal.getProbability() == null) {
            deal.setProbability(stage.getDefaultProbability() != null ? stage.getDefaultProbability() : 0);
        }
        if (deal.getForecastCategory() == null || ForecastCategory.CLOSED.equals(deal.getForecastCategory())) {
            deal.setForecastCategory(stage.getDefaultForecastCategory() != null
                ? stage.getDefaultForecastCategory()
                : ForecastCategory.PIPELINE);
        }
        deal.setClosedDate(null);
        deal.setWonReason(null);
        deal.setLostReason(null);
    }

    private BigDecimal calculateExpectedRevenue(BigDecimal amount, Integer probability) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        int normalizedProbability = probability == null ? 0 : probability;
        return amount
            .multiply(BigDecimal.valueOf(normalizedProbability))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void validateProbability(Integer probability) {
        if (probability == null) {
            return;
        }
        if (probability < 0 || probability > 100) {
            throw new BusinessException("INVALID_PROBABILITY", "Probability must be between 0 and 100");
        }
    }

    private void logDealHistory(UUID tenantId, UUID dealId, String eventType,
                                String description, UUID performedBy, Map<String, Object> metadata) {
        entityHistoryService.logHistoryWithMetadataRequired(
            tenantId, dealId, "DEAL", eventType, description, performedBy, metadata);
    }

    /**
     * Single source of truth for the STAGE_CHANGED metadata shape. Used by
     * both {@link #changeStage} (PATCH) and the PUT update path so stage
     * history is identical regardless of how the transition was performed.
     */
    private Map<String, Object> stageChangeMetadata(UUID oldStageId, String oldStageName,
                                                    RecordCategory oldCategory, Deal updatedDeal) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("oldStageId", oldStageId);
        metadata.put("oldStageName", oldStageName);
        metadata.put("oldRecordCategory", oldCategory);
        metadata.put("newStageId", updatedDeal.getStage().getId());
        metadata.put("newStageName", updatedDeal.getStage().getName());
        metadata.put("newRecordCategory", updatedDeal.getStage().getRecordCategory());
        metadata.put("probability", updatedDeal.getProbability());
        metadata.put("forecastCategory", updatedDeal.getForecastCategory());
        metadata.put("closedDate", updatedDeal.getClosedDate());
        return metadata;
    }

    private boolean shouldProvisionEntitlements(RecordCategory previousCategory, RecordCategory newCategory) {
        return previousCategory != RecordCategory.CLOSED_WON && newCategory == RecordCategory.CLOSED_WON;
    }

    /**
     * L3.0-P5: a won Deal is born commercially complete, so it may not be created
     * without the lines its coverage will be derived from.
     *
     * <p>Checked before the Deal is written, so an incomplete won Deal is rejected
     * outright rather than persisted in a state that can never be repaired by
     * adding lines. This is what removed P4's transitional exception: the old
     * "closed Deal with no entitlements may accept one line" window was only
     * ever needed because creation could not carry lines in the first place.
     *
     * <p>Only CLOSED_WON is constrained. A CLOSED_LOST Deal legitimately has no
     * commercial coverage to provision, and an OPEN Deal has not sold anything
     * yet, so both may be created without lines as before.
     */
    private void requireCommercialLinesForWonDeal(RecordCategory recordCategory, List<DealLineItemCreateRequest> lineItems) {
        if (recordCategory != RecordCategory.CLOSED_WON) {
            return;
        }
        if (lineItems == null || lineItems.isEmpty()) {
            throw new BusinessException("DEAL_WON_REQUIRES_LINE_ITEMS",
                    "A deal created in a won stage must include at least one line item, "
                            + "so its customer entitlements can be provisioned in the same transaction");
        }
    }

    /**
     * L3.0-P5: creates the Deal's initial lines. Any failure propagates out of
     * createDeal and rolls back the whole creation, including the Deal row and
     * any lines already written before the failure.
     */
    private void createInitialLineItems(UUID tenantId, UUID userId, UUID dealId, List<DealLineItemCreateRequest> lineItems) {
        if (lineItems == null || lineItems.isEmpty()) {
            return;
        }
        for (DealLineItemCreateRequest lineItem : lineItems) {
            dealLineItemService.persistLineItem(tenantId, dealId, userId, lineItem);
        }
    }

    /**
     * L3.0-P4-E: rejects reassigning a Deal to a different Account once it has
     * provisioned entitlements. The existence check is tenant-scoped, so another
     * tenant's entitlements can never freeze this Deal.
     */
    private void requireAccountReassignable(UUID tenantId, UUID dealId, UUID currentAccountId, UUID newAccountId) {
        if (java.util.Objects.equals(currentAccountId, newAccountId)) {
            return;
        }
        if (!entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId)) {
            return;
        }
        throw new BusinessException("DEAL_ACCOUNT_REASSIGNMENT_BLOCKED",
                "Deal has provisioned entitlements and cannot be reassigned to a different account");
    }
}
