package com.shivang.crm.modules.entitlement.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntitlementRenewalTaskService {

    private static final List<EntitlementStatus> ELIGIBLE_STATUSES =
            List.of(EntitlementStatus.ACTIVE, EntitlementStatus.PENDING);

    private final CustomerEntitlementRepository entitlementRepository;
    private final EntitlementRenewalTaskPersistenceService renewalTaskPersistenceService;

    public int processDueRenewalFollowUps(LocalDate today) {
        List<CustomerEntitlement> dueEntitlements = entitlementRepository
                .findByRenewableTrueAndStatusInAndRenewalDueDateLessThanEqualAndEndDateGreaterThanEqualAndDeletedFalse(
                        ELIGIBLE_STATUSES, today, today);
        if (dueEntitlements.isEmpty()) {
            return 0;
        }

        int created = 0;
        for (CustomerEntitlement entitlement : dueEntitlements) {
            try {
                created += renewalTaskPersistenceService.createRenewalFollowUpIfMissing(entitlement);
            } catch (Exception ex) {
                log.warn("Failed to create renewal follow-up task for entitlement {}", entitlement.getId(), ex);
            }
        }
        if (created > 0) {
            log.info("Created {} renewal follow-up tasks as of {}", created, today);
        }
        return created;
    }
}
