package com.shivang.crm.modules.integration.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.integration.entity.ConnectorCredential;

public interface ConnectorCredentialService {
    ConnectorCredential save(ConnectorCredential connectorCredential);

    void deactivate( UUID tenantId, UUID credentialId, UUID userId );

    Optional<ConnectorCredential> findById(UUID tenantId, UUID id);
    List<ConnectorCredential> findByTenantId(UUID tenantId);
    List<ConnectorCredential> findByTenantIdAndConnectorInstanceIdAndCreatedByAndIsActiveTrue(UUID tenantId, UUID connectorInstanceId, UUID createdBy);
    List<ConnectorCredential> findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrue(UUID tenantId, UUID connectorInstanceId);
    Optional<String> resolveCredentialValue(UUID tenantId, UUID connectorInstanceId, UUID userId, String credentialName);
    String decryptValue(ConnectorCredential credential);
    String maskSecret(String value);


     List<ConnectorCredential> findActiveUserCredentials(
            UUID tenantId,
            UUID connectorInstanceId,
            UUID userId
    );

    List<ConnectorCredential> findActiveTenantCredentials(
            UUID tenantId,
            UUID connectorInstanceId
    );

}
