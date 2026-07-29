package com.shivang.crm.modules.integration.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ConnectorUserAgent;
import com.shivang.crm.modules.integration.repository.ConnectorUserAgentRepository;
import com.shivang.crm.modules.integration.service.ConnectorInstanceService;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConnectorUserAgentService {

    private final ConnectorUserAgentRepository repository;
    private final ConnectorInstanceService connectorInstanceService;
    private final UserRepository userRepository;

    public List<ConnectorUserAgent> findAll(
            UUID tenantId,
            UUID connectorInstanceId) {

        requireTenantId(tenantId);

        if (connectorInstanceId == null) {
            return repository
                    .findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(
                            tenantId
                    );
        }

        requireConnectorInstance(
                tenantId,
                connectorInstanceId
        );

        return repository
                .findByTenantIdAndConnectorInstanceIdAndDeletedFalseOrderByCreatedAtDesc(
                        tenantId,
                        connectorInstanceId
                );
    }

    public ConnectorUserAgent findById(
            UUID tenantId,
            UUID id) {

        requireTenantId(tenantId);

        if (id == null) {
            throw new BusinessException(
                    "INVALID_REQUEST",
                    "Connector user agent ID is required"
            );
        }

        return repository
                .findByIdAndTenantIdAndDeletedFalse(
                        id,
                        tenantId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                "NOT_FOUND",
                                "Connector user-agent mapping not found"
                        )
                );
    }

    public Optional<ConnectorUserAgent> findForUser(
            UUID tenantId,
            UUID connectorInstanceId,
            UUID userId) {

        if (tenantId == null
                || connectorInstanceId == null
                || userId == null) {

            return Optional.empty();
        }

        return repository
                .findFirstByTenantIdAndConnectorInstanceIdAndUserIdAndIsActiveTrueAndDeletedFalse(
                        tenantId,
                        connectorInstanceId,
                        userId
                );
    }

    public Optional<UUID> resolveUserId(
            UUID tenantId,
            UUID connectorInstanceId,
            String externalAgentId,
            String externalAgentNumber) {

        if (tenantId == null || connectorInstanceId == null) {
            return Optional.empty();
        }

        String normalizedAgentId =
                normalize(externalAgentId);

        if (normalizedAgentId != null) {
            Optional<ConnectorUserAgent> byId =
                    repository
                            .findFirstByTenantIdAndConnectorInstanceIdAndExternalAgentIdAndIsActiveTrueAndDeletedFalse(
                                    tenantId,
                                    connectorInstanceId,
                                    normalizedAgentId
                            );

            if (byId.isPresent()) {
                return Optional.of(
                        byId.get().getUserId()
                );
            }
        }

        String normalizedAgentNumber =
                normalize(externalAgentNumber);

        if (normalizedAgentNumber != null) {
            return repository
                    .findFirstByTenantIdAndConnectorInstanceIdAndExternalAgentNumberAndIsActiveTrueAndDeletedFalse(
                            tenantId,
                            connectorInstanceId,
                            normalizedAgentNumber
                    )
                    .map(ConnectorUserAgent::getUserId);
        }

        return Optional.empty();
    }

    @Transactional
    public ConnectorUserAgent create(
            UUID tenantId,
            UUID currentUserId,
            UUID connectorInstanceId,
            UUID userId,
            String externalAgentId,
            String externalAgentNumber,
            Boolean active) {

        requireTenantId(tenantId);

        ConnectorInstance connectorInstance =
                requireConnectorInstance(
                        tenantId,
                        connectorInstanceId
                );

        User user = requireUser(
                tenantId,
                userId
        );

        String normalizedAgentId =
                normalize(externalAgentId);

        String normalizedAgentNumber =
                normalize(externalAgentNumber);

        validateAgentIdentifiers(
                normalizedAgentId,
                normalizedAgentNumber
        );

        validateCreateUniqueness(
                tenantId,
                connectorInstanceId,
                userId,
                normalizedAgentId,
                normalizedAgentNumber
        );

        ConnectorUserAgent mapping =
                ConnectorUserAgent.builder()
                        .tenantId(tenantId)
                        .connectorInstance(connectorInstance)
                        .userId(user.getId())
                        .externalAgentId(normalizedAgentId)
                        .externalAgentNumber(
                                normalizedAgentNumber
                        )
                        .isActive(
                                active == null || active
                        )
                        .createdBy(currentUserId)
                        .updatedBy(currentUserId)
                        .build();

        return repository.save(mapping);
    }

    @Transactional
    public ConnectorUserAgent update(
            UUID tenantId,
            UUID currentUserId,
            UUID mappingId,
            UUID userId,
            String externalAgentId,
            String externalAgentNumber,
            Boolean active) {

        ConnectorUserAgent mapping =
                findById(
                        tenantId,
                        mappingId
                );

        UUID connectorInstanceId =
                mapping.getConnectorInstance().getId();

        UUID resolvedUserId =
                userId != null
                        ? userId
                        : mapping.getUserId();

        requireUser(
                tenantId,
                resolvedUserId
        );

        String normalizedAgentId =
                normalize(externalAgentId);

        String normalizedAgentNumber =
                normalize(externalAgentNumber);

        validateAgentIdentifiers(
                normalizedAgentId,
                normalizedAgentNumber
        );

        validateUpdateUniqueness(
                tenantId,
                connectorInstanceId,
                resolvedUserId,
                normalizedAgentId,
                normalizedAgentNumber,
                mappingId
        );

        mapping.setUserId(resolvedUserId);
        mapping.setExternalAgentId(
                normalizedAgentId
        );
        mapping.setExternalAgentNumber(
                normalizedAgentNumber
        );

        if (active != null) {
            mapping.setIsActive(active);
        }

        mapping.setUpdatedBy(currentUserId);

        return repository.save(mapping);
    }

    @Transactional
    public ConnectorUserAgent updateStatus(
            UUID tenantId,
            UUID currentUserId,
            UUID mappingId,
            boolean active) {

        ConnectorUserAgent mapping =
                findById(
                        tenantId,
                        mappingId
                );

        mapping.setIsActive(active);
        mapping.setUpdatedBy(currentUserId);

        return repository.save(mapping);
    }

    @Transactional
    public void delete(
            UUID tenantId,
            UUID currentUserId,
            UUID mappingId) {

        ConnectorUserAgent mapping =
                findById(
                        tenantId,
                        mappingId
                );

        mapping.setDeleted(true);
        mapping.setDeletedAt(Instant.now());
        mapping.setDeletedBy(currentUserId);
        mapping.setIsActive(false);
        mapping.setUpdatedBy(currentUserId);

        repository.save(mapping);
    }

    private ConnectorInstance requireConnectorInstance(
            UUID tenantId,
            UUID connectorInstanceId) {

        if (connectorInstanceId == null) {
            throw new BusinessException(
                    "INVALID_REQUEST",
                    "Connector instance ID is required"
            );
        }

        return connectorInstanceService
                .findById(
                        tenantId,
                        connectorInstanceId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                "NOT_FOUND",
                                "Connector instance not found"
                        )
                );
    }

    private User requireUser(
            UUID tenantId,
            UUID userId) {

        if (userId == null) {
            throw new BusinessException(
                    "INVALID_REQUEST",
                    "User ID is required"
            );
        }

        /*
         * Prefer a tenant-scoped repository method.
         *
         * Adjust this method name if your UserRepository uses a different
         * convention.
         */
        return userRepository
                .findByIdAndTenantId(
                        userId,
                        tenantId
                )
                .filter(user ->
                        Boolean.TRUE.equals(
                                user.getIsActive()
                        ))
                .orElseThrow(() ->
                        new BusinessException(
                                "USER_NOT_FOUND",
                                "Active CRM user not found in this tenant"
                        )
                );
    }

    private void validateCreateUniqueness(
            UUID tenantId,
            UUID connectorInstanceId,
            UUID userId,
            String externalAgentId,
            String externalAgentNumber) {

        if (repository
                .existsByTenantIdAndConnectorInstanceIdAndUserIdAndDeletedFalse(
                        tenantId,
                        connectorInstanceId,
                        userId
                )) {

            throw new BusinessException(
                    "USER_AGENT_MAPPING_EXISTS",
                    "This user already has an agent mapping for the connector"
            );
        }

        if (externalAgentId != null
                && repository
                        .existsByTenantIdAndConnectorInstanceIdAndExternalAgentIdAndDeletedFalse(
                                tenantId,
                                connectorInstanceId,
                                externalAgentId
                        )) {

            throw new BusinessException(
                    "EXTERNAL_AGENT_ID_EXISTS",
                    "This external agent ID is already mapped"
            );
        }

        if (externalAgentNumber != null
                && repository
                        .existsByTenantIdAndConnectorInstanceIdAndExternalAgentNumberAndDeletedFalse(
                                tenantId,
                                connectorInstanceId,
                                externalAgentNumber
                        )) {

            throw new BusinessException(
                    "EXTERNAL_AGENT_NUMBER_EXISTS",
                    "This external agent number is already mapped"
            );
        }
    }

    private void validateUpdateUniqueness(
            UUID tenantId,
            UUID connectorInstanceId,
            UUID userId,
            String externalAgentId,
            String externalAgentNumber,
            UUID mappingId) {

        if (repository
                .existsByTenantIdAndConnectorInstanceIdAndUserIdAndIdNotAndDeletedFalse(
                        tenantId,
                        connectorInstanceId,
                        userId,
                        mappingId
                )) {

            throw new BusinessException(
                    "USER_AGENT_MAPPING_EXISTS",
                    "This user already has another mapping for the connector"
            );
        }

        if (externalAgentId != null
                && repository
                        .existsByTenantIdAndConnectorInstanceIdAndExternalAgentIdAndIdNotAndDeletedFalse(
                                tenantId,
                                connectorInstanceId,
                                externalAgentId,
                                mappingId
                        )) {

            throw new BusinessException(
                    "EXTERNAL_AGENT_ID_EXISTS",
                    "This external agent ID is already mapped"
            );
        }

        if (externalAgentNumber != null
                && repository
                        .existsByTenantIdAndConnectorInstanceIdAndExternalAgentNumberAndIdNotAndDeletedFalse(
                                tenantId,
                                connectorInstanceId,
                                externalAgentNumber,
                                mappingId
                        )) {

            throw new BusinessException(
                    "EXTERNAL_AGENT_NUMBER_EXISTS",
                    "This external agent number is already mapped"
            );
        }
    }

    private void validateAgentIdentifiers(
            String externalAgentId,
            String externalAgentNumber) {

        if (externalAgentId == null
                && externalAgentNumber == null) {

            throw new BusinessException(
                    "INVALID_REQUEST",
                    "At least one external agent identifier is required"
            );
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private void requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new BusinessException(
                    "TENANT_REQUIRED",
                    "Tenant context is required"
            );
        }
    }
}