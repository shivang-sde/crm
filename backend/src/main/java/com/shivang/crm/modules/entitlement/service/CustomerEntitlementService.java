package com.shivang.crm.modules.entitlement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealLineItem;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.entitlement.dto.CustomerEntitlementResponse;
import com.shivang.crm.modules.entitlement.dto.CustomerEntitlementUpdateRequest;
import com.shivang.crm.modules.entitlement.dto.UpcomingRenewalResponse;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.mapper.CustomerEntitlementMapper;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementSpecifications;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
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
public class CustomerEntitlementService {

    private final CustomerEntitlementRepository entitlementRepository;
    private final CustomerEntitlementMapper entitlementMapper;
    private final DealRepository dealRepository;
    private final DealLineItemRepository dealLineItemRepository;
    private final com.shivang.crm.modules.catalog.repository.OfferingRepository offeringRepository;
    private final ActivityService activityService;
    private final RecordScopeGuard recordScopeGuard;
    private final com.shivang.crm.shared.service.CustomerRelationshipValidator relationshipValidator;
    /** L3.0-P6: sole producer of ENTITLEMENT.CREATED / UPDATED / STATUS_CHANGED. */
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;

    public CustomerEntitlementResponse getById(UUID id, UUID tenantId) {
        UUID currentUserId = UserUtil.currentUserId();
        String scope = recordScopeGuard.requireScope(tenantId, currentUserId, "entitlement", "read");

        CustomerEntitlement entitlement = entitlementRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Entitlement not found"));

        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, currentUserId, entitlement.getOwnerId(), entitlement.getCreatedBy());

        return toResponse(entitlement);
    }

    @Transactional(readOnly = true)
    public Page<CustomerEntitlementResponse> list(
            UUID tenantId,
            UUID accountId,
            UUID contactId,
            UUID offeringId,
            EntitlementStatus status,
            UUID ownerUserId,
            Boolean renewable,
            LocalDate endDateFrom,
            LocalDate endDateTo,
            String search,
            int page,
            int size) {
        Specification<CustomerEntitlement> spec = CustomerEntitlementSpecifications.buildSpecification(
                tenantId, accountId, contactId, offeringId, status, ownerUserId, renewable, endDateFrom, endDateTo, search);
        spec = andScopeFilter(spec, tenantId, "entitlement", "read");
        Pageable pageable = PageRequest.of(page, size);
        return entitlementRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<UpcomingRenewalResponse> findUpcomingRenewals(
            UUID tenantId,
            UUID accountId,
            UUID ownerUserId,
            EntitlementStatus status,
            int daysAhead,
            int page,
            int size) {
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(daysAhead);
        Specification<CustomerEntitlement> spec = CustomerEntitlementSpecifications.buildUpcomingRenewalSpecification(
                tenantId, accountId, ownerUserId, status, today, windowEnd);
        spec = andScopeFilter(spec, tenantId, "entitlement", "read");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "endDate"));
        return entitlementRepository.findAll(spec, pageable).map(entitlement -> toUpcomingRenewalResponse(entitlement, today));
    }

    /**
     * RBAC-7: ANDs the caller's entitlement:read scope into a specification.
     * ALL adds no constraint (the tenant predicate is already present);
     * NONE/missing throws; OWN/TEAM constrain owner/creator/team.
     */
    private Specification<CustomerEntitlement> andScopeFilter(
            Specification<CustomerEntitlement> spec, UUID tenantId, String module, String action) {
        UUID currentUserId = UserUtil.currentUserId();
        String scope = recordScopeGuard.requireScope(tenantId, currentUserId, module, action);
        if ("ALL".equals(scope)) {
            return spec;
        }
        List<UUID> teamUserIds = "TEAM".equals(scope)
                ? recordScopeGuard.teamMemberIds(tenantId, currentUserId)
                : List.of();
        return spec.and(CustomerEntitlementSpecifications.visibleToUser(scope, currentUserId, teamUserIds));
    }

    public CustomerEntitlementResponse update(UUID id, UUID tenantId, UUID userId, CustomerEntitlementUpdateRequest request) {
        String writeScope = recordScopeGuard.requireScope(tenantId, userId, "entitlement", "write");
        CustomerEntitlement entitlement = entitlementRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Entitlement not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                writeScope, tenantId, userId, entitlement.getOwnerId(), entitlement.getCreatedBy());

        // L3.0-P6: snapshot status and the editable fields before mutating, so the
        // event can distinguish a real status transition from a plain field edit.
        EntitlementStatus beforeStatus = entitlement.getStatus();
        Map<String, Object> beforeSnapshot = entitlementSnapshot(entitlement);

        entitlementMapper.updateEntity(request, entitlement);

        // L3.0-P3: changing ownership is a privileged assignment operation, not a
        // field edit. 'entitlement:write' must not imply it. The permission is
        // resolved through the same RecordScopeGuard / PermissionEvaluatorService
        // pair as every other module's assignment check, and EMPLOYEE never holds
        // it (see DefaultRoleConfig.EMPLOYEE_ACTIONS and V68).
        if (request.getOwnerUserId() != null
                && !java.util.Objects.equals(request.getOwnerUserId(), entitlement.getOwnerId())) {
            String assignScope = recordScopeGuard.requireScope(tenantId, userId, "entitlement", "assign");
            recordScopeGuard.assertWithinOwnerCreatorScope(
                    assignScope, tenantId, userId, entitlement.getOwnerId(), entitlement.getCreatedBy());
            relationshipValidator.requireTenantOwner(request.getOwnerUserId(), tenantId);
            entitlement.setOwnerId(request.getOwnerUserId());
        }

        entitlement.setUpdatedBy(userId);
        recalculateRenewalDueDate(entitlement);
        CustomerEntitlement saved = entitlementRepository.save(entitlement);
        activityService.logActivity(tenantId, saved.getId(), "ENTITLEMENT", "ENTITLEMENT_UPDATED",
                "Entitlement updated", userId, buildMetadata(saved));
        // L3.0-P6: one canonical producer decides between UPDATED and
        // STATUS_CHANGED, so a status change can never produce both.
        publishEntitlementUpdateOrStatusChange(tenantId, userId, saved, beforeStatus, beforeSnapshot);
        return toResponse(saved);
    }

    /**
     * L3.0-P6: emits either {@code ENTITLEMENT.UPDATED} or
     * {@code ENTITLEMENT.STATUS_CHANGED} for a user-driven entitlement mutation,
     * never both.
     *
     * <p>A status transition is announced as {@code STATUS_CHANGED} alone,
     * because that event already carries {@code previousStatus}/{@code newStatus}
     * and a consumer asking "did this entitlement expire" must not have to
     * correlate two events to answer it.
     */
    private void publishEntitlementUpdateOrStatusChange(UUID tenantId, UUID userId, CustomerEntitlement entitlement,
            EntitlementStatus previousStatus, Map<String, Object> beforeSnapshot) {
        EntitlementStatus newStatus = entitlement.getStatus();
        Map<String, Object> metadata = baseEventMetadata(tenantId, userId);
        addEntitlementIdentifiers(metadata, entitlement);

        if (previousStatus != newStatus) {
            metadata.put("previousStatus", previousStatus.name());
            metadata.put("newStatus", newStatus.name());
            canonicalCrmEventPublisher.publish(
                    tenantId,
                    CanonicalCrmEvent.ENTITLEMENT_ENTITY_TYPE,
                    CanonicalCrmEvent.STATUS_CHANGED_EVENT_TYPE,
                    entitlement.getId(),
                    metadata);
            // A user-driven move into the terminal expiration state is also a
            // real expiration, so it is announced as such. This cannot double
            // fire for the same transition: this method runs once per mutation,
            // and the scheduler path is the only other caller of EXPIRED.
            if (newStatus == EntitlementStatus.EXPIRED) {
                canonicalCrmEventPublisher.publish(
                        tenantId,
                        CanonicalCrmEvent.ENTITLEMENT_ENTITY_TYPE,
                        CanonicalCrmEvent.EXPIRED_EVENT_TYPE,
                        entitlement.getId(),
                        baseEventMetadata(tenantId, userId));
            }
            return;
        }

        Map<String, Object> afterSnapshot = entitlementSnapshot(entitlement);
        List<String> changedFields = new ArrayList<>();
        beforeSnapshot.forEach((field, oldValue) -> {
            if (!Objects.equals(oldValue, afterSnapshot.get(field))) {
                changedFields.add(field);
            }
        });
        if (changedFields.isEmpty()) {
            return;
        }
        metadata.put("changedFields", changedFields);
        canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.ENTITLEMENT_ENTITY_TYPE,
                CanonicalCrmEvent.UPDATED_EVENT_TYPE,
                entitlement.getId(),
                metadata);
    }

    /** L3.0-P6: sole producer of ENTITLEMENT.CREATED. */
    private void publishEntitlementCreated(UUID tenantId, UUID userId, CustomerEntitlement entitlement) {
        Map<String, Object> metadata = baseEventMetadata(tenantId, userId);
        addEntitlementIdentifiers(metadata, entitlement);
        metadata.put("status", String.valueOf(entitlement.getStatus()));
        metadata.put("startDate", String.valueOf(entitlement.getStartDate()));
        metadata.put("endDate", String.valueOf(entitlement.getEndDate()));
        metadata.put("renewable", Boolean.TRUE.equals(entitlement.getRenewable()));
        putIfPresent(metadata, "currencyCode", entitlement.getCurrencyCode());

        canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.ENTITLEMENT_ENTITY_TYPE,
                CanonicalCrmEvent.CREATED_EVENT_TYPE,
                entitlement.getId(),
                metadata);
    }

    /**
     * The identifiers a consumer needs to place an entitlement in context. The
     * whole entity is deliberately not carried.
     */
    private static void addEntitlementIdentifiers(Map<String, Object> metadata, CustomerEntitlement entitlement) {
        putIfPresent(metadata, "accountId", entitlement.getAccountId());
        putIfPresent(metadata, "contactId", entitlement.getContactId());
        putIfPresent(metadata, "dealId", entitlement.getDealId());
        putIfPresent(metadata, "dealLineItemId", entitlement.getDealLineItemId());
        putIfPresent(metadata, "offeringId", entitlement.getOfferingId());
    }

    private Map<String, Object> entitlementSnapshot(CustomerEntitlement entitlement) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", entitlement.getName());
        snapshot.put("description", entitlement.getDescription());
        // Trailing zeros stripped so a representation-only difference is not
        // mistaken for a commercial change.
        snapshot.put("quantity", moneyValue(entitlement.getQuantity()));
        snapshot.put("agreedPrice", moneyValue(entitlement.getAgreedPrice()));
        snapshot.put("startDate", entitlement.getStartDate());
        snapshot.put("endDate", entitlement.getEndDate());
        snapshot.put("renewable", entitlement.getRenewable());
        snapshot.put("autoRenew", entitlement.getAutoRenew());
        snapshot.put("renewalNoticeDays", entitlement.getRenewalNoticeDays());
        snapshot.put("ownerId", entitlement.getOwnerId());
        snapshot.put("customData", entitlement.getCustomData());
        return snapshot;
    }

    private static java.math.BigDecimal moneyValue(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private Map<String, Object> baseEventMetadata(UUID tenantId, UUID userId) {
        boolean workflowCaused = com.shivang.crm.shared.event.CausalEventContext.get() != null;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("actorId", userId.toString());
        metadata.put("actorType", workflowCaused ? "SYSTEM" : "USER");
        metadata.put("source", workflowCaused ? "WORKFLOW" : "MANUAL");
        return metadata;
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value.toString());
        }
    }

    public void activate(UUID id, UUID tenantId, UUID userId) {
        CustomerEntitlement entitlement = entitlementRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Entitlement not found"));
        String activateScope = recordScopeGuard.requireScope(tenantId, userId, "entitlement", "write");
        recordScopeGuard.assertWithinOwnerCreatorScope(
                activateScope, tenantId, userId, entitlement.getOwnerId(), entitlement.getCreatedBy());
        if (entitlement.getStatus() != EntitlementStatus.PENDING && entitlement.getStatus() != EntitlementStatus.SUSPENDED) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Only pending or suspended entitlements can be activated");
        }
        EntitlementStatus previousStatus = entitlement.getStatus();
        entitlement.setStatus(EntitlementStatus.ACTIVE);
        entitlement.setUpdatedBy(userId);
        entitlementRepository.save(entitlement);
        activityService.logActivity(tenantId, entitlement.getId(), "ENTITLEMENT", "ENTITLEMENT_ACTIVATED",
                "Entitlement activated", userId, buildMetadata(entitlement));
        // L3.0-P6: the transition is announced here rather than through update(),
        // because activate() is a dedicated lifecycle entry point.
        publishEntitlementStatusChange(tenantId, userId, entitlement, previousStatus);
    }

    public void suspend(UUID id, UUID tenantId, UUID userId) {
        CustomerEntitlement entitlement = entitlementRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Entitlement not found"));
        String suspendScope = recordScopeGuard.requireScope(tenantId, userId, "entitlement", "write");
        recordScopeGuard.assertWithinOwnerCreatorScope(
                suspendScope, tenantId, userId, entitlement.getOwnerId(), entitlement.getCreatedBy());
        if (entitlement.getStatus() != EntitlementStatus.ACTIVE) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Only active entitlements can be suspended");
        }
        EntitlementStatus previousStatus = entitlement.getStatus();
        entitlement.setStatus(EntitlementStatus.SUSPENDED);
        entitlement.setUpdatedBy(userId);
        entitlementRepository.save(entitlement);
        activityService.logActivity(tenantId, entitlement.getId(), "ENTITLEMENT", "ENTITLEMENT_SUSPENDED",
                "Entitlement suspended", userId, buildMetadata(entitlement));
        publishEntitlementStatusChange(tenantId, userId, entitlement, previousStatus);
    }

    public void terminate(UUID id, UUID tenantId, UUID userId) {
        CustomerEntitlement entitlement = entitlementRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Entitlement not found"));
        String terminateScope = recordScopeGuard.requireScope(tenantId, userId, "entitlement", "write");
        recordScopeGuard.assertWithinOwnerCreatorScope(
                terminateScope, tenantId, userId, entitlement.getOwnerId(), entitlement.getCreatedBy());
        if (entitlement.getStatus() != EntitlementStatus.ACTIVE
                && entitlement.getStatus() != EntitlementStatus.PENDING
                && entitlement.getStatus() != EntitlementStatus.SUSPENDED) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Only active, pending, or suspended entitlements can be terminated");
        }
        EntitlementStatus previousStatus = entitlement.getStatus();
        entitlement.setStatus(EntitlementStatus.TERMINATED);
        entitlement.setUpdatedBy(userId);
        entitlementRepository.save(entitlement);
        activityService.logActivity(tenantId, entitlement.getId(), "ENTITLEMENT", "ENTITLEMENT_TERMINATED",
                "Entitlement terminated", userId, buildMetadata(entitlement));
        publishEntitlementStatusChange(tenantId, userId, entitlement, previousStatus);
    }

    /**
     * L3.0-P6: the one place a dedicated lifecycle transition announces itself.
     * Shared by activate, suspend and terminate so all three produce identical
     * metadata shape, and it only fires when the status genuinely moved.
     */
    private void publishEntitlementStatusChange(UUID tenantId, UUID userId, CustomerEntitlement entitlement,
            EntitlementStatus previousStatus) {
        publishEntitlementUpdateOrStatusChange(tenantId, userId, entitlement, previousStatus, entitlementSnapshot(entitlement));
    }

public void provisionFromWonDeal(UUID tenantId, UUID dealId, UUID userId) {
        // L3.0-P2: a live, tenant-scoped Deal is the precondition. The old
        // tenant-only lookup could resolve a soft-deleted deal.
        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Deal not found"));
        if (!deal.isWon()) {
            log.debug("Skipping entitlement provisioning for non-won deal {}", dealId);
            return;
        }
        if (deal.getAccountId() == null && deal.getContactId() == null) {
            throw new BusinessException("ENTITLEMENT_NO_CUSTOMER", "A won deal must have an account or contact before provisioning entitlements");
        }

        // L3.0-P2: the customer references copied onto every entitlement must
        // still be live and mutually consistent at provisioning time, not merely
        // valid when the Deal was written.
        requireConsistentCustomerReferences(deal, tenantId);

        List<DealLineItem> activeLineItems = dealLineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId);
        if (activeLineItems.isEmpty()) {
            log.info("No active deal line items for won deal {} - skipping entitlement provisioning", dealId);
            return;
        }

        for (DealLineItem lineItem : activeLineItems) {
            if (entitlementRepository.existsByTenantIdAndDealLineItemIdAndDeletedFalse(tenantId, lineItem.getId())) {
                continue;
            }
            // L3.0-P2: line items are already filtered to this tenant by the
            // repository query; re-assert the Offering is live in this tenant.
            requireLiveOffering(lineItem, tenantId);
            CustomerEntitlement entitlement = buildEntitlement(deal, lineItem, tenantId, userId);
CustomerEntitlement saved = entitlementRepository.save(entitlement);
        activityService.logActivity(tenantId, saved.getId(), "ENTITLEMENT", "ENTITLEMENT_CREATED",
                    "Entitlement created from won deal", userId, buildMetadata(saved));
        // L3.0-P6: the single creation event, emitted from the one place an
        // entitlement is built. Both P5 CLOSED_WON provisioning and the P4
        // reconciliation service route through here, so neither can miss it and
        // neither can emit its own.
        publishEntitlementCreated(tenantId, userId, saved);
        }
    }

    /**
     * L3.0-P4-B: reconciles entitlements that were missed by Deals created
     * directly in CLOSED_WON before P4-A existed.
     *
     * <p>Deliberately NOT wired to application startup or any scheduler. The
     * prompt is explicit that historical data must not be silently modified
     * during normal startup, and this repository has no established
     * operational-migration runner to invoke it from. It is an explicit,
     * tenant-scoped, idempotent operation an operator or an operator-run test can
     * call per tenant.
     *
     * <p>Idempotency comes from {@link #provisionFromWonDeal}, which skips any
     * line item that already has an entitlement, so a re-run is a no-op.
     * Tenant safety comes from every lookup being tenant-scoped and from the
     * deal ids being read per tenant.
     *
     * @param tenantId the tenant to reconcile
     * @param userId the acting user recorded on created entitlements
     * @return the ids of Deals that were still missing entitlements and were
     *         successfully provisioned; empty when nothing was missing
     */
    /**
     * Outcome of a reconciliation run.
     *
     * @param reconciled won Deal ids that were missing entitlements and now have them
     * @param needsManualReview won Deal ids that are still missing entitlements and
     *        could not be safely provisioned, with the reason for each
     */
    public record ReconciliationReport(
            List<UUID> reconciled,
            Map<UUID, String> needsManualReview) {

        public boolean isFullyReconciled() {
            return needsManualReview.isEmpty();
        }
    }

    /**
     * L3.0-P4-B: reconciles entitlements that were missed by Deals created
     * directly in CLOSED_WON before P4-A existed.
     *
     * <p>Deliberately NOT wired to application startup or any scheduler. The
     * prompt is explicit that historical data must not be silently modified
     * during normal startup, and this repository has no established
     * operational-migration runner to invoke it from. It is an explicit,
     * tenant-scoped, idempotent operation an operator or an operator-run test can
     * call per tenant.
     *
     * <p>Idempotency comes from {@link #provisionFromWonDeal}, which skips any
     * line item that already has an entitlement, so a re-run is a no-op.
     * Tenant safety comes from every lookup being tenant-scoped and from the
     * deal ids being read per tenant.
     *
     * <p>A Deal is only reported as reconciled once its entitlements actually
     * exist. Anything that could not be safely provisioned comes back in
     * {@link ReconciliationReport#needsManualReview()} with a reason, because a
     * silent skip would let a caller believe the run was complete when it was not.
     *
     * @param tenantId the tenant to reconcile
     * @param userId the acting user recorded on created entitlements
     */
    public ReconciliationReport reconcileMissingEntitlementsForWonDeals(UUID tenantId, UUID userId) {
        List<Deal> wonDeals = dealRepository.findByTenantIdAndRecordCategoryAndDeletedFalse(
                tenantId, RecordCategory.CLOSED_WON);

        List<UUID> reconciled = new ArrayList<>();
        Map<UUID, String> needsManualReview = new LinkedHashMap<>();
        for (Deal deal : wonDeals) {
            if (deal.getAccountId() == null && deal.getContactId() == null) {
                needsManualReview.put(deal.getId(), "Won deal has no account or contact to attribute coverage to");
                continue;
            }
            if (!entitlementRepository.findByTenantIdAndDealIdAndDeletedFalse(tenantId, deal.getId()).isEmpty()) {
                continue;
            }

            try {
                provisionFromWonDeal(tenantId, deal.getId(), userId);
                // provisionFromWonDeal is a silent no-op for a Deal with no line
                // items, so verify the gap is actually closed rather than assume it.
                if (entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantId, deal.getId())) {
                    reconciled.add(deal.getId());
                } else {
                    needsManualReview.put(deal.getId(),
                            "Won deal has no active line items to provision coverage from");
                }
            } catch (Exception ex) {
                // One problematic Deal must not abort the whole run.
                needsManualReview.put(deal.getId(), ex.getMessage());
                log.warn("Failed to reconcile entitlements for won deal {}: {}", deal.getId(), ex.getMessage());
            }
        }

        log.info("Entitlement reconciliation for tenant {}: {} reconciled, {} need manual review",
                tenantId, reconciled.size(), needsManualReview.size());
        return new ReconciliationReport(List.copyOf(reconciled), Map.copyOf(needsManualReview));
    }

    /**
     * L3.0-P4-G: renewal semantics for the current entitlement uniqueness model.
     *
     * <p>Decision: option B. A DealLineItem is the original commercial source and
     * a CustomerEntitlement is the resulting customer coverage. A renewal must
     * therefore create a NEW entitlement from a NEW line item (a renewal Deal)
     * rather than reuse the original, because the existing
     * {@code UNIQUE(tenant_id, deal_line_item_id)} constraint permits exactly one
     * entitlement per line item for the life of that line item.
     *
     * <p>The schema already anticipates this: {@code renewedFromEntitlementId},
     * {@code renewedToEntitlementId}, {@code renewalDealId} and the
     * {@code RENEWED} status all exist but are never written. No migration is
     * needed for L3.0 — the constraint is already correct for option B, and the
     * link columns are populated by whichever future phase implements renewal
     * provisioning. What this method does is make the invariant enforceable and
     * explicit rather than implicit in a unique constraint.
     *
     * <p>A renewal therefore reuses the original DealLineItem, which would be
     * rejected by the constraint. Rather than inventing renewal provisioning
     * here (explicitly out of scope for P4), the correct entry point is asserted:
     * the originating line item must already be frozen because its entitlement
     * exists.
     */
    public void assertRenewalRequiresNewCommercialSource(UUID originalDealId, UUID newDealId, UUID tenantId) {
        if (java.util.Objects.equals(originalDealId, newDealId)) {
            throw new BusinessException("ENTITLEMENT_RENEWAL_REQUIRES_NEW_DEAL",
                    "A renewal must be provisioned from a new commercial source, not the original deal");
        }
        Deal renewalDeal = dealRepository.findByIdAndTenantIdAndDeletedFalse(newDealId, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Renewal deal not found"));
        if (!renewalDeal.isWon()) {
            throw new BusinessException("ENTITLEMENT_RENEWAL_DEAL_NOT_WON",
                    "A renewal deal must be in a CLOSED_WON stage before entitlements are provisioned");
        }
    }

    /**
     * L3.0-P2: an entitlement inherits the Deal's customer identity, so every
     * reference it will snapshot must be live, tenant-scoped, and mutually
     * consistent. Offering is validated per line item inside
     * {@link #provisionFromWonDeal}'s loop instead, because it varies per item.
     */
    private void requireConsistentCustomerReferences(Deal deal, UUID tenantId) {
        relationshipValidator.requireLiveAccount(deal.getAccountId(), tenantId);
        relationshipValidator.requireLiveContact(deal.getContactId(), tenantId);
        relationshipValidator.requireContactBelongsToAccount(
                deal.getContactId(), deal.getAccountId(), tenantId);
        relationshipValidator.requireTenantOwner(deal.getOwnerId(), tenantId);
    }

    /**
     * L3.0-P2: the DealLineItem's Offering must be a live record of this tenant.
     * DealLineItemService already validates this at write time; re-checking here
     * keeps provisioning correct for rows written before this guard existed.
     */
    private void requireLiveOffering(DealLineItem lineItem, UUID tenantId) {
        if (lineItem.getOfferingId() == null) {
            throw new BusinessException("OFFERING_NOT_FOUND",
                    "Deal line item has no offering reference");
        }
        offeringRepository.findByIdAndTenantIdAndDeletedFalse(lineItem.getOfferingId(), tenantId)
                .orElseThrow(() -> new BusinessException("OFFERING_NOT_FOUND",
                        "Offering not found: " + lineItem.getOfferingId()));
    }

    private CustomerEntitlement buildEntitlement(Deal deal, DealLineItem lineItem, UUID tenantId, UUID userId) {
        CustomerEntitlement entitlement = new CustomerEntitlement();
        entitlement.setTenantId(tenantId);
        entitlement.setOwnerId(deal.getOwnerId());
        entitlement.setCreatedBy(userId);
        entitlement.setUpdatedBy(userId);
        entitlement.setAccountId(deal.getAccountId());
        entitlement.setContactId(deal.getContactId());
        entitlement.setOfferingId(lineItem.getOfferingId());
        entitlement.setDealId(deal.getId());
        entitlement.setDealLineItemId(lineItem.getId());
        entitlement.setName(lineItem.getItemName());
        entitlement.setCode(lineItem.getItemCode());
        entitlement.setDescription(lineItem.getDescription());
        entitlement.setQuantity(lineItem.getQuantity());
        entitlement.setAgreedPrice(lineItem.getUnitPrice());
        // L3.0-P4-F: the entitlement's monetary values are a copy of the line
        // item's, so the currency must be too. Taking the line item's snapshot
        // keeps the two rows from ever disagreeing about the currency of the
        // same agreed price, and preserves null rather than inventing a default.
        entitlement.setCurrencyCode(lineItem.getCurrencyCode());
        entitlement.setRenewable(lineItem.getRenewable());
        entitlement.setAutoRenew(Boolean.FALSE);
        entitlement.setRenewalNoticeDays(lineItem.getRenewalNoticeDays());
        entitlement.setStartDate(lineItem.getServiceStartDate());
        entitlement.setEndDate(lineItem.getServiceEndDate());
        entitlement.setStatus(calculateInitialStatus(lineItem.getServiceStartDate(), lineItem.getServiceEndDate()));
        recalculateRenewalDueDate(entitlement);
        entitlement.setCustomData(lineItem.getCustomData() == null ? new HashMap<>() : new HashMap<>(lineItem.getCustomData()));
        return entitlement;
    }

    private EntitlementStatus calculateInitialStatus(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        if (startDate != null && startDate.isAfter(today)) {
            return EntitlementStatus.PENDING;
        }
        if (endDate != null && endDate.isBefore(today)) {
            return EntitlementStatus.EXPIRED;
        }
        return EntitlementStatus.ACTIVE;
    }

    private void recalculateRenewalDueDate(CustomerEntitlement entitlement) {
        if (Boolean.TRUE.equals(entitlement.getRenewable())
                && entitlement.getEndDate() != null
                && entitlement.getRenewalNoticeDays() != null) {
            entitlement.setRenewalDueDate(entitlement.getEndDate().minusDays(entitlement.getRenewalNoticeDays()));
        } else {
            entitlement.setRenewalDueDate(null);
        }
    }

    private CustomerEntitlementResponse toResponse(CustomerEntitlement entitlement) {
        return CustomerEntitlementResponse.builder()
                .id(entitlement.getId())
                .tenantId(entitlement.getTenantId())
                .accountId(entitlement.getAccountId())
                .contactId(entitlement.getContactId())
                .offeringId(entitlement.getOfferingId())
                .dealId(entitlement.getDealId())
                .dealLineItemId(entitlement.getDealLineItemId())
                .name(entitlement.getName())
                .code(entitlement.getCode())
                .description(entitlement.getDescription())
                .status(entitlement.getStatus())
                .startDate(entitlement.getStartDate())
                .endDate(entitlement.getEndDate())
                .quantity(entitlement.getQuantity())
                .agreedPrice(entitlement.getAgreedPrice())
                .currencyCode(entitlement.getCurrencyCode())
                .renewable(entitlement.getRenewable())
                .autoRenew(entitlement.getAutoRenew())
                .renewalNoticeDays(entitlement.getRenewalNoticeDays())
                .renewalDueDate(entitlement.getRenewalDueDate())
                .renewedFromEntitlementId(entitlement.getRenewedFromEntitlementId())
                .renewedToEntitlementId(entitlement.getRenewedToEntitlementId())
                .renewalDealId(entitlement.getRenewalDealId())
                .customData(entitlement.getCustomData())
                .ownerUserId(entitlement.getOwnerId())
                .createdAt(entitlement.getCreatedAt())
                .updatedAt(entitlement.getUpdatedAt())
                .build();
    }

    private UpcomingRenewalResponse toUpcomingRenewalResponse(CustomerEntitlement entitlement, LocalDate today) {
        return UpcomingRenewalResponse.builder()
                .entitlementId(entitlement.getId())
                .name(entitlement.getName())
                .accountId(entitlement.getAccountId())
                .contactId(entitlement.getContactId())
                .offeringId(entitlement.getOfferingId())
                .ownerUserId(entitlement.getOwnerId())
                .startDate(entitlement.getStartDate())
                .endDate(entitlement.getEndDate())
                .renewalDueDate(entitlement.getRenewalDueDate())
                .daysUntilExpiry((int) ChronoUnit.DAYS.between(today, entitlement.getEndDate()))
                .status(entitlement.getStatus())
                .renewable(entitlement.getRenewable())
                .build();
    }

    private Map<String, Object> buildMetadata(CustomerEntitlement entitlement) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entitlementId", entitlement.getId());
        metadata.put("offeringId", entitlement.getOfferingId());
        metadata.put("dealId", entitlement.getDealId());
        metadata.put("dealLineItemId", entitlement.getDealLineItemId());
        metadata.put("name", entitlement.getName());
        metadata.put("status", entitlement.getStatus());
        metadata.put("startDate", entitlement.getStartDate());
        metadata.put("endDate", entitlement.getEndDate());
        return metadata;
    }
}
