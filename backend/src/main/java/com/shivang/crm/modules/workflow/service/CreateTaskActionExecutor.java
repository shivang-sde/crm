package com.shivang.crm.modules.workflow.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.task.dto.TaskCreateRequest;
import com.shivang.crm.modules.task.dto.TaskResponse;
import com.shivang.crm.modules.task.entity.TaskPriority;
import com.shivang.crm.modules.task.entity.TaskStatus;
import com.shivang.crm.shared.model.Recurrence;

@Component
public class CreateTaskActionExecutor implements WorkflowActionExecutor {

    private final WorkflowTaskCreationService taskCreationService;
    private final WorkflowValueResolver valueResolver;
    private final ObjectMapper objectMapper;

    public CreateTaskActionExecutor(
        WorkflowTaskCreationService taskCreationService,
        WorkflowValueResolver valueResolver,
        ObjectMapper objectMapper
    ) {
        this.taskCreationService = taskCreationService;
        this.valueResolver = valueResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public String actionType() {
        return "CREATE_TASK";
    }

    @Override
    public WorkflowActionExecutionResult execute(WorkflowExecutionContext context, Map<String, Object> configuration) {
        if (configuration == null) {
            throw failure("WORKFLOW_CREATE_TASK_INVALID_CONFIG", "Task configuration is required");
        }

        Map<String, Object> resolved = resolveMap(configuration, context);
        String subject = text(resolved.get("subject"), "WORKFLOW_CREATE_TASK_SUBJECT_REQUIRED", "Task subject is required");
        TaskCreateRequest.TaskCreateRequestBuilder request = TaskCreateRequest.builder()
            .subject(subject)
            .description(textOrNull(resolved.get("description")))
            .dueDate(instant(resolved.get("dueDate"), "WORKFLOW_CREATE_TASK_INVALID_DATE"))
            .remindAt(instant(resolved.get("remindAt"), "WORKFLOW_CREATE_TASK_INVALID_DATE"))
            .status(status(resolved.get("status")))
            .priority(priority(resolved.get("priority")))
            .entityType(textOrNull(resolved.get("entityType")))
            .entityId(uuid(resolved.get("entityId"), "WORKFLOW_CREATE_TASK_INVALID_CONFIG"))
            .ownerUserId(uuid(resolved.get("ownerId"), "WORKFLOW_CREATE_TASK_INVALID_OWNER"))
            .customData(map(resolved.get("customData")));

        Object recurrence = resolved.get("recurrence");
        if (recurrence != null) {
            try {
                request.recurrence(objectMapper.convertValue(recurrence, Recurrence.class));
            } catch (IllegalArgumentException ex) {
                throw failure("WORKFLOW_CREATE_TASK_VALIDATION_FAILED", "Invalid recurrence configuration");
            }
        }

        try {
            TaskResponse task = taskCreationService.create(context.getIdentity().tenantId(), context.getIdentity().actorId(), request.build());
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("success", true);
            output.put("taskId", task.getId().toString());
            output.put("subject", task.getSubject());
            output.put("entityType", task.getEntityType());
            output.put("entityId", task.getEntityId());
            output.put("ownerId", task.getOwnerUserId());
            output.put("createdBy", context.getIdentity().actorId().toString());
            return WorkflowActionExecutionResult.completed(output);
        } catch (WorkflowTaskCreationException ex) {
            throw failure(ex.getErrorCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            throw failure("WORKFLOW_CREATE_TASK_EXECUTION_FAILED", "Task creation failed");
        }
    }

    private Map<String, Object> resolveMap(Map<String, Object> configuration, WorkflowExecutionContext context) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        configuration.forEach((key, value) -> resolved.put(key, resolveValue(value, context)));
        return resolved;
    }

    private Object resolveValue(Object value, WorkflowExecutionContext context) {
        if (value instanceof String text && text.startsWith("{{") && text.endsWith("}}")) {
            WorkflowResolvedValue resolved = valueResolver.resolve(context, text.substring(2, text.length() - 2).trim());
            if (!resolved.found()) throw failure("WORKFLOW_CREATE_TASK_VALUE_RESOLUTION_FAILED", "Task value could not be resolved");
            return resolved.value();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, nested) -> resolved.put(String.valueOf(key), resolveValue(nested, context)));
            return resolved;
        }
        if (value instanceof java.util.List<?> list) {
            return list.stream().map(item -> resolveValue(item, context)).toList();
        }
        return value;
    }

    private String text(Object value, String code, String message) {
        Object resolved = value;
        if (resolved == null || String.valueOf(resolved).isBlank()) throw failure(code, message);
        return String.valueOf(resolved);
    }

    private String textOrNull(Object value) { return value == null ? null : String.valueOf(value); }

    private UUID uuid(Object value, String code) {
        if (value == null) return null;
        try { return UUID.fromString(String.valueOf(value)); }
        catch (Exception ex) { throw failure(code, "Invalid UUID value"); }
    }

    private Instant instant(Object value, String code) {
        if (value == null) return null;
        try { return Instant.parse(String.valueOf(value)); }
        catch (Exception ex) { throw failure(code, "Invalid date/time value"); }
    }

    private TaskStatus status(Object value) {
        if (value == null) return null;
        try { return TaskStatus.valueOf(String.valueOf(value).trim().toUpperCase()); }
        catch (Exception ex) { throw failure("WORKFLOW_CREATE_TASK_INVALID_STATUS", "Invalid task status"); }
    }

    private TaskPriority priority(Object value) {
        if (value == null) return null;
        try { return TaskPriority.valueOf(String.valueOf(value).trim().toUpperCase()); }
        catch (Exception ex) { throw failure("WORKFLOW_CREATE_TASK_INVALID_PRIORITY", "Invalid task priority"); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value == null) return null;
        if (!(value instanceof Map<?, ?> map)) throw failure("WORKFLOW_CREATE_TASK_INVALID_CONFIG", "customData must be an object");
        return (Map<String, Object>) map;
    }

    private WorkflowRuntimeException failure(String code, String message) { return new WorkflowRuntimeException(code, message); }
}