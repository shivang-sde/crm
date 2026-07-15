package com.shivang.crm.modules.integration.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.integration.entity.ConnectorCredential;

public interface ConnectorCredentialService {
    ConnectorCredential save(ConnectorCredential connectorCredential);
    Optional<ConnectorCredential> findById(UUID tenantId, UUID id);
    List<ConnectorCredential> findByTenantId(UUID tenantId);
    Optional<String> resolveCredentialValue(UUID tenantId, UUID connectorInstanceId, UUID userId, String credentialName);
    String maskSecret(String value);
}
