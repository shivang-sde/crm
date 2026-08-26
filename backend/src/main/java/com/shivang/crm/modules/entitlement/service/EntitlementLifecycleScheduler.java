package com.shivang.crm.modules.entitlement.service;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shivang.crm.modules.entitlement.config.EntitlementExpiryProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntitlementLifecycleScheduler {

    private final EntitlementExpiryService entitlementExpiryService;
    private final EntitlementExpiryProperties entitlementExpiryProperties;

    @Scheduled(
            fixedDelayString = "${app.entitlements.expiry-delay-ms:86400000}",
            initialDelayString = "${app.entitlements.expiry-delay-ms:86400000}")
    public void expireDueEntitlements() {
        if (!entitlementExpiryProperties.isExpiryEnabled()) {
            return;
        }
        int expired = entitlementExpiryService.expireDueEntitlements(LocalDate.now());
        if (expired > 0) {
            log.debug("Entitlement lifecycle scheduler expired {} entitlements", expired);
        }
    }
}
