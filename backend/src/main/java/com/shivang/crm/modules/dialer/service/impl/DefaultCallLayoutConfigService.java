package com.shivang.crm.modules.dialer.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.dialer.entity.CallLayoutConfig;
import com.shivang.crm.modules.dialer.repository.CallLayoutConfigRepository;
import com.shivang.crm.modules.dialer.service.CallLayoutConfigService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultCallLayoutConfigService implements CallLayoutConfigService {

    private final CallLayoutConfigRepository callLayoutConfigRepository;

    @Override
    public CallLayoutConfig save(CallLayoutConfig config) {
        return callLayoutConfigRepository.save(config);
    }

    @Override
    public Optional<CallLayoutConfig> findById(UUID id) {
        return callLayoutConfigRepository.findById(id);
    }

    @Override
    public List<CallLayoutConfig> findByTenantId(UUID tenantId) {
        return callLayoutConfigRepository.findByTenantId(tenantId);
    }
}
