package com.shivang.crm.modules.integration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.integration.entity.ConnectorUserAgent;

@Repository
public interface ConnectorUserAgentRepository
        extends JpaRepository<ConnectorUserAgent, UUID> {

    List<ConnectorUserAgent>
    findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId
    );

    List<ConnectorUserAgent>
    findByTenantIdAndConnectorInstanceIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId,
            UUID connectorInstanceId
    );

    Optional<ConnectorUserAgent>
    findByIdAndTenantIdAndDeletedFalse(
            UUID id,
            UUID tenantId
    );

    Optional<ConnectorUserAgent>
    findFirstByTenantIdAndConnectorInstanceIdAndUserIdAndIsActiveTrueAndDeletedFalse(
            UUID tenantId,
            UUID connectorInstanceId,
            UUID userId
    );

    Optional<ConnectorUserAgent>
    findFirstByTenantIdAndConnectorInstanceIdAndExternalAgentIdAndIsActiveTrueAndDeletedFalse(
            UUID tenantId,
            UUID connectorInstanceId,
            String externalAgentId
    );

    Optional<ConnectorUserAgent>
    findFirstByTenantIdAndConnectorInstanceIdAndExternalAgentNumberAndIsActiveTrueAndDeletedFalse(
            UUID tenantId,
            UUID connectorInstanceId,
            String externalAgentNumber
    );

    boolean existsByTenantIdAndConnectorInstanceIdAndUserIdAndDeletedFalse(
            UUID tenantId,
            UUID connectorInstanceId,
            UUID userId
    );

    boolean existsByTenantIdAndConnectorInstanceIdAndExternalAgentIdAndDeletedFalse(
            UUID tenantId,
            UUID connectorInstanceId,
            String externalAgentId
    );

    boolean existsByTenantIdAndConnectorInstanceIdAndExternalAgentNumberAndDeletedFalse(
            UUID tenantId,
            UUID connectorInstanceId,
            String externalAgentNumber
    );

    boolean existsByTenantIdAndConnectorInstanceIdAndExternalAgentIdAndIdNotAndDeletedFalse(
            UUID tenantId,
            UUID connectorInstanceId,
            String externalAgentId,
            UUID excludedId
    );

    boolean existsByTenantIdAndConnectorInstanceIdAndExternalAgentNumberAndIdNotAndDeletedFalse(
            UUID tenantId,
            UUID connectorInstanceId,
            String externalAgentNumber,
            UUID excludedId
    );

    boolean existsByTenantIdAndConnectorInstanceIdAndUserIdAndIdNotAndDeletedFalse(
            UUID tenantId,
            UUID connectorInstanceId,
            UUID userId,
            UUID excludedId
    );
}