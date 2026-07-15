package com.shivang.crm.modules.dialer.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.dialer.entity.CallLayoutConfig;

public interface CallLayoutConfigService {
    CallLayoutConfig save(CallLayoutConfig config);
    Optional<CallLayoutConfig> findById(UUID id);
    List<CallLayoutConfig> findByTenantId(UUID tenantId);
}
