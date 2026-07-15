package com.shivang.crm.modules.integration.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.integration.entity.ConnectorWebhookEvent;
import com.shivang.crm.modules.integration.repository.ConnectorWebhookEventRepository;
import com.shivang.crm.modules.integration.service.ConnectorWebhookService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultConnectorWebhookService implements ConnectorWebhookService {

    private final ConnectorWebhookEventRepository connectorWebhookEventRepository;

    @Override
    public ConnectorWebhookEvent save(ConnectorWebhookEvent connectorWebhookEvent) {
        return connectorWebhookEventRepository.save(connectorWebhookEvent);
    }

    @Override
    public Optional<ConnectorWebhookEvent> findById(UUID id) {
        return connectorWebhookEventRepository.findById(id);
    }

    @Override
    public List<ConnectorWebhookEvent> findByTenantId(UUID tenantId) {
        return connectorWebhookEventRepository.findByTenantId(tenantId);
    }

    @Override
    public Optional<ConnectorWebhookEvent> findByConnectorInstanceIdAndExternalEventId(UUID connectorInstanceId, String externalEventId) {
        return connectorWebhookEventRepository.findByConnectorInstanceIdAndExternalEventId(connectorInstanceId, externalEventId);
    }

    @Override
    public Optional<ConnectorWebhookEvent> findByConnectorInstanceIdAndIdempotencyKey(UUID connectorInstanceId, String idempotencyKey) {
        return connectorWebhookEventRepository.findByConnectorInstanceIdAndIdempotencyKey(connectorInstanceId, idempotencyKey);
    }
}
