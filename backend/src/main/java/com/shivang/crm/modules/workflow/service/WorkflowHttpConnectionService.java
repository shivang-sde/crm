package com.shivang.crm.modules.workflow.service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.integration.outbound.OutboundHttpConnection;
import com.shivang.crm.modules.integration.outbound.OutboundHttpConnectionCredential;
import com.shivang.crm.modules.integration.outbound.OutboundHttpConnectionCredentialRepository;
import com.shivang.crm.modules.integration.outbound.OutboundHttpConnectionRepository;
import com.shivang.crm.modules.integration.outbound.OutboundHttpRequest;
import com.shivang.crm.modules.integration.outbound.OutboundHttpResult;
import com.shivang.crm.modules.integration.outbound.OutboundHttpMethod;
import com.shivang.crm.modules.integration.service.CredentialEncryptionService;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Tenant-scoped provisioning for Outbound HTTP connections used by the
 * HTTP_API workflow action.
 *
 * Secrets are accepted only inside credential values, encrypted at rest
 * through the shared AES-256-GCM {@link CredentialEncryptionService}, and are
 * never returned by any API in this service. The workflow definition stores a
 * connectionId reference only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowHttpConnectionService {

    private static final Set<String> SUPPORTED_AUTH_TYPES = Set.of("NONE", "API_KEY", "BEARER", "BASIC_AUTH");

    private final OutboundHttpConnectionRepository connectionRepository;
    private final OutboundHttpConnectionCredentialRepository credentialRepository;
    private final CredentialEncryptionService encryptionService;
    private final WorkflowHttpApiService httpApiService;
    private final ObjectMapper objectMapper;

    public record HttpConnectionRequest(String name, String authType, Map<String, Object> credential, Boolean active) {
    }

    public record HttpConnectionResponse(
        UUID id, String name, String authType, boolean active,
        boolean credentialConfigured, Instant createdAt, Instant updatedAt
    ) {
    }

    public record ConnectionTestRequest(String url) {
    }

    public record ConnectionTestResponse(boolean success, int statusCode, String message, UUID correlationId) {
    }

    @Transactional(readOnly = true)
    public java.util.List<HttpConnectionResponse> list(UUID tenantId) {
        return connectionRepository.findByTenantIdAndActiveTrueAndDeletedFalseOrderByNameAsc(tenantId).stream()
            .map(WorkflowHttpConnectionService::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public HttpConnectionResponse get(UUID tenantId, UUID id) {
        return toResponse(load(tenantId, id));
    }

    @Transactional
    public HttpConnectionResponse create(UUID tenantId, UUID actorId, HttpConnectionRequest request) {
        String authType = normalizeAuthType(request.authType());
        validateCredentialPairing(authType, request.credential());
        OutboundHttpConnection connection = OutboundHttpConnection.builder()
            .tenantId(tenantId)
            .name(requireName(request.name()))
            .authType(authType)
            .active(request.active() == null || request.active())
            .build();
        if (request.credential() != null && !request.credential().isEmpty()) {
            connection.setCredentialId(storeCredential(tenantId, actorId, authType, request.credential()));
        }
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional
    public HttpConnectionResponse update(UUID tenantId, UUID actorId, UUID id, HttpConnectionRequest request) {
        OutboundHttpConnection connection = load(tenantId, id);
        if (request.name() != null && !request.name().isBlank()) {
            connection.setName(request.name().trim());
        }
        if (request.active() != null) {
            connection.setActive(request.active());
        }
        if (request.authType() != null && !request.authType().isBlank()) {
            String authType = normalizeAuthType(request.authType());
            boolean replacingCredential = request.credential() != null;
            if ("NONE".equals(authType)) {
                if (replacingCredential && !request.credential().isEmpty()) {
                    throw new BusinessException("VALIDATION_ERROR", "NONE authentication cannot reference a credential");
                }
                retireCredential(tenantId, connection);
                connection.setCredentialId(null);
            } else if (replacingCredential) {
                validateCredentialValues(authType, request.credential());
                retireCredential(tenantId, connection);
                connection.setCredentialId(storeCredential(tenantId, actorId, authType, request.credential()));
            } else if (!authType.equals(normalizeAuthType(connection.getAuthType())) && connection.getCredentialId() == null) {
                throw new BusinessException("VALIDATION_ERROR", "Selected authentication requires a credential");
            }
            connection.setAuthType(authType);
        } else if (request.credential() != null) {
            String currentAuthType = normalizeAuthType(connection.getAuthType());
            if ("NONE".equals(currentAuthType)) {
                throw new BusinessException("VALIDATION_ERROR", "NONE authentication cannot reference a credential");
            }
            validateCredentialValues(currentAuthType, request.credential());
            retireCredential(tenantId, connection);
            connection.setCredentialId(storeCredential(tenantId, actorId, currentAuthType, request.credential()));
        }
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional
    public void delete(UUID tenantId, UUID actorId, UUID id) {
        OutboundHttpConnection connection = load(tenantId, id);
        retireCredential(tenantId, connection);
        connection.softDelete(actorId);
        connection.setActive(false);
        connectionRepository.save(connection);
    }

    /**
     * Executes a single GET through the existing outbound transport with the
     * connection's credentials resolved server-side. The response body is
     * deliberately not returned — only controlled status metadata.
     */
    @Transactional(readOnly = true)
    public ConnectionTestResponse test(UUID tenantId, UUID actorId, UUID id, ConnectionTestRequest request) {
        OutboundHttpConnection connection = load(tenantId, id);
        if (!Boolean.TRUE.equals(connection.getActive())) {
            throw new BusinessException("CONNECTION_INACTIVE", "Connection is inactive and cannot be tested");
        }
        if (request.url() == null || request.url().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "A test URL is required");
        }
        OutboundHttpRequest outbound = new OutboundHttpRequest(
            tenantId, actorId, null, null,
            OutboundHttpMethod.GET,
            request.url().trim(),
            Map.of(), Map.of(), null,
            connection.getId()
        );
        OutboundHttpResult result = httpApiService.execute(tenantId, actorId, null, null, outbound);
        String message = result.success()
            ? "Connection test succeeded"
            : (result.errorMessage() == null ? "Connection test failed" : result.errorMessage());
        log.info("Outbound connection test: tenant={} connection={} success={} statusCode={}",
            tenantId, id, result.success(), result.statusCode());
        return new ConnectionTestResponse(result.success(), result.statusCode(), message, result.correlationId());
    }

    private UUID storeCredential(UUID tenantId, UUID actorId, String authType, Map<String, Object> values) {
        try {
            String encrypted = encryptionService.encrypt(objectMapper.writeValueAsString(values));
            OutboundHttpConnectionCredential credential = OutboundHttpConnectionCredential.builder()
                .tenantId(tenantId)
                .authType(authType)
                .encryptedValue(encrypted)
                .isActive(true)
                .build();
            return credentialRepository.save(credential).getId();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to store outbound connection credential for tenant {}", tenantId);
            throw new BusinessException("CREDENTIAL_STORE_FAILED", "Credential could not be stored securely");
        }
    }

    private void retireCredential(UUID tenantId, OutboundHttpConnection connection) {
        if (connection.getCredentialId() == null) return;
        credentialRepository.findByIdAndTenantIdAndIsActiveTrueAndDeletedFalse(connection.getCredentialId(), tenantId)
            .ifPresent(credential -> {
                credential.softDelete(null);
                credential.setIsActive(false);
                credentialRepository.save(credential);
            });
        connection.setCredentialId(null);
    }

    private void validateCredentialPairing(String authType, Map<String, Object> credential) {
        if ("NONE".equals(authType)) {
            if (credential != null && !credential.isEmpty()) {
                throw new BusinessException("VALIDATION_ERROR", "NONE authentication cannot include a credential");
            }
            return;
        }
        validateCredentialValues(authType, credential);
    }

    private void validateCredentialValues(String authType, Map<String, Object> credential) {
        if (credential == null || credential.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "Selected authentication requires credential values");
        }
        Set<String> required = switch (authType) {
            case "API_KEY" -> Set.of("apiKey");
            case "BEARER" -> Set.of("token");
            case "BASIC_AUTH" -> Set.of("username", "password");
            default -> throw new BusinessException("VALIDATION_ERROR", "Unsupported authentication type");
        };
        for (String key : required) {
            Object value = credential.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new BusinessException("VALIDATION_ERROR", "Missing credential value: " + key);
            }
        }
    }

    private String normalizeAuthType(String raw) {
        if (raw == null || raw.isBlank()) throw new BusinessException("VALIDATION_ERROR", "Authentication type is required");
        String authType = raw.trim().toUpperCase();
        if (!SUPPORTED_AUTH_TYPES.contains(authType)) {
            throw new BusinessException("VALIDATION_ERROR", "Unsupported authentication type. Supported: NONE, API_KEY, BEARER, BASIC_AUTH");
        }
        return authType;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) throw new BusinessException("VALIDATION_ERROR", "Connection name is required");
        return name.trim();
    }

    private OutboundHttpConnection load(UUID tenantId, UUID id) {
        return connectionRepository.findById(id)
            .filter(connection -> tenantId.equals(connection.getTenantId()) && !connection.isDeleted())
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Connection not found"));
    }

    private static HttpConnectionResponse toResponse(OutboundHttpConnection connection) {
        return new HttpConnectionResponse(
            connection.getId(),
            connection.getName(),
            connection.getAuthType(),
            Boolean.TRUE.equals(connection.getActive()),
            connection.getCredentialId() != null,
            connection.getCreatedAt(),
            connection.getUpdatedAt()
        );
    }
}
