package com.shivang.crm.modules.integration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.integration.entity.ConnectorWebhookEvent;

@Repository
public interface ConnectorWebhookEventRepository extends JpaRepository<ConnectorWebhookEvent, UUID> {
    List<ConnectorWebhookEvent> findByTenantId(UUID tenantId);
    Optional<ConnectorWebhookEvent> findByConnectorInstanceIdAndExternalEventId(UUID connectorInstanceId, String externalEventId);
    Optional<ConnectorWebhookEvent> findByConnectorInstanceIdAndIdempotencyKey(UUID connectorInstanceId, String idempotencyKey);
}
