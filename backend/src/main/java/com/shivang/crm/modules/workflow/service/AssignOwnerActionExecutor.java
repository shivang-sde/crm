package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class AssignOwnerActionExecutor implements WorkflowActionExecutor {

    private final WorkflowOwnerAssignmentService ownerAssignmentService;
    private final WorkflowValueResolver valueResolver;

    public AssignOwnerActionExecutor(WorkflowOwnerAssignmentService ownerAssignmentService, WorkflowValueResolver valueResolver) {
        this.ownerAssignmentService = ownerAssignmentService;
        this.valueResolver = valueResolver;
    }

    @Override
    public String actionType() {
        return "ASSIGN_OWNER";
    }

    @Override
    public WorkflowActionExecutionResult execute(WorkflowExecutionContext context, Map<String, Object> configuration) {
        if (configuration == null) {
            throw failure("WORKFLOW_ASSIGN_OWNER_INVALID_CONFIG", "Assignment configuration is required");
        }
        String entityType = required(configuration.get("entityType"), "entityType");
        UUID entityId = resolveUuid(configuration.get("entityId"), context, "entityId");
        UUID ownerId = resolveUuid(configuration.get("ownerId"), context, "ownerId");

        try {
            WorkflowOwnerAssignmentResult result = ownerAssignmentService.assign(
                context.getIdentity().tenantId(),
                context.getIdentity().actorId(),
                entityType,
                entityId,
                ownerId
            );
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("success", true);
            output.put("entityType", result.entityType());
            output.put("entityId", result.entityId().toString());
            output.put("ownerId", result.ownerId().toString());
            output.put("actorId", context.getIdentity().actorId().toString());
            if (entityType.equalsIgnoreCase(context.getExecution().getEntityType())
                && entityId.equals(context.getExecution().getEntityId())) {
                // Keep later CONDITION/BRANCH nodes consistent with this mutation.
                context.refreshEntity();
            }
            return WorkflowActionExecutionResult.completed(output);
        } catch (WorkflowOwnerAssignmentException ex) {
            throw failure(ex.getErrorCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            throw failure("WORKFLOW_ASSIGN_OWNER_EXECUTION_FAILED", "Owner assignment failed");
        }
    }

    private UUID resolveUuid(Object value, WorkflowExecutionContext context, String field) {
        if (value == null) throw failure("WORKFLOW_ASSIGN_OWNER_INVALID_CONFIG", field + " is required");
        Object resolved = value;
        if (value instanceof String text && text.startsWith("{{") && text.endsWith("}}")) {
            WorkflowResolvedValue result = valueResolver.resolve(context, text.substring(2, text.length() - 2).trim());
            if (!result.found()) throw failure("WORKFLOW_ASSIGN_OWNER_INVALID_CONFIG", field + " could not be resolved");
            resolved = result.value();
        }
        try { return UUID.fromString(String.valueOf(resolved)); }
        catch (Exception ex) { throw failure("WORKFLOW_ASSIGN_OWNER_INVALID_CONFIG", "Invalid " + field); }
    }

    private String required(Object value, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw failure("WORKFLOW_ASSIGN_OWNER_INVALID_CONFIG", field + " is required");
        }
        return String.valueOf(value);
    }

    private WorkflowRuntimeException failure(String code, String message) {
        return new WorkflowRuntimeException(code, message);
    }
}