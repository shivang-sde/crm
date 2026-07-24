package com.shivang.crm.modules.dialer.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.dialer.entity.CallProviderLink;

public interface CallProviderLinkService {
    CallProviderLink save(CallProviderLink link);
    Optional<CallProviderLink> findById(UUID id);
    List<CallProviderLink> findByTenantIdAndDeletedFalse(UUID tenantId);
    Optional<CallProviderLink> findByTenantIdAndCallIdAndDeletedFalse(UUID tenantId, UUID callId);
    Optional<CallProviderLink> findByTenantIdAndExternalCallIdAndDeletedFalse(UUID tenantId, String externalCallId);
    Optional<CallProviderLink> findByTenantIdAndCorrelationKeyAndDeletedFalse(UUID tenantId, String correlationKey);
}
