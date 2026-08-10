package com.shivang.crm.modules.entitlement.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EntitlementProvisioningService {

    private final CustomerEntitlementService customerEntitlementService;

    public void provisionFromWonDeal(UUID tenantId, UUID dealId, UUID userId) {
        customerEntitlementService.provisionFromWonDeal(tenantId, dealId, userId);
    }
}
