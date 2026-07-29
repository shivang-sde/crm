package com.shivang.crm.modules.integration.service.impl;

import java.time.Instant;
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
    public ConnectorCredential save(
            ConnectorCredential connectorCredential) {

        /*
         * Prevent encrypting the same value repeatedly during ordinary updates.
         *
         * This implementation assumes callers pass plain text only when creating
         * or replacing a credential. For a more robust design, use a dedicated
         * create/update credential command instead of directly saving entities.
         */
        if (connectorCredential.getEncryptedValue() != null
                && !connectorCredential.getEncryptedValue().isBlank()) {

            connectorCredential.setEncryptedValue(
                    encryptionService.encrypt(
                            connectorCredential.getEncryptedValue()
                    )
            );
        }

        return repository.save(connectorCredential);
    }

    @Override
    @Transactional
    public void deactivate(UUID tenantId, UUID credentialId, UUID updatedBy) {
        Optional<ConnectorCredential> credentialOpt = repository.findById(credentialId);

        if (credentialOpt.isEmpty()) {
            throw new BusinessException("CREDENTIAL_NOT_FOUND", "Credential not found");
        }

        ConnectorCredential credential = credentialOpt.get();

        if (!tenantId.equals(credential.getTenantId())) {
            throw new BusinessException("TENANT_MISMATCH", "Tenant ID does not match");
        }

        if (!updatedBy.equals(credential.getCreatedBy())) {
            throw new BusinessException("USER_MISMATCH", "User ID does not match");
        }

        int updated = repository.deactivate(tenantId, credentialId, updatedBy);

        if (updated == 0) {
        throw new BusinessException(
                "NOT_FOUND",
                "Credential not found"
        );
    }

    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConnectorCredential> findById(
            UUID tenantId,
            UUID id) {

        return repository.findById(id)
                .filter(credential
                        -> !Boolean.TRUE.equals(credential.getDeleted()))
                .filter(credential
                        -> tenantId.equals(credential.getTenantId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectorCredential> findByTenantId(
            UUID tenantId) {

        return repository.findByTenantId(tenantId)
                .stream()
                .filter(credential
                        -> !Boolean.TRUE.equals(credential.getDeleted()))
                .toList();
    }

    @Override
    public List<ConnectorCredential> findByTenantIdAndConnectorInstanceIdAndCreatedByAndIsActiveTrue(UUID tenantId, UUID connectorInstanceId, UUID createdBy) {
        return repository.findByTenantIdAndConnectorInstanceIdAndCreatedByAndIsActiveTrue(tenantId, connectorInstanceId, createdBy);
    }

    @Override
    public List<ConnectorCredential> findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrue(UUID tenantId, UUID connectorInstanceId) {
        return repository.findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrue(tenantId, connectorInstanceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectorCredential> findActiveUserCredentials(
            UUID tenantId,
            UUID connectorInstanceId,
            UUID userId) {

        if (tenantId == null
                || connectorInstanceId == null
                || userId == null) {

            return List.of();
        }

        return repository
                .findByTenantIdAndConnectorInstanceIdAndCreatedByAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                        tenantId,
                        connectorInstanceId,
                        userId
                );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectorCredential> findActiveTenantCredentials(
            UUID tenantId,
            UUID connectorInstanceId) {

        if (tenantId == null || connectorInstanceId == null) {
            return List.of();
        }

        return repository
                .findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                        tenantId,
                        connectorInstanceId
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> resolveCredentialValue(
            UUID tenantId,
            UUID connectorInstanceId,
            UUID userId,
            String credentialName) {

        if (credentialName == null || credentialName.isBlank()) {
            return Optional.empty();
        }

        if (userId != null) {
            Optional<ConnectorCredential> userCredential
                    = findActiveUserCredentials(
                            tenantId,
                            connectorInstanceId,
                            userId
                    )
                            .stream()
                            .filter(credential
                                    -> credentialName.equals(
                                    credential.getCredentialName()
                            ))
                            .max(Comparator.comparing(
                                    credential
                                    -> credential.getCreatedAt() == null
                                    ? Instant.EPOCH
                                    : credential.getCreatedAt()
                            ));

            if (userCredential.isPresent()) {
                return Optional.of(
                        decryptValue(userCredential.get())
                );
            }
        }

        return findActiveTenantCredentials(
                tenantId,
                connectorInstanceId
        )
                .stream()
                .filter(credential
                        -> credentialName.equals(
                        credential.getCredentialName()
                ))
                .max(Comparator.comparing(
                        credential
                        -> credential.getCreatedAt() == null
                        ? Instant.EPOCH
                        : credential.getCreatedAt()
                ))
                .map(this::decryptValue);
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

    @Override
    public String decryptValue(ConnectorCredential credential) {
        if (credential == null) {
            throw new BusinessException(
                    "INVALID_REQUEST",
                    "Credential is required"
            );
        }
        return decryptValue(credential.getEncryptedValue());
    }

    private String decryptValue(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "Credential value is missing");
        }
        return encryptionService.decrypt(encryptedValue);
    }
}
