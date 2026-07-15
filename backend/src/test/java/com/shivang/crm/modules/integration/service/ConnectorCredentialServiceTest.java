package com.shivang.crm.modules.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.integration.entity.ConnectorCredential;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.repository.ConnectorCredentialRepository;
import com.shivang.crm.modules.integration.service.impl.ConnectorCredentialServiceImpl;
import com.shivang.crm.modules.integration.service.impl.DefaultCredentialEncryptionService;

class ConnectorCredentialServiceTest {

    @Test
    void saveEncryptsValueAndRespectsUserSpecificOverride() {
        ConnectorCredentialRepository repository = mock(ConnectorCredentialRepository.class);
        DefaultCredentialEncryptionService encryptionService = new DefaultCredentialEncryptionService("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");
        ConnectorCredentialServiceImpl service = new ConnectorCredentialServiceImpl(repository, encryptionService);

        UUID tenantId = UUID.randomUUID();
        UUID connectorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ConnectorInstance connectorInstance = new ConnectorInstance();
        connectorInstance.setId(connectorId);
        connectorInstance.setTenantId(tenantId);

        ConnectorCredential tenantCredential = new ConnectorCredential();
        tenantCredential.setTenantId(tenantId);
        tenantCredential.setConnectorInstance(connectorInstance);
        tenantCredential.setCredentialName("shared");
        tenantCredential.setAuthType("API_KEY");
        tenantCredential.setEncryptedValue(encryptionService.encrypt("tenant-secret"));
        tenantCredential.setIsActive(true);
        tenantCredential.setMetadata(Map.of("scope", "tenant"));

        ConnectorCredential userCredential = new ConnectorCredential();
        userCredential.setTenantId(tenantId);
        userCredential.setConnectorInstance(connectorInstance);
        userCredential.setCredentialName("shared");
        userCredential.setAuthType("API_KEY");
        userCredential.setEncryptedValue(encryptionService.encrypt("user-secret"));
        userCredential.setIsActive(true);
        userCredential.setMetadata(Map.of("scope", "user"));
        userCredential.setCreatedBy(userId);

        when(repository.findByTenantIdAndConnectorInstanceIdAndIsActiveTrue(tenantId, connectorId))
            .thenReturn(List.of(tenantCredential, userCredential));
        when(repository.save(any(ConnectorCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorCredential saveResult = service.save(new ConnectorCredential());
        assertThat(saveResult).isNotNull();

        Optional<String> resolved = service.resolveCredentialValue(tenantId, connectorId, userId, "shared");
        assertThat(resolved).contains("user-secret");
    }
}
