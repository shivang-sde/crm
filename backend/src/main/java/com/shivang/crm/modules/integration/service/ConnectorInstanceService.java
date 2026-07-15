package com.shivang.crm.modules.integration.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.integration.entity.ConnectorInstance;

public interface ConnectorInstanceService {
    ConnectorInstance save(ConnectorInstance connectorInstance);
    ConnectorInstance update(ConnectorInstance connectorInstance);
    ConnectorInstance activate(UUID tenantId, UUID connectorInstanceId, boolean active);
    Optional<ConnectorInstance> findById(UUID tenantId, UUID id);
    List<ConnectorInstance> findByTenantId(UUID tenantId);
    Optional<ConnectorInstance> findActiveByTenantAndProvider(UUID tenantId, String providerKey);
    Optional<ConnectorInstance> findByProviderKeyAndTenantSlug(String providerKey, String tenantSlug);
}
