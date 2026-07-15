package com.shivang.crm.modules.integration.service;

import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.integration.entity.ConnectorWebhookConfig;

public interface ConnectorWebhookConfigService {
    /**
     * Save webhook config data.
     *
     * @param config webhook configuration with a plaintext verification secret if present
     * @return saved config with an encrypted verification secret stored in persistence
     */
    ConnectorWebhookConfig save(ConnectorWebhookConfig config);

    Optional<ConnectorWebhookConfig> findByTenantAndConnector(UUID tenantId, UUID connectorInstanceId);

    /**
     * Regenerate the webhook verification secret and return it in plaintext exactly once.
     */
    String regenerateSecret(UUID tenantId, UUID connectorInstanceId);

    String getDecryptedSecret(ConnectorWebhookConfig config);
    String getMaskedSecret(ConnectorWebhookConfig config);
}
