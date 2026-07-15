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

    private List<ConnectorWebhookMapping> seedDefaults(UUID tenantId, UUID connectorInstanceId, String triggerKey) {
        List<ConnectorWebhookMapping> defaults = new ArrayList<>();
        if ("call-connect".equals(triggerKey) || "cdr".equals(triggerKey)) {
            if ("call-connect".equals(triggerKey)) {
                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.call_id")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("externalCallId")
                    .isRequired(true)
                    .build());

                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.agent_id")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("agentId")
                    .build());

                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.caller_number")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("callerNumber")
                    .build());

                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.callee_number")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("calleeNumber")
                    .build());

                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.direction")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("direction")
                    .build());

                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.timestamp")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("eventTimestamp")
                    .build());
            }

            if ("cdr".equals(triggerKey)) {
                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.call_id")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("externalCallId")
                    .isRequired(true)
                    .build());

                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.agent_id")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("agentId")
                    .build());

                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.duration")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("durationSeconds")
                    .build());

                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.status")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("providerStatus")
                    .build());

                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.recording_url")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("recordingUrl")
                    .build());

                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.disposition")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("disposition")
                    .build());

                defaults.add(ConnectorWebhookMapping.builder()
                    .tenantId(tenantId)
                    .connectorInstanceId(connectorInstanceId)
                    .triggerKey(triggerKey)
                    .sourcePath("$.timestamp")
                    .targetScope(WebhookMappingTargetScope.CANONICAL.name())
                    .targetPath("eventTimestamp")
                    .build());
            }
        }

        return defaults;
    }
}
