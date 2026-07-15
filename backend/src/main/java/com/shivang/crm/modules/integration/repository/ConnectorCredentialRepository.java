package com.shivang.crm.modules.integration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.integration.entity.ConnectorCredential;

@Repository
public interface ConnectorCredentialRepository extends JpaRepository<ConnectorCredential, UUID> {
    List<ConnectorCredential> findByTenantId(UUID tenantId);
    List<ConnectorCredential> findByTenantIdAndConnectorInstanceIdAndIsActiveTrue(UUID tenantId, UUID connectorInstanceId);
    List<ConnectorCredential> findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrue(UUID tenantId, UUID connectorInstanceId);
    List<ConnectorCredential> findByTenantIdAndConnectorInstanceIdAndCreatedByAndIsActiveTrue(UUID tenantId, UUID connectorInstanceId, UUID createdBy);
}
