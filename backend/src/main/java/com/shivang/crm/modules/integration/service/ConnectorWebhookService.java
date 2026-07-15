package com.shivang.crm.modules.integration.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.integration.entity.ConnectorWebhookEvent;

public interface ConnectorWebhookService {
    ConnectorWebhookEvent save(ConnectorWebhookEvent connectorWebhookEvent);
    Optional<ConnectorWebhookEvent> findById(UUID id);
    List<ConnectorWebhookEvent> findByTenantId(UUID tenantId);
    Optional<ConnectorWebhookEvent> findByConnectorInstanceIdAndExternalEventId(java.util.UUID connectorInstanceId, String externalEventId);
    Optional<ConnectorWebhookEvent> findByConnectorInstanceIdAndIdempotencyKey(java.util.UUID connectorInstanceId, String idempotencyKey);
}
