package com.shivang.crm.modules.deal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;
    private final DealMapper dealMapper;

    private final ActivityService activityService;
    private final EntityHistoryService entityHistoryService;
    private final EntitlementProvisioningService entitlementProvisioningService;

    private final UserRepository userRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final com.shivang.crm.modules.rbac.service.RecordScopeGuard recordScopeGuard;

    /**
     * Create a new deal
     */
    public DealResponse createDeal(UUID tenantId, UUID userId, DealCreateRequest request) {
        log.info("Creating deal for tenant: {}", tenantId);

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

        // Save deal
        Deal savedDeal = dealRepository.save(deal);

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

        Deal deal = dealRepository.findByIdAndTenantId(id, tenantId)
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
        Deal deal = dealRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                updateScope, tenantId, userId, deal.getOwnerId(), deal.getCreatedBy());

        // Store old values for activity logging
        Map<String, Object> oldValues = new HashMap<>();
        RecordCategory previousCategory = deal.getRecordCategory();
        if (request.getStageId() != null && !request.getStageId().equals(deal.getStage().getId())) {
            oldValues.put("oldStageId", deal.getStage().getId());
            oldValues.put("oldStageName", deal.getStage().getName());
        }
        if (request.getOwnerUserId() != null && !request.getOwnerUserId().equals(deal.getOwnerId())) {
            oldValues.put("oldOwnerId", deal.getOwnerId());
        }
        if (request.getName() != null && !request.getName().equals(deal.getName())) {
            oldValues.put("oldName", deal.getName());
        }

        if (request.getAmount() != null && (deal.getAmount() == null || request.getAmount().compareTo(deal.getAmount()) != 0)
                && dealLineItemRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantId, id)) {
            throw new BusinessException("DEAL_AMOUNT_MANAGED_BY_LINE_ITEMS", "Deal amount is managed by line items");
        }

        validateCustomData(tenantId, request.getCustomData());

        DealStage requestedStage = null;
        boolean stageChanged = false;
        if (request.getStageId() != null) {
            requestedStage = dealStageRepository.findByIdAndTenantId(request.getStageId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Stage not found"));
            stageChanged = !request.getStageId().equals(deal.getStage().getId());
        }

        // Update entity
        dealMapper.updateEntity(request, deal);
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
        }

        return dealMapper.toResponse(updatedDeal);
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
        Deal deal = dealRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                stageScope, tenantId, userId, deal.getOwnerId(), deal.getCreatedBy());

        DealStage newStage = dealStageRepository.findByIdAndTenantId(stageId, tenantId)
            .orElseThrow(() -> new RuntimeException("Stage not found"));

        UUID oldStageId = deal.getStage().getId();
        String oldStageName = deal.getStage().getName();
        RecordCategory oldCategory = deal.getRecordCategory();
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
            Map<String, Object> eventMetadata = new HashMap<>();
            eventMetadata.put("previousStageId", oldStageId.toString());
            eventMetadata.put("newStageId", newStage.getId().toString());
            eventMetadata.put("previousStage", oldStageName);
            eventMetadata.put("newStage", newStage.getName());
            eventMetadata.put("actorId", userId.toString());
            eventMetadata.put("actorType", "USER");
            canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.DEAL_ENTITY_TYPE,
                CanonicalCrmEvent.STAGE_TRANSITIONED_EVENT_TYPE,
                updatedDeal.getId(),
                eventMetadata
            );
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
        Deal deal = dealRepository.findByIdAndTenantId(id, tenantId)
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
        Deal deal = dealRepository.findByIdAndTenantId(id, tenantId)
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
        Deal deal = dealRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                assignScope, tenantId, userId, deal.getOwnerId(), deal.getCreatedBy());

        UUID oldOwner = deal.getOwnerId();
        deal.setOwnerId(ownerUserId);
        deal.setUpdatedBy(userId);

        Deal updatedDeal = dealRepository.save(deal);

        boolean ownerChanged = !java.util.Objects.equals(oldOwner, ownerUserId);
        if (ownerChanged) {
            Map<String, Object> eventMetadata = new HashMap<>();
            if (oldOwner != null) {
                eventMetadata.put("previousOwnerId", oldOwner.toString());
            }
            if (ownerUserId != null) {
                eventMetadata.put("newOwnerId", ownerUserId.toString());
            }
            eventMetadata.put("actorId", userId.toString());
            eventMetadata.put("actorType", "USER");
            canonicalCrmEventPublisher.publish(
                updatedDeal.getTenantId(),
                CanonicalCrmEvent.DEAL_ENTITY_TYPE,
                CanonicalCrmEvent.OWNER_CHANGED_EVENT_TYPE,
                updatedDeal.getId(),
                eventMetadata
            );
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
     * Delete a deal (soft delete can be implemented)
     */
    public void deleteDeal(UUID id, UUID tenantId, UUID userId) {
        log.info("Deleting deal: {} for tenant: {}", id, tenantId);

        String deleteScope = recordScopeGuard.requireScope(tenantId, userId, "deal", "delete");
        Deal deal = dealRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Deal not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                deleteScope, tenantId, userId, deal.getOwnerId(), deal.getCreatedBy());

        dealRepository.delete(deal);
        log.info("Deal deleted: {}", id);
    }

    /**
     * RBAC-7: ensures the parent deal is within the caller's scope for the
     * given action before nested resources (notes, activities, line items)
     * are served or mutated. Used by controllers for nested endpoints.
     */
    public void assertDealAccessible(UUID tenantId, UUID dealId, String action) {
        UUID currentUserId = com.shivang.crm.util.UserUtil.currentUserId();
        String scope = recordScopeGuard.requireScope(tenantId, currentUserId, "deal", action);

        Deal deal = dealRepository.findByIdAndTenantId(dealId, tenantId)
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
                .and(DealSpecifications.byOwnerUserId(userId))
                .and(DealSpecifications.byIsWon(false))
                .and(DealSpecifications.byIsLost(false))
        );

        return deals.stream()
            .map(Deal::getAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
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
}
