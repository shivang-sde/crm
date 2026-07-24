package com.shivang.crm.modules.dialer.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.dialer.repository.CallProviderLinkRepository;
import com.shivang.crm.modules.dialer.service.CallProviderLinkService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultCallProviderLinkService implements CallProviderLinkService {

    private final CallProviderLinkRepository callProviderLinkRepository;

    @Override
    public CallProviderLink save(CallProviderLink link) {
        return callProviderLinkRepository.save(link);
    }

    @Override
    public Optional<CallProviderLink> findById(UUID id) {
        return callProviderLinkRepository.findById(id);
    }

    @Override
    public List<CallProviderLink> findByTenantIdAndDeletedFalse(UUID tenantId) {
        return callProviderLinkRepository.findByTenantIdAndDeletedFalse(tenantId);
    }

    @Override
    public Optional<CallProviderLink> findByTenantIdAndCallIdAndDeletedFalse(UUID tenantId, UUID callId) {
        return callProviderLinkRepository.findByTenantIdAndCallIdAndDeletedFalse(tenantId, callId);
    }

    @Override
    public Optional<CallProviderLink> findByTenantIdAndExternalCallIdAndDeletedFalse(UUID tenantId, String externalCallId) {
        return callProviderLinkRepository.findByTenantIdAndExternalCallIdAndDeletedFalse(tenantId, externalCallId);
    }

    @Override
    public Optional<CallProviderLink> findByTenantIdAndCorrelationKeyAndDeletedFalse(UUID tenantId, String correlationKey) {
        return callProviderLinkRepository.findByTenantIdAndCorrelationKeyAndDeletedFalse(tenantId, correlationKey);
    }
}
