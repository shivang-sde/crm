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

    public DefaultWebhookMappingService(
            ConnectorWebhookMappingRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ConnectorWebhookMapping> loadActiveMappings(
            UUID tenantId,
            UUID connectorInstanceId,
            String triggerKey) {

        List<ConnectorWebhookMapping> mappings =
                repository
                        .findByTenantIdAndConnectorInstanceIdAndTriggerKeyAndIsActiveTrue(
                                tenantId,
                                connectorInstanceId,
                                triggerKey
                        );

        if (mappings == null || mappings.isEmpty()) {
            return seedDefaults(
                    tenantId,
                    connectorInstanceId,
                    triggerKey
            );
        }

        return mappings;
    }

    /**
     * In-memory fallback mappings used only when the tenant has no persisted
     * active mappings for this connector and trigger.
     */
    private List<ConnectorWebhookMapping> seedDefaults(
            UUID tenantId,
            UUID connectorInstanceId,
            String triggerKey) {

        List<ConnectorWebhookMapping> defaults = new ArrayList<>();

        if ("call-connect".equalsIgnoreCase(triggerKey)) {
            addCallConnectDefaults(
                    defaults,
                    tenantId,
                    connectorInstanceId,
                    triggerKey
            );
        } else if ("cdr".equalsIgnoreCase(triggerKey)) {
            addCdrDefaults(
                    defaults,
                    tenantId,
                    connectorInstanceId,
                    triggerKey
            );
        }

        return defaults;
    }

    private void addCallConnectDefaults(
            List<ConnectorWebhookMapping> defaults,
            UUID tenantId,
            UUID connectorInstanceId,
            String triggerKey) {

        /*
         * SellSpark call-connect:
         *
         * call_uniqueid = provider call ID
         * lead_id       = currently "0", so it must NOT be correlationKey
         * meta_data     = currently "NA", so it must NOT be correlationKey
         */
        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.call_uniqueid",
                WebhookMappingTargetScope.CANONICAL,
                "externalCallId",
                true
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.agent",
                WebhookMappingTargetScope.CANONICAL,
                "agentId",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.agent_number",
                WebhookMappingTargetScope.CANONICAL,
                "agentNumber",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.agent_name",
                WebhookMappingTargetScope.ACTIVITY_METADATA,
                "agentName",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.call_with",
                WebhookMappingTargetScope.CANONICAL,
                "callerNumber",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.did_no",
                WebhookMappingTargetScope.ACTIVITY_METADATA,
                "didNumber",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.call_type",
                WebhookMappingTargetScope.CANONICAL,
                "direction",
                false
        ));

        /*
         * Your payload log shows "start_time " with a trailing space.
         * The bracket form supports the exact JSON property name.
         */
        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$['start_time ']",
                WebhookMappingTargetScope.CANONICAL,
                "eventTimestamp",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.meta_data",
                WebhookMappingTargetScope.ACTIVITY_METADATA,
                "metaData",
                false
        ));
    }

    private void addCdrDefaults(
            List<ConnectorWebhookMapping> defaults,
            UUID tenantId,
            UUID connectorInstanceId,
            String triggerKey) {

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.call_id",
                WebhookMappingTargetScope.CANONICAL,
                "externalCallId",
                true
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.uniqueid",
                WebhookMappingTargetScope.CANONICAL,
                "externalEventId",
                false
        ));

        /*
         * Unlike call-connect, the CDR lead_id contains your CRM Call UUID,
         * so it is a valid correlation key.
         */
        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.lead_id",
                WebhookMappingTargetScope.CANONICAL,
                "correlationKey",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.agent",
                WebhookMappingTargetScope.CANONICAL,
                "agentId",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.agent_no",
                WebhookMappingTargetScope.CANONICAL,
                "agentNumber",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.applicant_no",
                WebhookMappingTargetScope.CANONICAL,
                "calleeNumber",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.call_type",
                WebhookMappingTargetScope.CANONICAL,
                "direction",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.start_time",
                WebhookMappingTargetScope.CANONICAL,
                "startedAt",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.end_time",
                WebhookMappingTargetScope.CANONICAL,
                "endedAt",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.call_duration",
                WebhookMappingTargetScope.CANONICAL,
                "durationSeconds",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.rec_path",
                WebhookMappingTargetScope.CANONICAL,
                "recordingUrl",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.resource_url",
                WebhookMappingTargetScope.ACTIVITY_METADATA,
                "recordingUrlFallback",
                false
        ));

        defaults.add(mapping(
                tenantId,
                connectorInstanceId,
                triggerKey,
                "$.status",
                WebhookMappingTargetScope.CANONICAL,
                "providerStatus",
                false
        ));
    }

    private static ConnectorWebhookMapping mapping(
            UUID tenantId,
            UUID connectorInstanceId,
            String triggerKey,
            String sourcePath,
            WebhookMappingTargetScope targetScope,
            String targetPath,
            boolean required) {

        return ConnectorWebhookMapping.builder()
                .tenantId(tenantId)
                .connectorInstanceId(connectorInstanceId)
                .triggerKey(triggerKey)
                .sourcePath(sourcePath)
                .targetScope(targetScope.name())
                .targetPath(targetPath)
                .isRequired(required)
                .isActive(true)
                .build();
    }
}