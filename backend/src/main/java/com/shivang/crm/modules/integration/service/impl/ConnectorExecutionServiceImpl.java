package com.shivang.crm.modules.integration.service.impl;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.integration.dto.ConnectorExecutionContext;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionRequest;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionResult;
import com.shivang.crm.modules.integration.entity.ConnectorCredential;
import com.shivang.crm.modules.integration.entity.ConnectorExecution;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ProviderActionDefinition;
import com.shivang.crm.modules.integration.entity.ProviderDefinition;
import com.shivang.crm.modules.integration.repository.ConnectorExecutionRepository;
import com.shivang.crm.modules.integration.service.ConnectorAuditSanitizer;
import com.shivang.crm.modules.integration.service.ConnectorCredentialService;
import com.shivang.crm.modules.integration.service.ConnectorExecutionService;
import com.shivang.crm.modules.integration.service.ConnectorInstanceService;
import com.shivang.crm.modules.integration.service.ProviderActionExecutor;
import com.shivang.crm.modules.integration.service.ProviderRegistryService;
import com.shivang.crm.modules.integration.service.TemplateResolver;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConnectorExecutionServiceImpl implements ConnectorExecutionService {

    private final ConnectorExecutionRepository executionRepository;
    private final ProviderRegistryService providerRegistryService;
    private final ConnectorInstanceService connectorInstanceService;
    private final ConnectorCredentialService credentialService;
    private final TemplateResolver templateResolver;
    private final ProviderActionExecutor providerActionExecutor;
    private final ConnectorAuditSanitizer sanitizer;

    @Override
    @Transactional
    public ConnectorExecutionResult execute(ConnectorExecutionRequest request) {
        validateRequest(request);

        ProviderDefinition provider = providerRegistryService.findByProviderKey(request.getProviderKey())
            .orElseThrow(() -> new BusinessException("PROVIDER_NOT_FOUND", "Provider not found: " + request.getProviderKey()));
        providerRegistryService.validateProviderActive(provider);

        ProviderActionDefinition action = providerRegistryService.findActionByProviderKeyAndActionKey(request.getProviderKey(), request.getActionKey())
            .orElseThrow(() -> new BusinessException("ACTION_NOT_FOUND", "Action not found: " + request.getActionKey()));
        providerRegistryService.validateActionActive(action);

        ConnectorInstance connectorInstance = connectorInstanceService.findActiveByTenantAndProvider(request.getTenantId(), request.getProviderKey())
            .orElseThrow(() -> new BusinessException("CONNECTOR_NOT_FOUND", "Active connector instance not found for tenant and provider"));

        Map<String, Object> credentials = resolveCredentials(request, connectorInstance);

        ConnectorExecutionContext context = buildContext(request, connectorInstance, credentials);
        ConnectorExecutionResult executionResult = providerActionExecutor.execute(context, action, connectorInstance, credentials);

        ConnectorExecution execution = new ConnectorExecution();
        execution.setTenantId(request.getTenantId());
        execution.setConnectorInstance(connectorInstance);
        execution.setActionKey(request.getActionKey());
        execution.setExecutionStatus(executionResult.isSuccess() ? "SUCCEEDED" : "FAILED");
        execution.setCreatedBy(request.getUserId());
        execution.setUpdatedBy(request.getUserId());
        execution.setStartedAt(Instant.now());
        execution.setCompletedAt(Instant.now());
        execution.setRequestPayload(buildRequestPayload(request, executionResult));
        execution.setResponsePayload(buildResponsePayload(executionResult));
        execution.setErrorMessage(executionResult.getErrorMessage());

        ConnectorExecution savedExecution = executionRepository.save(execution);
        executionResult.setExecutionId(savedExecution.getId());
        return executionResult;
    }

    @Override
    public ConnectorExecution save(ConnectorExecution connectorExecution) {
        return executionRepository.save(connectorExecution);
    }

    @Override
    public java.util.Optional<ConnectorExecution> findById(UUID id) {
        return executionRepository.findById(id);
    }

    @Override
    public java.util.List<ConnectorExecution> findByTenantId(UUID tenantId) {
        return executionRepository.findByTenantId(tenantId);
    }

    private void validateRequest(ConnectorExecutionRequest request) {
        if (request == null) {
            throw new BusinessException("INVALID_REQUEST", "Execution request is required");
        }
        if (request.getTenantId() == null) {
            throw new BusinessException("INVALID_REQUEST", "tenantId is required");
        }
        if (request.getProviderKey() == null || request.getProviderKey().isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "providerKey is required");
        }
        if (request.getActionKey() == null || request.getActionKey().isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "actionKey is required");
        }
    }

    private Map<String, Object> resolveCredentials(
        ConnectorExecutionRequest request,
        ConnectorInstance connectorInstance) {

    UUID tenantId = request.getTenantId();
    UUID userId = request.getUserId();
    UUID connectorInstanceId = connectorInstance.getId();

    if (userId != null) {
        List<ConnectorCredential> userCredentials =
                credentialService
                        .findActiveUserCredentials(
                                tenantId,
                                connectorInstanceId,
                                userId
                        );

        if (!userCredentials.isEmpty()) {
            return decryptCredentialValue(
                    userCredentials.getFirst()
            );
        }
    }

    List<ConnectorCredential> tenantCredentials =
            credentialService
                    .findActiveTenantCredentials(
                            tenantId,
                            connectorInstanceId
                    );

    if (!tenantCredentials.isEmpty()) {
        return decryptCredentialValue(
                tenantCredentials.getFirst()
        );
    }

    throw new BusinessException(
            "NO_CREDENTIALS",
            userId == null
                    ? "No active tenant credential was found"
                    : "No active user or tenant credential was found"
    );
}

    private Map<String, Object> decryptCredentialValue(ConnectorCredential cred) {
        Map<String, Object> credentials = new LinkedHashMap<>();
        String decrypted = credentialService.decryptValue(cred);
        if (decrypted != null && !decrypted.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(decrypted, Map.class);
                credentials.putAll(parsed);
            } catch (Exception e) {
                // Fallback
            }
        }
        return credentials;
    }

    private ConnectorExecutionContext buildContext(ConnectorExecutionRequest request, ConnectorInstance connectorInstance, Map<String, Object> credentials) {
        ConnectorExecutionContext context = new ConnectorExecutionContext();
        context.setTenantId(request.getTenantId());
        context.setUserId(request.getUserId());
        context.setProviderKey(request.getProviderKey());
        context.setActionKey(request.getActionKey());
        context.setConnectorInstanceId(connectorInstance.getId());
        context.setEntityType(request.getEntityType());
        context.setEntityId(request.getEntityId());
        context.setEntity(request.getEntityData() == null ? Map.of() : request.getEntityData());
        context.setInput(request.getInputData() == null ? Map.of() : request.getInputData());
        context.setTenant(Map.of("id", request.getTenantId()));
        context.setUser(Map.of("id", request.getUserId()));
        context.setCredential(credentials);
        context.setRequestMetadata(Map.of("tenantId", request.getTenantId(), "userId", request.getUserId()));
        return context;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestPayload(ConnectorExecutionRequest request, ConnectorExecutionResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerKey", request.getProviderKey());
        payload.put("actionKey", request.getActionKey());
        payload.put("headers", result.getRequestHeaders());
        payload.put("body", result.getRequestBody());
        return (Map<String, Object>) sanitizer.sanitize(payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildResponsePayload(ConnectorExecutionResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("statusCode", result.getStatusCode());
        payload.put("body", result.getResponseBody());
        payload.put("executionTimeMs", result.getExecutionTimeMs());
        return (Map<String, Object>) sanitizer.sanitize(payload);
    }
}
