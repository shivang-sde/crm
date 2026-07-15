package com.shivang.crm.modules.integration.service;

import java.util.List;
import java.util.UUID;

import com.shivang.crm.modules.integration.entity.ConnectorWebhookMapping;

public interface WebhookMappingService {
    List<ConnectorWebhookMapping> loadActiveMappings(UUID tenantId, UUID connectorInstanceId, String triggerKey);
}
