package com.shivang.crm.modules.integration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.integration.entity.ConnectorWebhookMapping;

@Repository
public interface ConnectorWebhookMappingRepository extends JpaRepository<ConnectorWebhookMapping, UUID> {
    List<ConnectorWebhookMapping> findByTenantIdAndConnectorInstanceIdAndTriggerKeyAndIsActiveTrue(UUID tenantId, UUID connectorInstanceId, String triggerKey);
}
