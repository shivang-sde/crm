package com.shivang.crm.modules.dialer.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.dialer.entity.CallProviderLink;

public interface CallProviderLinkService {
    CallProviderLink save(CallProviderLink link);
    Optional<CallProviderLink> findById(UUID id);
    List<CallProviderLink> findByTenantId(UUID tenantId);
    Optional<CallProviderLink> findByCallId(UUID callId);
    Optional<CallProviderLink> findByExternalCallId(String externalCallId);
}
