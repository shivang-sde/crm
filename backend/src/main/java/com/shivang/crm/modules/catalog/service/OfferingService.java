package com.shivang.crm.modules.catalog.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.catalog.dto.OfferingCreateRequest;
import com.shivang.crm.modules.catalog.dto.OfferingResponse;
import com.shivang.crm.modules.catalog.dto.OfferingUpdateRequest;
import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.catalog.enums.BillingInterval;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;
import com.shivang.crm.modules.catalog.mapper.OfferingMapper;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.catalog.repository.OfferingSpecifications;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OfferingService {

    /**
     * L3.0-P4-D: entitlement statuses that represent live customer coverage.
     *
     * <p>Deliberately BROADER than {@code EntitlementRenewalTaskService}'s
     * {@code ELIGIBLE_STATUSES}, which is only {ACTIVE, PENDING} because it
     * selects which coverage periods are due a renewal follow-up. Here the
     * question is different: does the Offering still describe something the
     * customer is using? A SUSPENDED entitlement is paused rather than ended, so
     * the Offering is still referenced and still blocks deletion.
     *
     * <p>Terminal coverages (EXPIRED, CANCELLED, RENEWED, TERMINATED) stay
     * queryable for history and do not block catalog cleanup.
     */
    static final List<EntitlementStatus> LIVE_ENTITLEMENT_STATUSES = List.of(
            EntitlementStatus.PENDING,
            EntitlementStatus.ACTIVE,
            EntitlementStatus.SUSPENDED);

    private final OfferingRepository offeringRepository;
    private final com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository entitlementRepository;
    private final com.shivang.crm.modules.deal.repository.DealLineItemRepository dealLineItemRepository;
    /** L3.0-P6: sole producer of the OFFERING event family. */
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;
    private final OfferingMapper offeringMapper;
    private final UserRepository userRepository;
    private final ActivityService activityService;

    public OfferingResponse createOffering(UUID tenantId, UUID userId, OfferingCreateRequest request) {
        log.info("Creating offering for tenant {}", tenantId);

        validateRequest(request);
        normalizeRequest(request);

        if (offeringRepository.existsByTenantIdAndCodeIgnoreCaseAndDeletedFalse(tenantId, request.getCode())) {
            throw new BusinessException("DUPLICATE_OFFERING_CODE", "An offering with the same code already exists");
        }

        Offering offering = offeringMapper.toEntity(request);
        offering.setTenantId(tenantId);
        offering.setCreatedBy(userId);
        offering.setUpdatedBy(userId);
        offering.setOwnerId(request.getOwnerUserId() != null ? request.getOwnerUserId() : userId);
        offering.setActive(!Boolean.FALSE.equals(request.getActive()));
        offering.setRenewable(Boolean.TRUE.equals(request.getRenewable()));

        validateOwner(tenantId, offering.getOwnerId());

        Offering saved = offeringRepository.save(offering);
        activityService.logActivity(tenantId, saved.getId(), "OFFERING", "OFFERING_CREATED",
                "Offering created", userId, Map.of("code", saved.getCode()));
        // L3.0-P6: OFFERING.CREATED, sole producer. A new Offering may already be
        // active, so the lifecycle state is reported rather than assumed.
        Map<String, Object> createdMetadata = baseEventMetadata(tenantId, userId);
        createdMetadata.put("code", saved.getCode());
        createdMetadata.put("offeringType", String.valueOf(saved.getOfferingType()));
        createdMetadata.put("active", Boolean.TRUE.equals(saved.getActive()));
        putIfPresent(createdMetadata, "currencyCode", saved.getCurrencyCode());
        canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.OFFERING_ENTITY_TYPE,
                CanonicalCrmEvent.CREATED_EVENT_TYPE,
                saved.getId(),
                createdMetadata);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OfferingResponse getOfferingById(UUID id, UUID tenantId) {
        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Offering not found"));
        return toResponse(offering);
    }

    @Transactional(readOnly = true)
    public Page<OfferingResponse> listOfferings(UUID tenantId, String search, OfferingType offeringType,
            BillingType billingType, Boolean active, UUID ownerUserId, int page, int size) {
        Specification<Offering> spec = OfferingSpecifications.buildSpecification(
                tenantId, search, offeringType, billingType, active, ownerUserId);
        Pageable pageable = PageRequest.of(page, size);
        return offeringRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public OfferingResponse updateOffering(UUID id, UUID tenantId, UUID userId, OfferingUpdateRequest request) {
        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Offering not found"));

        if (request.getCode() != null && !request.getCode().trim().equalsIgnoreCase(offering.getCode())) {
            normalizeCode(request);
            if (offeringRepository.existsByTenantIdAndCodeIgnoreCaseAndIdNotAndDeletedFalse(tenantId, request.getCode(), id)) {
                throw new BusinessException("DUPLICATE_OFFERING_CODE", "An offering with the same code already exists");
            }
        }

        normalizeUpdateRequest(request);
        validateUpdateRequest(request, offering);

        // L3.0-P6: snapshot the lifecycle flag and the editable fields before
        // mutating, so the correct single event can be chosen afterwards.
        boolean wasActive = Boolean.TRUE.equals(offering.getActive());
        Map<String, Object> before = offeringSnapshot(offering);

        offeringMapper.updateEntity(request, offering);
        if (request.getBillingType() != null && request.getBillingType() != BillingType.RECURRING) {
            // MapStruct ignores the normalized null, so clear the persisted
            // interval explicitly when leaving the recurring model.
            offering.setBillingInterval(null);
        }

        if (request.getOwnerUserId() != null) {
            validateOwner(tenantId, request.getOwnerUserId());
            offering.setOwnerId(request.getOwnerUserId());
        }
        if (request.getCode() != null) {
            offering.setCode(request.getCode().trim().toUpperCase());
        }
        if (request.getCurrencyCode() != null) {
            offering.setCurrencyCode(request.getCurrencyCode().trim().toUpperCase());
        }
        if (request.getActive() != null) {
            offering.setActive(request.getActive());
        }
        if (request.getRenewable() != null) {
            offering.setRenewable(request.getRenewable());
        }
        offering.setUpdatedBy(userId);

Offering updated = offeringRepository.save(offering);
        activityService.logActivity(tenantId, updated.getId(), "OFFERING", "OFFERING_UPDATED",
                "Offering updated", userId, Map.of("code", updated.getCode()));
        publishOfferingUpdateOrLifecycle(tenantId, userId, updated, wasActive, before);
        return toResponse(updated);
    }

    /**
     * L3.0-P6: emits exactly one event for an Offering update.
     *
     * <p>Event semantics chosen for this repository, where the Offering lifecycle
     * is a boolean {@code active} flag rather than a status enum:
     * <ul>
     *   <li>{@code OFFERING.UPDATED} — non-lifecycle field changes.</li>
     *   <li>{@code OFFERING.ACTIVATED} / {@code OFFERING.DEACTIVATED} — a
     *       transition across the {@code active} boundary.</li>
     * </ul>
     *
     * <p>A request that flips {@code active} emits the lifecycle event and
     * <em>suppresses</em> {@code OFFERING.UPDATED}, even if it also changed other
     * fields. Emitting both would leave consumers guessing which one describes
     * the request, and "this plan went on or off sale" is the consequential fact.
     * A consumer that needs the full field state re-reads the entity.
     *
     * <p>Nothing is emitted when no field actually changed.
     */
    private void publishOfferingUpdateOrLifecycle(UUID tenantId, UUID userId, Offering offering,
            boolean wasActive, Map<String, Object> before) {
        boolean isActive = Boolean.TRUE.equals(offering.getActive());
        Map<String, Object> metadata = baseEventMetadata(tenantId, userId);
        metadata.put("code", offering.getCode());
        putIfPresent(metadata, "currencyCode", offering.getCurrencyCode());

        if (isActive != wasActive) {
            metadata.put("active", isActive);
            canonicalCrmEventPublisher.publish(
                    tenantId,
                    CanonicalCrmEvent.OFFERING_ENTITY_TYPE,
                    isActive
                            ? CanonicalCrmEvent.ACTIVATED_EVENT_TYPE
                            : CanonicalCrmEvent.DEACTIVATED_EVENT_TYPE,
                    offering.getId(),
                    metadata);
            return;
        }

        Map<String, Object> after = offeringSnapshot(offering);
        List<String> changedFields = new ArrayList<>();
        before.forEach((field, oldValue) -> {
            if (!Objects.equals(oldValue, after.get(field))) {
                changedFields.add(field);
            }
        });
        if (changedFields.isEmpty()) {
            return;
        }
        metadata.put("changedFields", changedFields);
        canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.OFFERING_ENTITY_TYPE,
                CanonicalCrmEvent.UPDATED_EVENT_TYPE,
                offering.getId(),
                metadata);
    }

/**
     * Trailing zeros are stripped from monetary values so a no-op edit that only
     * differs in representation (1000 versus 1000.00) is not reported as a change.
     */
    private Map<String, Object> offeringSnapshot(Offering offering) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", offering.getName());
        snapshot.put("code", offering.getCode());
        snapshot.put("description", offering.getDescription());
        snapshot.put("offeringType", offering.getOfferingType());
        snapshot.put("billingType", offering.getBillingType());
        snapshot.put("billingInterval", offering.getBillingInterval());
        snapshot.put("defaultPrice", moneyValue(offering.getDefaultPrice()));
        snapshot.put("currencyCode", offering.getCurrencyCode());
        snapshot.put("defaultTermDays", offering.getDefaultTermDays());
        snapshot.put("renewable", offering.getRenewable());
        snapshot.put("ownerId", offering.getOwnerId());
        snapshot.put("customData", offering.getCustomData());
        return snapshot;
    }

    private static java.math.BigDecimal moneyValue(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    public void deactivateOffering(UUID id, UUID tenantId, UUID userId) {
        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Offering not found"));
        boolean wasActive = Boolean.TRUE.equals(offering.getActive());
        offering.setActive(false);
        offering.setUpdatedBy(userId);
        offeringRepository.save(offering);
        activityService.logActivity(tenantId, offering.getId(), "OFFERING", "OFFERING_DEACTIVATED",
                "Offering deactivated", userId, Map.of("code", offering.getCode()));
// L3.0-P6: only a real transition is announced, so deactivating an
        // already-inactive Offering does not emit a second DEACTIVATED event.
        if (!wasActive) {
            return;
        }
        publishOfferingLifecycle(tenantId, userId, offering, false);
    }

    public void activateOffering(UUID id, UUID tenantId, UUID userId) {
        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Offering not found"));
        boolean wasActive = Boolean.TRUE.equals(offering.getActive());
        offering.setActive(true);
        offering.setUpdatedBy(userId);
        offeringRepository.save(offering);
        activityService.logActivity(tenantId, offering.getId(), "OFFERING", "OFFERING_ACTIVATED",
                "Offering activated", userId, Map.of("code", offering.getCode()));
        if (wasActive) {
            return;
        }
        publishOfferingLifecycle(tenantId, userId, offering, true);
    }

    /** L3.0-P6: sole producer of OFFERING.ACTIVATED / OFFERING.DEACTIVATED. */
    private void publishOfferingLifecycle(UUID tenantId, UUID userId, Offering offering, boolean active) {
        Map<String, Object> metadata = baseEventMetadata(tenantId, userId);
        metadata.put("code", offering.getCode());
        metadata.put("active", active);
        putIfPresent(metadata, "currencyCode", offering.getCurrencyCode());
        canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.OFFERING_ENTITY_TYPE,
                active
                        ? CanonicalCrmEvent.ACTIVATED_EVENT_TYPE
                        : CanonicalCrmEvent.DEACTIVATED_EVENT_TYPE,
                offering.getId(),
                metadata);
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

    public void deleteOffering(UUID id, UUID tenantId, UUID userId) {
        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Offering not found"));

        // L3.0-P4-D: an Offering that underpins live customer coverage must not be
        // deleted. Live means PENDING, ACTIVE or SUSPENDED (see
        // LIVE_ENTITLEMENT_STATUSES). Terminal coverages do not block cleanup.
        //
        // The lookup is tenant-scoped, so another tenant's entitlement for the
        // same offering id can never block this tenant's deletion.

        if (entitlementRepository.existsByTenantIdAndOfferingIdAndStatusInAndDeletedFalse(
                tenantId, id, LIVE_ENTITLEMENT_STATUSES)) {
            throw new BusinessException("OFFERING_IN_USE",
                    "Offering has live customer entitlements and cannot be deleted");
        }
        if (dealLineItemRepository.existsOpenDealLineItemByTenantIdAndOfferingId(tenantId, id)) {
            throw new BusinessException("OFFERING_IN_USE",
                    "Offering is referenced by an open deal and cannot be deleted");
        }

        offering.softDelete(userId);
        offering.setUpdatedBy(userId);
        offeringRepository.save(offering);
        activityService.logActivity(tenantId, offering.getId(), "OFFERING", "OFFERING_DELETED",
                "Offering deleted", userId, Map.of("code", offering.getCode()));
    }

    private void validateRequest(OfferingCreateRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("INVALID_OFFERING", "Offering name is required");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new BusinessException("INVALID_OFFERING", "Offering code is required");
        }
        if (request.getOfferingType() == null) {
            throw new BusinessException("INVALID_OFFERING", "Offering type is required");
        }
        if (request.getBillingType() == null) {
            throw new BusinessException("INVALID_OFFERING", "Billing type is required");
        }
        if (request.getDefaultPrice() != null && request.getDefaultPrice().signum() < 0) {
            throw new BusinessException("INVALID_OFFERING", "Default price must be zero or positive");
        }
        if (request.getDefaultTermDays() != null && request.getDefaultTermDays() <= 0) {
            throw new BusinessException("INVALID_OFFERING", "Default term days must be positive when provided");
        }
        if (request.getCurrencyCode() != null && !request.getCurrencyCode().isBlank() && request.getCurrencyCode().trim().length() != 3) {
            throw new BusinessException("INVALID_OFFERING", "Currency code must be three characters when provided");
        }
        if (request.getBillingType() == BillingType.RECURRING && request.getBillingInterval() == null) {
            throw new BusinessException("INVALID_OFFERING", "Billing interval is required for recurring offerings");
        }
        if (request.getBillingType() != BillingType.RECURRING && request.getBillingInterval() != null) {
            throw new BusinessException("INVALID_OFFERING", "Billing interval must be null for non-recurring offerings");
        }
    }

    private void validateUpdateRequest(OfferingUpdateRequest request, Offering existing) {
        if (request.getName() != null && request.getName().isBlank()) {
            throw new BusinessException("INVALID_OFFERING", "Offering name is required");
        }
        if (request.getCode() != null && request.getCode().isBlank()) {
            throw new BusinessException("INVALID_OFFERING", "Offering code is required");
        }
        if (request.getOfferingType() != null && request.getOfferingType() == null) {
            throw new BusinessException("INVALID_OFFERING", "Offering type is required");
        }
        if (request.getBillingType() != null && request.getBillingType() == null) {
            throw new BusinessException("INVALID_OFFERING", "Billing type is required");
        }
        if (request.getDefaultPrice() != null && request.getDefaultPrice().signum() < 0) {
            throw new BusinessException("INVALID_OFFERING", "Default price must be zero or positive");
        }
        if (request.getDefaultTermDays() != null && request.getDefaultTermDays() <= 0) {
            throw new BusinessException("INVALID_OFFERING", "Default term days must be positive when provided");
        }
        if (request.getCurrencyCode() != null && !request.getCurrencyCode().isBlank() && request.getCurrencyCode().trim().length() != 3) {
            throw new BusinessException("INVALID_OFFERING", "Currency code must be three characters when provided");
        }
        BillingType resolvedBillingType = request.getBillingType() != null ? request.getBillingType() : existing.getBillingType();
        BillingInterval resolvedInterval;
        if (request.getBillingInterval() != null) {
            resolvedInterval = request.getBillingInterval();
        } else if (resolvedBillingType == BillingType.RECURRING) {
            resolvedInterval = existing.getBillingInterval();
        } else {
            resolvedInterval = null;
        }
        if (resolvedBillingType == BillingType.RECURRING && resolvedInterval == null) {
            throw new BusinessException("INVALID_OFFERING", "Billing interval is required for recurring offerings");
        }
        if (resolvedBillingType != BillingType.RECURRING && resolvedInterval != null) {
            throw new BusinessException("INVALID_OFFERING", "Billing interval must be null for non-recurring offerings");
        }
    }

    private void normalizeRequest(OfferingCreateRequest request) {
        if (request.getCode() != null) {
            request.setCode(request.getCode().trim().toUpperCase());
        }
        if (request.getCurrencyCode() != null) {
            request.setCurrencyCode(request.getCurrencyCode().trim().toUpperCase());
        }
        if (request.getBillingType() != BillingType.RECURRING) {
            request.setBillingInterval(null);
        }
    }

    private void normalizeUpdateRequest(OfferingUpdateRequest request) {
        if (request.getCode() != null) {
            request.setCode(request.getCode().trim().toUpperCase());
        }
        if (request.getCurrencyCode() != null) {
            request.setCurrencyCode(request.getCurrencyCode().trim().toUpperCase());
        }
        if (request.getBillingType() != null && request.getBillingType() != BillingType.RECURRING) {
            request.setBillingInterval(null);
        }
    }

    private void normalizeCode(OfferingUpdateRequest request) {
        if (request.getCode() != null) {
            request.setCode(request.getCode().trim().toUpperCase());
        }
    }

    private void validateOwner(UUID tenantId, UUID ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        var owner = userRepository.findByIdAndTenantIdAndDeletedFalse(ownerUserId, tenantId)
                .orElseThrow(() -> new BusinessException("INVALID_OWNER", "Owner must belong to the same tenant"));
        if (Boolean.FALSE.equals(owner.getIsActive())) {
            throw new BusinessException("INVALID_OWNER", "Owner must be an active user of the same tenant");
        }
    }

    private OfferingResponse toResponse(Offering offering) {
        OfferingResponse response = offeringMapper.toResponse(offering);
        response.setOwnerName(offering.getOwnerId() != null
                ? userRepository.findByIdAndTenantIdAndDeletedFalse(offering.getOwnerId(), offering.getTenantId())
                        .map(user -> user.getDisplayName())
                        .orElse(null)
                : null);
        response.setTenantId(offering.getTenantId());
        return response;
    }
}
