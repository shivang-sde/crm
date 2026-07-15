package com.shivang.crm.modules.integration.service.impl;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.integration.entity.ConnectorWebhookConfig;
import com.shivang.crm.modules.integration.repository.ConnectorWebhookConfigRepository;
import com.shivang.crm.modules.integration.service.ConnectorCredentialService;
import com.shivang.crm.modules.integration.service.CredentialEncryptionService;
import com.shivang.crm.shared.exception.BusinessException;

@Service
public class ConnectorWebhookConfigServiceImpl implements com.shivang.crm.modules.integration.service.ConnectorWebhookConfigService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConnectorWebhookConfigRepository repository;
    private final CredentialEncryptionService encryptionService;
    private final ConnectorCredentialService credentialService;

    public ConnectorWebhookConfigServiceImpl(ConnectorWebhookConfigRepository repository,
                                             CredentialEncryptionService encryptionService,
                                             ConnectorCredentialService credentialService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.credentialService = credentialService;
    }

    @Transactional
    public ConnectorWebhookConfig save(ConnectorWebhookConfig config) {
        if (config.getVerificationSecret() == null || config.getVerificationSecret().isBlank()) {
            config.setVerificationSecret(generateSecret());
        }
        if (!isSecretEncrypted(config.getVerificationSecret())) {
            config.setVerificationSecret(encryptionService.encrypt(config.getVerificationSecret()));
        }
        return repository.save(config);
    }

    @Transactional
    public String regenerateSecret(UUID tenantId, UUID connectorInstanceId) {
        ConnectorWebhookConfig config = repository.findByTenantId(tenantId).stream()
            .filter(item -> item.getConnectorInstance() != null && item.getConnectorInstance().getId().equals(connectorInstanceId))
            .findFirst()
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Webhook config not found"));
        String secret = generateSecret();
        config.setVerificationSecret(secret);
        save(config);
        return secret;
    }

    @Transactional(readOnly = true)
    public Optional<ConnectorWebhookConfig> findByTenantAndConnector(UUID tenantId, UUID connectorInstanceId) {
        return repository.findByTenantId(tenantId).stream()
            .filter(item -> item.getConnectorInstance() != null && item.getConnectorInstance().getId().equals(connectorInstanceId))
            .findFirst();
    }

    @Transactional(readOnly = true)
    public String getMaskedSecret(ConnectorWebhookConfig config) {
        if (config == null || config.getVerificationSecret() == null || config.getVerificationSecret().isBlank()) {
            return "***";
        }
        String decryptedSecret = encryptionService.decrypt(config.getVerificationSecret());
        String masked = credentialService.maskSecret(decryptedSecret);
        if (masked == null || masked.isBlank() || "***".equals(masked) && decryptedSecret != null && !decryptedSecret.isBlank()) {
            return maskSecret(decryptedSecret);
        }
        return masked;
    }

    private boolean isSecretEncrypted(String secret) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        try {
            encryptionService.decrypt(secret);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public String getDecryptedSecret(ConnectorWebhookConfig config) {
        if (config == null || config.getVerificationSecret() == null || config.getVerificationSecret().isBlank()) {
            return null;
        }
        return encryptionService.decrypt(config.getVerificationSecret());
    }

    public String generateSecret() {
        byte[] randomBytes = new byte[24];
        RANDOM.nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes);
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        return "***" + value.substring(value.length() - 4);
    }
}
