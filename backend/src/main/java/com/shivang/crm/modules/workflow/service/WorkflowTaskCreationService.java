package com.shivang.crm.modules.workflow.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.task.dto.TaskCreateRequest;
import com.shivang.crm.modules.task.dto.TaskResponse;
import com.shivang.crm.modules.task.service.TaskService;
import com.shivang.crm.shared.service.EntityResolverService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowTaskCreationService {

    private final TaskService taskService;
    private final EntityResolverService entityResolverService;
    private final UserRepository userRepository;

    public TaskResponse create(UUID tenantId, UUID actorId, TaskCreateRequest request) {
        if (request == null || request.getSubject() == null || request.getSubject().isBlank()) {
            throw new WorkflowTaskCreationException("WORKFLOW_CREATE_TASK_SUBJECT_REQUIRED", "Task subject is required");
        }

        if (request.getEntityType() != null || request.getEntityId() != null) {
            if (request.getEntityType() == null || request.getEntityId() == null) {
                throw new WorkflowTaskCreationException("WORKFLOW_CREATE_TASK_INVALID_CONFIG", "Task entityType and entityId must be provided together");
            }
            try {
                entityResolverService.validateEntityExists(request.getEntityType(), request.getEntityId(), tenantId);
            } catch (RuntimeException ex) {
                throw new WorkflowTaskCreationException("WORKFLOW_CREATE_TASK_ENTITY_NOT_FOUND", "Linked task entity was not found");
            }
        }

        if (request.getOwnerUserId() != null) {
            User owner = userRepository.findByIdAndTenantIdAndDeletedFalse(request.getOwnerUserId(), tenantId)
                .orElseThrow(() -> new WorkflowTaskCreationException("WORKFLOW_CREATE_TASK_OWNER_NOT_FOUND", "Task owner was not found"));
            if (!Boolean.TRUE.equals(owner.getIsActive())) {
                throw new WorkflowTaskCreationException("WORKFLOW_CREATE_TASK_OWNER_INACTIVE", "Task owner is inactive");
            }
        }

        try {
            return taskService.createTaskInternal(tenantId, actorId, request);
        } catch (WorkflowTaskCreationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new WorkflowTaskCreationException("WORKFLOW_CREATE_TASK_EXECUTION_FAILED", "Task creation failed");
        }
    }
}