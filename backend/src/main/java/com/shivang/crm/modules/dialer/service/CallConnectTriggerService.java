package com.shivang.crm.modules.dialer.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.dialer.entity.CallConnectTrigger;

public interface CallConnectTriggerService {
    CallConnectTrigger save(CallConnectTrigger trigger);
    Optional<CallConnectTrigger> findById(UUID id);
    List<CallConnectTrigger> findByTenantId(UUID tenantId);
    List<CallConnectTrigger> findActiveByTenantAndDirection(UUID tenantId, String direction);
}
