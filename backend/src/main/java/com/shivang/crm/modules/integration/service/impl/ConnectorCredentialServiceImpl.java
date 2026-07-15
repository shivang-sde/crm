package com.shivang.crm.modules.integration.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.integration.entity.ConnectorCredential;
import com.shivang.crm.modules.integration.repository.ConnectorCredentialRepository;
import com.shivang.crm.modules.integration.service.ConnectorCredentialService;
import com.shivang.crm.modules.integration.service.CredentialEncryptionService;
import com.shivang.crm.shared.exception.BusinessException;

@Service
public class ConnectorCredentialServiceImpl implements ConnectorCredentialService {

    private final ConnectorCredentialRepository repository;
    private final CredentialEncryptionService encryptionService;

    public ConnectorCredentialServiceImpl(ConnectorCredentialRepository repository,
                                          CredentialEncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
    }

    @Override
    @Transactional
    public ConnectorCredential save(ConnectorCredential connectorCredential) {
        if (connectorCredential.getEncryptedValue() != null && !connectorCredential.getEncryptedValue().isBlank()) {
            connectorCredential.setEncryptedValue(encryptionService.encrypt(connectorCredential.getEncryptedValue()));
        }
        return repository.save(connectorCredential);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConnectorCredential> findById(UUID tenantId, UUID id) {
        return repository.findById(id)
            .filter(credential -> tenantId.equals(credential.getTenantId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectorCredential> findByTenantId(UUID tenantId) {
        return repository.findByTenantId(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> resolveCredentialValue(UUID tenantId, UUID connectorInstanceId, UUID userId, String credentialName) {
        List<ConnectorCredential> credentials = repository.findByTenantIdAndConnectorInstanceIdAndIsActiveTrue(tenantId, connectorInstanceId);
        Optional<ConnectorCredential> userCredential = credentials.stream()
            .filter(credential -> Boolean.TRUE.equals(credential.getIsActive()))
            .filter(credential -> credential.getCreatedBy() != null && credential.getCreatedBy().equals(userId))
            .filter(credential -> credentialName.equals(credential.getCredentialName()))
            .max(Comparator.comparing(credential -> credential.getCreatedAt() == null ? java.time.Instant.EPOCH : credential.getCreatedAt()));
        if (userCredential.isPresent()) {
            return Optional.ofNullable(decryptValue(userCredential.get().getEncryptedValue()));
        }
        Optional<ConnectorCredential> tenantCredential = credentials.stream()
            .filter(credential -> Boolean.TRUE.equals(credential.getIsActive()))
            .filter(credential -> credential.getCreatedBy() == null)
            .filter(credential -> credentialName.equals(credential.getCredentialName()))
            .max(Comparator.comparing(credential -> credential.getCreatedAt() == null ? java.time.Instant.EPOCH : credential.getCreatedAt()));
        return tenantCredential.map(credential -> decryptValue(credential.getEncryptedValue()));
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

    private String decryptValue(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "Credential value is missing");
        }
        return encryptionService.decrypt(encryptedValue);
    }
}
