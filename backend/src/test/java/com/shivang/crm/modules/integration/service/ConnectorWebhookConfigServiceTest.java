package com.shivang.crm.modules.integration.service;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ConnectorWebhookConfig;
import com.shivang.crm.modules.integration.repository.ConnectorWebhookConfigRepository;
import com.shivang.crm.modules.integration.service.impl.ConnectorWebhookConfigServiceImpl;
import com.shivang.crm.modules.integration.service.impl.DefaultCredentialEncryptionService;

class ConnectorWebhookConfigServiceTest {

    @Test
    void generatesAndRegeneratesMaskedWebhookSecret() {
        ConnectorWebhookConfigRepository repository = mock(ConnectorWebhookConfigRepository.class);
        DefaultCredentialEncryptionService encryptionService = new DefaultCredentialEncryptionService("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");
        ConnectorCredentialService credentialService = mock(ConnectorCredentialService.class);
        ConnectorWebhookConfigServiceImpl service = new ConnectorWebhookConfigServiceImpl(repository, encryptionService, credentialService);

        ConnectorWebhookConfig config = new ConnectorWebhookConfig();
        config.setVerificationSecret(service.generateSecret());

        String encrypted = encryptionService.encrypt(config.getVerificationSecret());
        config.setVerificationSecret(encrypted);

        assertThat(service.getMaskedSecret(config)).isNotBlank();
    }

    @Test
    void saveEncryptsPlaintextSecretOnlyOnce() {
        ConnectorWebhookConfigRepository repository = mock(ConnectorWebhookConfigRepository.class);
        DefaultCredentialEncryptionService encryptionService = new DefaultCredentialEncryptionService("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");
        ConnectorCredentialService credentialService = mock(ConnectorCredentialService.class);
        ConnectorWebhookConfigServiceImpl service = new ConnectorWebhookConfigServiceImpl(repository, encryptionService, credentialService);

        ConnectorWebhookConfig config = new ConnectorWebhookConfig();
        config.setVerificationSecret("plain-text-secret");

        service.save(config);

        ArgumentCaptor<ConnectorWebhookConfig> savedCaptor = ArgumentCaptor.forClass(ConnectorWebhookConfig.class);
        verify(repository).save(savedCaptor.capture());
        String savedSecret = savedCaptor.getValue().getVerificationSecret();

        assertThat(savedSecret).isNotBlank();
        assertThat(savedSecret).isNotEqualTo("plain-text-secret");
        assertThat(encryptionService.decrypt(savedSecret)).isEqualTo("plain-text-secret");
    }

    @Test
    void regenerateSecretRotatesAndPersistsEncryptedSecret() {
        ConnectorWebhookConfigRepository repository = mock(ConnectorWebhookConfigRepository.class);
        DefaultCredentialEncryptionService encryptionService = new DefaultCredentialEncryptionService("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");
        ConnectorCredentialService credentialService = mock(ConnectorCredentialService.class);
        ConnectorWebhookConfigServiceImpl service = new ConnectorWebhookConfigServiceImpl(repository, encryptionService, credentialService);

        UUID tenantId = UUID.randomUUID();
        UUID connectorInstanceId = UUID.randomUUID();
        ConnectorWebhookConfig config = new ConnectorWebhookConfig();
        config.setTenantId(tenantId);
        config.setConnectorInstance(ConnectorInstance.builder().id(connectorInstanceId).build());
        when(repository.findByTenantId(tenantId)).thenReturn(List.of(config));

        String secret = service.regenerateSecret(tenantId, connectorInstanceId);

        assertThat(secret).isNotBlank();
        assertThat(config.getVerificationSecret()).isNotEqualTo(secret);
        assertThat(encryptionService.decrypt(config.getVerificationSecret())).isEqualTo(secret);
    }

    @Test
    void saveDoesNotDoubleEncryptAlreadyEncryptedSecret() {
        ConnectorWebhookConfigRepository repository = mock(ConnectorWebhookConfigRepository.class);
        DefaultCredentialEncryptionService encryptionService = new DefaultCredentialEncryptionService("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");
        ConnectorCredentialService credentialService = mock(ConnectorCredentialService.class);
        ConnectorWebhookConfigServiceImpl service = new ConnectorWebhookConfigServiceImpl(repository, encryptionService, credentialService);

        ConnectorWebhookConfig config = new ConnectorWebhookConfig();
        String encryptedSecret = encryptionService.encrypt("plaintext-secret");
        config.setVerificationSecret(encryptedSecret);

        service.save(config);

        ArgumentCaptor<ConnectorWebhookConfig> savedCaptor = ArgumentCaptor.forClass(ConnectorWebhookConfig.class);
        verify(repository).save(savedCaptor.capture());
        String savedSecret = savedCaptor.getValue().getVerificationSecret();

        assertThat(savedSecret).isEqualTo(encryptedSecret);
        assertThat(encryptionService.decrypt(savedSecret)).isEqualTo("plaintext-secret");
    }
}
