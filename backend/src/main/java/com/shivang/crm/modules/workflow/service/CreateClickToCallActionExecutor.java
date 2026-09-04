package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.call.dto.ClickToCallRequest;
import com.shivang.crm.modules.call.dto.ClickToCallResponse;
import com.shivang.crm.modules.integration.service.ConnectorInstanceService;
import com.shivang.crm.modules.integration.service.ProviderRegistryService;

@Component
public class CreateClickToCallActionExecutor implements WorkflowActionExecutor {

    private final WorkflowClickToCallService clickToCallService;
    private final WorkflowValueResolver valueResolver;
    private final WorkflowExecutionIdentityResolver executionIdentityResolver;
    private final ProviderRegistryService providerRegistryService;
    private final ConnectorInstanceService connectorInstanceService;

    public CreateClickToCallActionExecutor(WorkflowClickToCallService clickToCallService, WorkflowValueResolver valueResolver, WorkflowExecutionIdentityResolver executionIdentityResolver, ProviderRegistryService providerRegistryService, ConnectorInstanceService connectorInstanceService) {
        this.clickToCallService = clickToCallService;
        this.valueResolver = valueResolver;
        this.executionIdentityResolver = executionIdentityResolver;
        this.providerRegistryService = providerRegistryService;
        this.connectorInstanceService = connectorInstanceService;
    }

    @Override
    public String actionType() {
        return "CLICK_TO_CALL";
    }

    @Override
    public WorkflowActionExecutionResult execute(WorkflowExecutionContext context, Map<String, Object> configuration) {
        if (configuration == null) {
            throw failure("WORKFLOW_CLICK_TO_CALL_INVALID_CONFIG", "Click-to-call configuration is required");
        }

        String phoneNumber = textOrNull(resolve(configuration.get("phoneNumber"), context));
        String entityType = textOrNull(resolve(configuration.get("entityType"), context));
        UUID entityId = uuidOrNull(resolve(configuration.get("entityId"), context));
        String subject = textOrNull(resolve(configuration.get("subject"), context));
        // provider/instance must be explicit — no fallback (FE/BE-WF-28/32)
        Object rawInstance = configuration.get("connectorInstanceId");
        if (rawInstance == null) rawInstance = configuration.get("providerInstanceId");
        UUID connectorInstanceId = uuidOrNull(resolve(rawInstance, context));
        Object rawProvider = configuration.get("providerKey");
        if (rawProvider == null) rawProvider = configuration.get("provider");
        String providerKey = textOrNull(resolve(rawProvider, context));
        if (connectorInstanceId == null && (providerKey == null || providerKey.isBlank())) {
            throw failure("WORKFLOW_CLICK_TO_CALL_PROVIDER_REQUIRED", "Click to Call requires a configured calling provider");
        }
        if (providerKey != null) providerKey = providerKey.trim();

        boolean directPhone = phoneNumber != null && !phoneNumber.isBlank();
        boolean entityCall = entityType != null && !entityType.isBlank() && entityId != null;
        if (!directPhone && !entityCall) {
            throw failure("WORKFLOW_CLICK_TO_CALL_PHONE_REQUIRED", "Provide phoneNumber or entityType and entityId");
        }
        if ((entityType != null && !entityType.isBlank()) != (entityId != null)) {
            throw failure("WORKFLOW_CLICK_TO_CALL_INVALID_CONFIG", "entityType and entityId must be provided together");
        }

        // ── Tenant-scoped provider/instance validation ──
        UUID tenantId = context.getIdentity().tenantId();
        String effectiveProviderKey = providerKey;
        UUID effectiveInstanceId = connectorInstanceId;
        if (effectiveInstanceId != null) {
            var instOpt = connectorInstanceService.findById(tenantId, effectiveInstanceId);
            if (instOpt.isEmpty()) throw failure("CONNECTOR_NOT_FOUND", "Calling connection not found");
            var inst = instOpt.get();
            if (!Boolean.TRUE.equals(inst.getIsActive())) throw failure("CONNECTOR_INACTIVE", "Calling connection is inactive");
            if (inst.getProvider() == null || inst.getProvider().getProviderKey() == null) throw failure("PROVIDER_NOT_FOUND", "Provider not found for connection");
            effectiveProviderKey = inst.getProvider().getProviderKey();
            if (providerKey != null && !providerKey.isBlank() && !effectiveProviderKey.equals(providerKey)) {
                throw failure("CONNECTOR_PROVIDER_MISMATCH", "Selected connection does not belong to the specified provider");
            }
        }
        var providerOpt = providerRegistryService.findByProviderKey(effectiveProviderKey);
        if (providerOpt.isEmpty()) {
            throw failure("PROVIDER_NOT_FOUND", "Calling provider not found: " + effectiveProviderKey);
        }
        var provider = providerOpt.get();
        try {
            providerRegistryService.validateProviderActive(provider);
        } catch (RuntimeException ex) {
            throw failure("PROVIDER_INACTIVE", "Calling provider is inactive: " + effectiveProviderKey);
        }
        var actionOpt = providerRegistryService.findActionByProviderKeyAndActionKey(effectiveProviderKey, "CLICK_TO_CALL");
        if (actionOpt.isEmpty()) {
            throw failure("PROVIDER_DOES_NOT_SUPPORT_CLICK_TO_CALL", "Provider does not support Click-to-Call: " + effectiveProviderKey);
        }
        try {
            providerRegistryService.validateActionActive(actionOpt.get());
        } catch (RuntimeException ex) {
            throw failure("ACTION_INACTIVE", "Click-to-Call action is inactive for provider: " + effectiveProviderKey);
        }
        if (effectiveInstanceId == null) {
            var instanceOpt = connectorInstanceService.findActiveByTenantAndProvider(tenantId, effectiveProviderKey);
            if (instanceOpt.isEmpty()) {
                throw failure("CONNECTOR_NOT_FOUND", "Calling provider not configured for this tenant: " + effectiveProviderKey);
            }
            effectiveInstanceId = instanceOpt.get().getId();
        }

        ClickToCallRequest request = ClickToCallRequest.builder()
            .phoneNumber(phoneNumber)
            .entityType(entityType)
            .entityId(entityId)
            .subject(subject)
            .providerKey(effectiveProviderKey)
            .connectorInstanceId(effectiveInstanceId)
            .build();

        UUID executionUserId;
        try {
            executionUserId = executionIdentityResolver.resolveExecutionUser(context, configuration);
        } catch (WorkflowRuntimeException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new WorkflowRuntimeException("EXECUTION_USER_RESOLUTION_FAILED", "Failed to resolve execution user: " + ex.getMessage());
        }

        try {
            ClickToCallResponse response = clickToCallService.execute(
                context.getIdentity().tenantId(),
                executionUserId,
                request
            );
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("success", true);
            output.put("callId", response.getCallId() == null ? null : response.getCallId().toString());
            output.put("status", response.getStatus());
            output.put("message", response.getMessage());
            return WorkflowActionExecutionResult.completed(output);
        } catch (RuntimeException ex) {
            throw failure("WORKFLOW_CLICK_TO_CALL_EXECUTION_FAILED", safeMessage(ex));
        }
    }

    private Object resolve(Object value, WorkflowExecutionContext context) {
        if (value instanceof String text && text.startsWith("{{") && text.endsWith("}}")) {
            WorkflowResolvedValue resolved = valueResolver.resolve(context, text.substring(2, text.length() - 2).trim());
            if (!resolved.found()) {
                throw failure("WORKFLOW_CLICK_TO_CALL_VALUE_RESOLUTION_FAILED", "Click-to-call value could not be resolved");
            }
            return resolved.value();
        }
        return value;
    }

    private String textOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private UUID uuidOrNull(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return UUID.fromString(String.valueOf(value)); }
        catch (IllegalArgumentException ex) { throw failure("WORKFLOW_CLICK_TO_CALL_INVALID_CONFIG", "entityId must be a valid UUID"); }
    }

    private WorkflowRuntimeException failure(String code, String message) {
        return new WorkflowRuntimeException(code, message);
    }

    private String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? "Click-to-call failed" : ex.getMessage();
    }
}