package com.shivang.crm.modules.integration.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.integration.entity.ConnectorCredential;
import com.shivang.crm.modules.integration.repository.ConnectorCredentialRepository;
import com.shivang.crm.modules.integration.service.ConnectorCredentialService;
import com.shivang.crm.modules.integration.service.CredentialEncryptionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultConnectorCredentialService implements ConnectorCredentialService {

    private final ConnectorCredentialRepository connectorCredentialRepository;
    private final CredentialEncryptionService credentialEncryptionService;

    @Override
    public ConnectorCredential save(ConnectorCredential connectorCredential) {
        if (connectorCredential.getEncryptedValue() != null && !connectorCredential.getEncryptedValue().isBlank()) {
            connectorCredential.setEncryptedValue(credentialEncryptionService.encrypt(connectorCredential.getEncryptedValue()));
        }
        return connectorCredentialRepository.save(connectorCredential);
    }

    @Override
    public Optional<ConnectorCredential> findById(UUID tenantId, UUID id) {
        return connectorCredentialRepository.findById(id)
            .filter(credential -> tenantId.equals(credential.getTenantId()));
    }

    @Override
    public List<ConnectorCredential> findByTenantId(UUID tenantId) {
        return connectorCredentialRepository.findByTenantId(tenantId);
    }

    @Override
    public Optional<String> resolveCredentialValue(UUID tenantId, UUID connectorInstanceId, UUID userId, String credentialName) {
        return connectorCredentialRepository.findByTenantIdAndConnectorInstanceIdAndIsActiveTrue(tenantId, connectorInstanceId).stream()
            .filter(credential -> credentialName.equals(credential.getCredentialName()))
            .filter(credential -> credential.getCreatedBy() == null || userId.equals(credential.getCreatedBy()))
            .findFirst()
            .map(credential -> credentialEncryptionService.decrypt(credential.getEncryptedValue()));
    }

    @Override
    public String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        return "***" + value.substring(value.length() - 4);
    }
}
