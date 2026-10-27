package com.shivang.crm.modules.entitlement.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntitlementExpiryService {

    /**
     * L3.0-P6: actor type for a scheduled mutation. The scheduler acts as the
     * platform, not as any user, so no user id is attached: the repository's
     * null-tolerant metadata drops an absent actorId rather than fabricating one
     * that would point at an arbitrary human.
     */
    private static final String ACTOR_TYPE_SYSTEM = "SYSTEM";
    private static final String ACTOR_SOURCE_SCHEDULER = "SCHEDULER";

    private final CustomerEntitlementRepository entitlementRepository;
    private final EntitlementExpiryActivityService expiryActivityService;
    /** L3.0-P6: sole producer of ENTITLEMENT.EXPIRED. */
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;

    @Transactional
    public int expireDueEntitlements(LocalDate today) {
        List<CustomerEntitlement> dueEntitlements = entitlementRepository
                .findByStatusAndEndDateBeforeAndDeletedFalse(EntitlementStatus.ACTIVE, today);
        if (dueEntitlements.isEmpty()) {
            return 0;
        }

        int expired = 0;
        for (CustomerEntitlement entitlement : dueEntitlements) {
            try {
                expired += expireEntitlement(entitlement);
            } catch (Exception ex) {
                log.warn("Failed to expire entitlement {}", entitlement.getId(), ex);
            }
        }
        if (expired > 0) {
            log.info("Automatically expired {} entitlements as of {}", expired, today);
        }
        return expired;
    }

    /**
     * Idempotency: the candidate query only ever selects ACTIVE entitlements, and
     * this method re-checks the current status before writing. An entitlement
     * that is already EXPIRED therefore produces no status write, no
     * STATUS_CHANGED event and no EXPIRED event, so running the scheduler twice
     * over the same data emits nothing the second time.
     */
    private int expireEntitlement(CustomerEntitlement entitlement) {
        EntitlementStatus previousStatus = entitlement.getStatus();
        if (previousStatus == EntitlementStatus.EXPIRED) {
            return 0;
        }

        entitlement.setStatus(EntitlementStatus.EXPIRED);
        entitlementRepository.save(entitlement);

        // L3.0-P6: the event carries the entitlement's OWN tenant. The scheduler
        // iterates across tenants, so using any single run-level tenant here
        // would misattribute the event.
        UUID tenantId = entitlement.getTenantId();
        publishExpiry(tenantId, entitlement, previousStatus);
        writeExpiredActivitySafely(entitlement);
        return 1;
    }

    private void publishExpiry(UUID tenantId, CustomerEntitlement entitlement, EntitlementStatus previousStatus) {
        Map<String, Object> identifiers = new HashMap<>();
        putIfPresent(identifiers, "accountId", entitlement.getAccountId());
        putIfPresent(identifiers, "contactId", entitlement.getContactId());
        putIfPresent(identifiers, "dealId", entitlement.getDealId());
        putIfPresent(identifiers, "dealLineItemId", entitlement.getDealLineItemId());
        putIfPresent(identifiers, "offeringId", entitlement.getOfferingId());

        Map<String, Object> statusMetadata = new HashMap<>(identifiers);
        statusMetadata.put("actorType", ACTOR_TYPE_SYSTEM);
        statusMetadata.put("source", ACTOR_SOURCE_SCHEDULER);
        statusMetadata.put("previousStatus", previousStatus.name());
        statusMetadata.put("newStatus", EntitlementStatus.EXPIRED.name());
        canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.ENTITLEMENT_ENTITY_TYPE,
                CanonicalCrmEvent.STATUS_CHANGED_EVENT_TYPE,
                entitlement.getId(),
                statusMetadata);

        Map<String, Object> expiredMetadata = new HashMap<>(identifiers);
        expiredMetadata.put("actorType", ACTOR_TYPE_SYSTEM);
        expiredMetadata.put("source", ACTOR_SOURCE_SCHEDULER);
        canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.ENTITLEMENT_ENTITY_TYPE,
                CanonicalCrmEvent.EXPIRED_EVENT_TYPE,
                entitlement.getId(),
                expiredMetadata);
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value.toString());
        }
    }

    private void writeExpiredActivitySafely(CustomerEntitlement entitlement) {
        try {
            expiryActivityService.writeExpiredActivity(entitlement);
        } catch (Exception ex) {
            log.warn("Entitlement {} expired but its ENTITLEMENT_EXPIRED activity could not be recorded",
                    entitlement.getId(), ex);
        }
    }
}
