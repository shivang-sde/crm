package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class UpdateEntityFieldActionExecutor implements WorkflowActionExecutor {

    private final WorkflowEntityUpdateService entityUpdateService;
    private final WorkflowValueResolver valueResolver;

    public UpdateEntityFieldActionExecutor(WorkflowEntityUpdateService entityUpdateService, WorkflowValueResolver valueResolver) {
        this.entityUpdateService = entityUpdateService;
        this.valueResolver = valueResolver;
    }

    @Override
    public String actionType() {
        return "UPDATE_ENTITY_FIELD";
    }

    @Override
    public WorkflowActionExecutionResult execute(WorkflowExecutionContext context, Map<String, Object> configuration) {
        if (configuration == null) {
            throw new WorkflowRuntimeException("WORKFLOW_UPDATE_INVALID_CONFIG", "Update configuration is required");
        }
        String entityType = requiredText(configuration.get("entityType"), "entityType");
        UUID entityId = resolveUuid(configuration.get("entityId"), context, "entityId");
        String field = requiredText(configuration.get("field"), "field");
        if (!configuration.containsKey("value")) {
            throw new WorkflowRuntimeException("WORKFLOW_UPDATE_INVALID_CONFIG", "value is required");
        }
        Object value = resolveValue(configuration.get("value"), context);

        try {
            WorkflowEntityUpdateResult result = entityUpdateService.update(
                context.getIdentity().tenantId(),
                context.getIdentity().actorId(),
                entityType,
                entityId,
                field,
                value,
                currentCustomFields(context)
            );
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("success", true);
            output.put("entityType", result.entityType());
            output.put("entityId", result.entityId().toString());
            output.put("field", result.field());
            output.put("value", value);
            return WorkflowActionExecutionResult.completed(output);
        } catch (WorkflowEntityUpdateException ex) {
            throw new WorkflowRuntimeException(ex.getErrorCode(), ex.getMessage());
        } catch (Exception ex) {
            throw new WorkflowRuntimeException("WORKFLOW_UPDATE_EXECUTION_FAILED", "Entity update failed");
        }
    }

    private Object resolveValue(Object value, WorkflowExecutionContext context) {
        if (value instanceof String text && text.startsWith("{{") && text.endsWith("}}")) {
            WorkflowResolvedValue resolved = valueResolver.resolve(context, text.substring(2, text.length() - 2).trim());
            if (!resolved.found()) {
                throw new WorkflowRuntimeException("WORKFLOW_UPDATE_VALUE_RESOLUTION_FAILED", "Update value was not found");
            }
            return resolved.value();
        }
        return value;
    }

    private UUID resolveUuid(Object value, WorkflowExecutionContext context, String field) {
        Object resolved = resolveValue(value, context);
        try { return UUID.fromString(String.valueOf(resolved)); }
        catch (Exception ex) { throw new WorkflowRuntimeException("WORKFLOW_UPDATE_INVALID_CONFIG", "Invalid " + field); }
    }

    private String requiredText(Object value, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new WorkflowRuntimeException("WORKFLOW_UPDATE_INVALID_CONFIG", field + " is required");
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> currentCustomFields(WorkflowExecutionContext context) {
        Object fields = context.getEntity().get("customFields");
        return fields instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

}