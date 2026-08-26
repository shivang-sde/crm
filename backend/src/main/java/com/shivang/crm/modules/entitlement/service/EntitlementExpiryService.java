package com.shivang.crm.modules.entitlement.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntitlementExpiryService {

    private final CustomerEntitlementRepository entitlementRepository;
    private final EntitlementExpiryActivityService expiryActivityService;

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

    private int expireEntitlement(CustomerEntitlement entitlement) {
        entitlement.setStatus(EntitlementStatus.EXPIRED);
        entitlementRepository.save(entitlement);
        writeExpiredActivitySafely(entitlement);
        return 1;
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
