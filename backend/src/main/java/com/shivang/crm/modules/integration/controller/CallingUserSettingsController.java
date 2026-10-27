package com.shivang.crm.modules.integration.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.integration.entity.ConnectorCredential;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ConnectorUserAgent;
import com.shivang.crm.modules.integration.service.ConnectorCredentialService;
import com.shivang.crm.modules.integration.service.ConnectorInstanceService;
import com.shivang.crm.modules.integration.service.impl.ConnectorUserAgentService;
import com.shivang.crm.shared.dto.ApiResponse;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class CallingUserSettingsController {

    private static final String USER_SCOPE = "USER";

    private final TenantContext tenantContext;
    private final ConnectorInstanceService connectorInstanceService;
    private final ConnectorCredentialService credentialService;
    private final ConnectorUserAgentService userAgentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record ConnectorResponse(
            UUID id,
            String providerKey,
            String providerName,
            String connectorName,
            String environment,
            boolean active,
            List<CredentialFieldResponse> credentialFields) {
    }

    public record CredentialFieldResponse(
            String key,
            String label,
            String type,
            boolean required) {
    }

    public record CredentialStatusResponse(
            UUID connectorInstanceId,
            boolean configured,
            String authType) {
    }

    /**
     * VOICE-02: short-lived Generic SIP connection material for browser
     * registration. Authenticated + tenant-scoped (see requireConnector),
     * resolves only the current user's credential (tenant fallback per
     * ConnectorCredentialService) under the allowlisted "primary" name.
     * The secret is returned ONLY here over HTTPS and MUST be treated as
     * ephemeral by the browser: never persisted, logged, or embedded in
     * events/state. Never log this response.
     */
    public record SipConnectionResponse(
            UUID connectorInstanceId,
            String providerKey,
            String endpoint,
            String sipDomain,
            String username,
            String password,
            Object iceServers) {
    }

    public record CredentialUpdateRequest(
            String authType,
            Map<String, Object> values) {
    }

    public record AgentMappingResponse(
            UUID id,
            UUID connectorInstanceId,
            String externalAgentId,
            String externalAgentNumber,
            boolean active) {
    }

    public record AgentMappingUpdateRequest(
            String externalAgentId,
            String externalAgentNumber,
            Boolean active) {
    }

    @GetMapping("/connectors")
    @PreAuthorize("@rbac.has(authentication, 'call', 'read')")
    public ResponseEntity<ApiResponse<List<ConnectorResponse>>> connectors() {
        UUID tenantId = requireTenantId();

        List<ConnectorResponse> response =
                connectorInstanceService
                        .findByTenantId(tenantId)
                        .stream()
                        .filter(instance ->
                                Boolean.TRUE.equals(
                                        instance.getIsActive()
                                ))
                        .filter(instance ->
                                instance.getProvider() != null
                                        && "CALLING".equalsIgnoreCase(
                                                instance.getProvider()
                                                        .getCategory()
                                        ))
                        .map(this::toConnectorResponse)
                        .toList();

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @GetMapping("/connectors/{connectorInstanceId}/credential-status")
    @PreAuthorize("@rbac.has(authentication, 'call', 'read')")
    public ResponseEntity<ApiResponse<CredentialStatusResponse>>
    credentialStatus(
            @PathVariable UUID connectorInstanceId) {

        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();

        requireConnector(
                tenantId,
                connectorInstanceId
        );

        List<ConnectorCredential> credentials =
                credentialService.findActiveUserCredentials(
                        tenantId,
                        connectorInstanceId,
                        userId
                );

        Optional<ConnectorCredential> credential =
                credentials.stream().findFirst();

        CredentialStatusResponse response =
                new CredentialStatusResponse(
                        connectorInstanceId,
                        credential.isPresent(),
                        credential
                                .map(ConnectorCredential::getAuthType)
                                .orElse("PROVIDER_SPECIFIC")
                );

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @GetMapping("/connectors/{connectorInstanceId}/sip-connection")
    @PreAuthorize("@rbac.has(authentication, 'call', 'read')")
    public ResponseEntity<ApiResponse<SipConnectionResponse>>
    sipConnection(
            @PathVariable UUID connectorInstanceId) {

        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();

        ConnectorInstance instance =
                requireConnector(
                        tenantId,
                        connectorInstanceId
                );

        Map<String, Object> config =
                instance.getConfig() == null
                        ? Map.of()
                        : instance.getConfig();

        Map<String, Object> credentialValues =
                credentialService
                        .resolveCredentialValue(
                                tenantId,
                                connectorInstanceId,
                                userId,
                                SIP_PRIMARY_CREDENTIAL_NAME
                        )
                        .map(this::parseCredentialJson)
                        .orElse(Map.of());

        String endpoint = text(config.get("endpoint"));
        String sipDomain = text(config.get("sipDomain"));
        String username = text(credentialValues.get("userId"));
        if (username == null) {
            username = text(config.get("username"));
        }
        String password = text(credentialValues.get("password"));
        if (password == null) {
            password = text(credentialValues.get("secret"));
        }

        if (endpoint == null || sipDomain == null
                || username == null || password == null) {
            throw new BusinessException(
                    "SIP_CONNECTION_NOT_CONFIGURED",
                    "SIP connection is not fully configured for this connector"
            );
        }

        return ResponseEntity.ok(
                ApiResponse.success(
                        new SipConnectionResponse(
                                connectorInstanceId,
                                instance.getProvider().getProviderKey(),
                                endpoint,
                                sipDomain,
                                username,
                                password,
                                config.get("iceServers")
                        )
                )
        );
    }

    private static final String SIP_PRIMARY_CREDENTIAL_NAME = "primary";

    private Map<String, Object> parseCredentialJson(String json) {
        try {
            Map<?, ?> parsed = objectMapper.readValue(json, Map.class);
            Map<String, Object> values = new HashMap<>();
            for (Map.Entry<?, ?> entry : parsed.entrySet()) {
                if (entry.getKey() != null) {
                    values.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return values;
        } catch (JacksonException exception) {
            throw new BusinessException(
                    "SIP_CONNECTION_NOT_CONFIGURED",
                    "SIP credential is not in the expected format"
            );
        }
    }

    private String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.trim();
    }
    public ResponseEntity<ApiResponse<CredentialStatusResponse>>
    saveCredentials(
            @PathVariable UUID connectorInstanceId,
            @RequestBody CredentialUpdateRequest request) {

        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();

        ConnectorInstance instance =
                requireConnector(
                        tenantId,
                        connectorInstanceId
                );

        if (request.values() == null
                || request.values().isEmpty()) {

            throw new BusinessException(
                    "INVALID_REQUEST",
                    "Credential values are required"
            );
        }

        deactivateCurrentUserCredentials(
                tenantId,
                connectorInstanceId,
                userId
        );

        ConnectorCredential credential =
                ConnectorCredential.builder()
                        .tenantId(tenantId)
                        .connectorInstance(instance)
                        .credentialName("primary")
                        .authType(
                                request.authType() == null
                                        || request.authType().isBlank()
                                            ? "PROVIDER_SPECIFIC"
                                            : request.authType()
                        )
                        .credentialScope(USER_SCOPE)
                        .ownerUserId(userId)
                        .encryptedValue(
                                serialize(request.values())
                        )
                        .metadata(Map.of(
                                "scope", USER_SCOPE,
                                "selfManaged", true
                        ))
                        .isActive(true)
                        .createdBy(userId)
                        .updatedBy(userId)
                        .build();

        credentialService.save(credential);

        return ResponseEntity.ok(
                ApiResponse.success(
                        new CredentialStatusResponse(
                                connectorInstanceId,
                                true,
                                credential.getAuthType()
                        )
                )
        );
    }

    @DeleteMapping("/connectors/{connectorInstanceId}/credentials")
    @PreAuthorize("@rbac.has(authentication, 'call', 'write')")
    public ResponseEntity<ApiResponse<Void>> deleteCredentials(
            @PathVariable UUID connectorInstanceId) {

        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();

        requireConnector(
                tenantId,
                connectorInstanceId
        );

        deactivateCurrentUserCredentials(
                tenantId,
                connectorInstanceId,
                userId
        );

        return ResponseEntity.ok(
                ApiResponse.success(null)
        );
    }

    @GetMapping("/connectors/{connectorInstanceId}/agent-mapping")
    @PreAuthorize("@rbac.has(authentication, 'call', 'read')")
    public ResponseEntity<ApiResponse<AgentMappingResponse>>
    getAgentMapping(
            @PathVariable UUID connectorInstanceId) {

        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();

        requireConnector(
                tenantId,
                connectorInstanceId
        );

        Optional<ConnectorUserAgent> mapping =
                userAgentService.findForUser(
                        tenantId,
                        connectorInstanceId,
                        userId
                );

        AgentMappingResponse response =
                mapping.map(this::toAgentMappingResponse)
                        .orElse(null);

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @PutMapping("/connectors/{connectorInstanceId}/agent-mapping")
    @PreAuthorize("@rbac.has(authentication, 'call', 'write')")
    public ResponseEntity<ApiResponse<AgentMappingResponse>>
    saveAgentMapping(
            @PathVariable UUID connectorInstanceId,
            @RequestBody AgentMappingUpdateRequest request) {

        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();

        requireConnector(
                tenantId,
                connectorInstanceId
        );

        Optional<ConnectorUserAgent> existing =
                userAgentService.findForUser(
                        tenantId,
                        connectorInstanceId,
                        userId
                );

        ConnectorUserAgent saved;

        if (existing.isPresent()) {
            saved = userAgentService.update(
                    tenantId,
                    userId,
                    existing.get().getId(),
                    userId,
                    request.externalAgentId(),
                    request.externalAgentNumber(),
                    request.active() == null
                            ? true
                            : request.active()
            );
        } else {
            saved = userAgentService.create(
                    tenantId,
                    userId,
                    connectorInstanceId,
                    userId,
                    request.externalAgentId(),
                    request.externalAgentNumber(),
                    request.active() == null
                            ? true
                            : request.active()
            );
        }

        return ResponseEntity.ok(
                ApiResponse.success(
                        toAgentMappingResponse(saved)
                )
        );
    }

    private void deactivateCurrentUserCredentials(
            UUID tenantId,
            UUID connectorInstanceId,
            UUID userId) {

        List<ConnectorCredential> existing =
                credentialService.findActiveUserCredentials(
                        tenantId,
                        connectorInstanceId,
                        userId
                );

        for (ConnectorCredential credential : existing) {
            /*
             * Use the repository-level deactivate method.
             * Do not call credentialService.save() here because the existing
             * encrypted value could be encrypted a second time.
             */
            credentialService.deactivate(
                    tenantId,
                    credential.getId(),
                    userId
            );
        }
    }

    private ConnectorInstance requireConnector(
            UUID tenantId,
            UUID connectorInstanceId) {

        ConnectorInstance instance =
                connectorInstanceService
                        .findById(
                                tenantId,
                                connectorInstanceId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        "NOT_FOUND",
                                        "Calling connector not found"
                                )
                        );

        if (!Boolean.TRUE.equals(instance.getIsActive())) {
            throw new BusinessException(
                    "CONNECTOR_INACTIVE",
                    "Calling connector is not active"
            );
        }

        if (instance.getProvider() == null
                || !"CALLING".equalsIgnoreCase(
                        instance.getProvider()
                                .getCategory()
                )) {

            throw new BusinessException(
                    "INVALID_CONNECTOR",
                    "Connector is not a calling provider"
            );
        }

        return instance;
    }

    private ConnectorResponse toConnectorResponse(
            ConnectorInstance instance) {

        String providerKey =
                instance.getProvider().getProviderKey();

        return new ConnectorResponse(
                instance.getId(),
                providerKey,
                instance.getProvider().getProviderName(),
                instance.getConnectorName(),
                instance.getEnvironment(),
                Boolean.TRUE.equals(instance.getIsActive()),
                credentialFields(providerKey)
        );
    }

    private List<CredentialFieldResponse> credentialFields(
            String providerKey) {
        // FE/BE-WF-28: provider-neutral — CALLING providers use userId/password (no hardcoded providerKey branching)
        return List.of(
                new CredentialFieldResponse(
                        "userId",
                        "User ID / Agent ID",
                        "text",
                        true
                ),
                new CredentialFieldResponse(
                        "password",
                        "Password",
                        "password",
                        true
                )
        );
    }

    private AgentMappingResponse toAgentMappingResponse(
            ConnectorUserAgent mapping) {

        return new AgentMappingResponse(
                mapping.getId(),
                mapping.getConnectorInstance()
                        .getId(),
                mapping.getExternalAgentId(),
                mapping.getExternalAgentNumber(),
                Boolean.TRUE.equals(
                        mapping.getIsActive()
                )
        );
    }

    private String serialize(
            Map<String, Object> values) {

        try {
            return objectMapper.writeValueAsString(
                    new HashMap<>(values)
            );
        } catch (JacksonException exception) {
            throw new BusinessException(
                    "INVALID_REQUEST",
                    "Unable to serialize credential values"
            );
        }
    }

    private UUID requireTenantId() {
        UUID tenantId =
                tenantContext.getTenantId();

        if (tenantId == null) {
            throw new BusinessException(
                    "TENANT_REQUIRED",
                    "Tenant context is required"
            );
        }

        return tenantId;
    }

    private UUID requireUserId() {
        UUID userId =
                tenantContext.getUserId();

        if (userId == null) {
            throw new BusinessException(
                    "USER_REQUIRED",
                    "User context is required"
            );
        }

        return userId;
    }
}