package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.call.dto.ClickToCallRequest;
import com.shivang.crm.modules.call.dto.ClickToCallResponse;

@Component
public class CreateClickToCallActionExecutor implements WorkflowActionExecutor {

    private final WorkflowClickToCallService clickToCallService;
    private final WorkflowValueResolver valueResolver;

    public CreateClickToCallActionExecutor(WorkflowClickToCallService clickToCallService, WorkflowValueResolver valueResolver) {
        this.clickToCallService = clickToCallService;
        this.valueResolver = valueResolver;
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

        boolean directPhone = phoneNumber != null && !phoneNumber.isBlank();
        boolean entityCall = entityType != null && !entityType.isBlank() && entityId != null;
        if (!directPhone && !entityCall) {
            throw failure("WORKFLOW_CLICK_TO_CALL_PHONE_REQUIRED", "Provide phoneNumber or entityType and entityId");
        }
        if ((entityType != null && !entityType.isBlank()) != (entityId != null)) {
            throw failure("WORKFLOW_CLICK_TO_CALL_INVALID_CONFIG", "entityType and entityId must be provided together");
        }

        ClickToCallRequest request = ClickToCallRequest.builder()
            .phoneNumber(phoneNumber)
            .entityType(entityType)
            .entityId(entityId)
            .subject(subject)
            .build();

        try {
            ClickToCallResponse response = clickToCallService.execute(
                context.getIdentity().tenantId(),
                context.getIdentity().actorId(),
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