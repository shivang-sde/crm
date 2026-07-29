package com.shivang.crm.modules.integration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.integration.entity.ConnectorCredential;

@Repository
public interface ConnectorCredentialRepository extends JpaRepository<ConnectorCredential, UUID> {

    List<ConnectorCredential> findByTenantId(UUID tenantId);

    List<ConnectorCredential> findByTenantIdAndConnectorInstanceIdAndIsActiveTrue(UUID tenantId, UUID connectorInstanceId);

    List<ConnectorCredential> findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrue(UUID tenantId, UUID connectorInstanceId);

    List<ConnectorCredential> findByTenantIdAndConnectorInstanceIdAndCreatedByAndIsActiveTrue(UUID tenantId, UUID connectorInstanceId, UUID createdBy);

    @Modifying
    @Query("""
    update ConnectorCredential c
       set c.isActive = false,
           c.updatedBy = :updatedBy
     where c.id = :credentialId
       and c.tenantId = :tenantId
""")
    int deactivate(
            @Param("tenantId") UUID tenantId,
            @Param("credentialId") UUID credentialId,
            @Param("updatedBy") UUID updatedBy
    );

    List<ConnectorCredential>
            findByTenantIdAndConnectorInstanceIdAndCredentialScopeAndOwnerUserIdAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                    UUID tenantId,
                    UUID connectorInstanceId,
                    String credentialScope,
                    UUID ownerUserId
            );

    List<ConnectorCredential>
            findByTenantIdAndConnectorInstanceIdAndCredentialScopeAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                    UUID tenantId,
                    UUID connectorInstanceId,
                    String credentialScope
            );

    List<ConnectorCredential>
            findByTenantIdAndConnectorInstanceIdAndCreatedByAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                    UUID tenantId,
                    UUID connectorInstanceId,
                    UUID createdBy
            );

    List<ConnectorCredential>
            findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                    UUID tenantId,
                    UUID connectorInstanceId
            );
}
