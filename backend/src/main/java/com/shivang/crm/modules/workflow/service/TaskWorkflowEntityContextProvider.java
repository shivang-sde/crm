package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.task.entity.Task;
import com.shivang.crm.modules.task.repository.TaskRepository;

@Component
public class TaskWorkflowEntityContextProvider implements WorkflowEntityContextProvider {

    private final TaskRepository taskRepository;
    private final WorkflowRelatedRecordResolver relatedRecordResolver;

    public TaskWorkflowEntityContextProvider(TaskRepository taskRepository, WorkflowRelatedRecordResolver relatedRecordResolver) {
        this.taskRepository = taskRepository;
        this.relatedRecordResolver = relatedRecordResolver;
    }

    @Override
    public String entityType() {
        return "TASK";
    }

    @Override
    public Optional<Map<String, Object>> load(UUID tenantId, UUID entityId) {
        return taskRepository.findByIdAndTenantIdAndDeletedFalse(entityId, tenantId)
            .map(this::toContext);
    }

    private Map<String, Object> toContext(Task task) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("id", task.getId());
        context.put("tenantId", task.getTenantId());
        context.put("ownerId", task.getOwnerId());
        context.put("createdBy", task.getCreatedBy());
        context.put("subject", task.getSubject());
        context.put("description", task.getDescription());
        context.put("status", task.getStatus() == null ? null : task.getStatus().name());
        context.put("priority", task.getPriority() == null ? null : task.getPriority().name());
        context.put("entityType", task.getEntityType());
        context.put("entityId", task.getEntityId());
        context.put("dueDate", task.getDueDate());
        context.put("remindAt", task.getRemindAt());
        context.put("recurrence", task.getRecurrence());
        context.put("isClosed", task.getIsClosed());
        context.put("completedAt", task.getCompletedAt());
        context.put("createdAt", task.getCreatedAt());
        context.put("updatedAt", task.getUpdatedAt());
        context.put("customFields", task.getCustomData() == null ? Map.of() : task.getCustomData());
        // Controlled one-hop relationship: Task → linked CRM record (LEAD/CONTACT/ACCOUNT/DEAL).
        context.put("related", relatedRecordResolver
            .related(task.getEntityType(), task.getTenantId(), task.getEntityId())
            .orElse(null));
        return context;
    }
}
