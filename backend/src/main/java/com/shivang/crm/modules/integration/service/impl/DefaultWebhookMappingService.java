package com.shivang.crm.modules.integration.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.integration.entity.ConnectorWebhookMapping;
import com.shivang.crm.modules.integration.repository.ConnectorWebhookMappingRepository;
import com.shivang.crm.modules.integration.service.WebhookMappingService;
import com.shivang.crm.modules.integration.webhook.WebhookMappingTargetScope;

@Service
public class DefaultWebhookMappingService implements WebhookMappingService {

    private final ConnectorWebhookMappingRepository repository;

    public DefaultWebhookMappingService(ConnectorWebhookMappingRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ConnectorWebhookMapping> loadActiveMappings(UUID tenantId, UUID connectorInstanceId, String triggerKey) {
        List<ConnectorWebhookMapping> list = repository.findByTenantIdAndConnectorInstanceIdAndTriggerKeyAndIsActiveTrue(tenantId, connectorInstanceId, triggerKey);
        if (list == null || list.isEmpty()) {
            return seedDefaults(tenantId, connectorInstanceId, triggerKey);
        }
        return list;
    }

    /**
     * Seed default mappings that match actual SellSpark webhook payloads.
     * These are NOT persisted — they are in-memory defaults used when no
     * custom mappings are configured for the connector instance.
     */
    private List<ConnectorWebhookMapping> seedDefaults(UUID tenantId, UUID connectorInstanceId, String triggerKey) {
        List<ConnectorWebhookMapping> defaults = new ArrayList<>();

        if ("call-connect".equals(triggerKey)) {
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.call_uniqueid", "CANONICAL", "externalCallId", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.lead_id", "CANONICAL", "correlationKey", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.agent", "CANONICAL", "agentId", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.agent_number", "CANONICAL", "agentNumber", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.agent_name", "PROVIDER_METADATA", "agentName", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.call_with", "CANONICAL", "callerNumber", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.did_no", "PROVIDER_METADATA", "didNumber", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.call_type", "CANONICAL", "direction", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.start_time", "CANONICAL", "eventTimestamp", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.meta_data", "PROVIDER_METADATA", "metaData", false));
        }

        if ("cdr".equals(triggerKey)) {
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.call_id", "CANONICAL", "externalCallId", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.uniqueid", "CANONICAL", "externalEventId", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.lead_id", "CANONICAL", "correlationKey", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.agent", "CANONICAL", "agentId", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.agent_no", "CANONICAL", "agentNumber", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.applicant_no", "CANONICAL", "calleeNumber", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.call_type", "CANONICAL", "direction", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.start_time", "CANONICAL", "startedAt", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.end_time", "CANONICAL", "endedAt", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.call_duration", "CANONICAL", "durationSeconds", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.rec_path", "CANONICAL", "recordingUrl", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.resource_url", "PROVIDER_METADATA", "recordingUrlFallback", false));
            defaults.add(mapping(tenantId, connectorInstanceId, triggerKey, "$.status", "CANONICAL", "providerStatus", false));
        }

        return defaults;
    }

    private static ConnectorWebhookMapping mapping(UUID tenantId, UUID connectorInstanceId, String triggerKey,
                                                    String sourcePath, String targetScope, String targetPath,
                                                    boolean isRequired) {
        return ConnectorWebhookMapping.builder()
            .tenantId(tenantId)
            .connectorInstanceId(connectorInstanceId)
            .triggerKey(triggerKey)
            .sourcePath(sourcePath)
            .targetScope(targetScope)
            .targetPath(targetPath)
            .isRequired(isRequired)
            .build();
    }
}
