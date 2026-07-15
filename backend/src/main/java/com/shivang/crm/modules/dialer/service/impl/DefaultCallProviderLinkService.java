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
    public List<CallProviderLink> findByTenantId(UUID tenantId) {
        return callProviderLinkRepository.findByTenantId(tenantId);
    }

    @Override
    public Optional<CallProviderLink> findByCallId(UUID callId) {
        return callProviderLinkRepository.findByCallId(callId);
    }

    @Override
    public Optional<CallProviderLink> findByExternalCallId(String externalCallId) {
        return callProviderLinkRepository.findByExternalCallId(externalCallId);
    }
}
