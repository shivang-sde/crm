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
public class EntitlementRenewalTaskScheduler {

    private final EntitlementRenewalTaskService entitlementRenewalTaskService;
    private final EntitlementExpiryProperties entitlementExpiryProperties;

    @Scheduled(
            fixedDelayString = "${app.entitlements.renewal-task-delay-ms:86400000}",
            initialDelayString = "${app.entitlements.renewal-task-delay-ms:86400000}")
    public void processDueRenewalFollowUps() {
        if (!entitlementExpiryProperties.isRenewalTaskEnabled()) {
            return;
        }
        int created = entitlementRenewalTaskService.processDueRenewalFollowUps(LocalDate.now());
        if (created > 0) {
            log.debug("Entitlement renewal task scheduler created {} follow-up tasks", created);
        }
    }
}
