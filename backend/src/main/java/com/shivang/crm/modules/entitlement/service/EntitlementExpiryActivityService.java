package com.shivang.crm.modules.entitlement.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntitlementExpiryActivityService {

    private static final String ACTIVITY_TYPE = "ENTITLEMENT_EXPIRED";
    private static final String ACTOR_SOURCE = "ENTITLEMENT_LIFECYCLE";

    private final ActivityService activityService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeExpiredActivity(CustomerEntitlement entitlement) {
        boolean hasAccount = entitlement.getAccountId() != null;
        UUID entityId = hasAccount ? entitlement.getAccountId() : entitlement.getContactId();
        if (entityId == null) {
            return;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entitlementId", entitlement.getId());
        metadata.put("endDate", entitlement.getEndDate());
        metadata.put("renewalDueDate", entitlement.getRenewalDueDate());
        metadata.put("ownerUserId", entitlement.getOwnerId());
        try {
            activityService.logSystemActivity(
                    entitlement.getTenantId(),
                    entityId,
                    hasAccount ? "ACCOUNT" : "CONTACT",
                    ACTIVITY_TYPE,
                    "Entitlement \"" + entitlement.getName() + "\" expired on " + entitlement.getEndDate(),
                    ACTOR_SOURCE,
                    metadata);
        } catch (Exception ex) {
            log.error("Failed to record {} activity for entitlement {}", ACTIVITY_TYPE, entitlement.getId(), ex);
        }
    }
}
