package com.shivang.crm.modules.task.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.recurrence.service.RecurrenceScheduleService;
import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.reminder.service.ReminderPlanningService;
import com.shivang.crm.modules.task.dto.TaskCreateRequest;
import com.shivang.crm.modules.task.dto.TaskResponse;
import com.shivang.crm.modules.task.dto.TaskUpdateRequest;
import com.shivang.crm.modules.task.entity.Task;
import com.shivang.crm.modules.task.entity.TaskPriority;
import com.shivang.crm.modules.task.entity.TaskStatus;
import com.shivang.crm.modules.task.repository.TaskRepository;
import com.shivang.crm.modules.task.specification.TaskSpecification;
import com.shivang.crm.shared.enums.OwnershipScope;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.NotFoundException;
import com.shivang.crm.shared.exception.PermissionDeniedException;
import com.shivang.crm.shared.service.EntityResolverService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final com.shivang.crm.modules.rbac.service.RecordScopeGuard recordScopeGuard;
    private final EntityResolverService entityResolverService;
    private final ReminderPlanningService reminderPlanningService;
    private final RecurrenceScheduleService recurrenceScheduleService;
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;

    private final TenantContext tenantContext;

    public TaskResponse createTask(UUID tenantId, UUID userId, TaskCreateRequest request) {
        if (!permissionEvaluatorService.hasPermission(tenantId, userId, "task:write")) {
            throw new PermissionDeniedException("No permission to create tasks");
        }

        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("source", "MANUAL");
        eventMetadata.put("actorType", "USER");
        return createTaskInternal(tenantId, userId, request, eventMetadata);
    }

    public TaskResponse createTaskInternal(UUID tenantId, UUID actorId, TaskCreateRequest request) {
        return createTaskInternal(tenantId, actorId, request, Map.of("source", "INTERNAL"));
    }

    public TaskResponse createTaskInternal(UUID tenantId, UUID actorId, TaskCreateRequest request, Map<String, Object> eventMetadata) {
        // Validate linked entity if provided
        if (request.getEntityType() != null && request.getEntityId() != null) {
            entityResolverService.validateEntityExists(
                request.getEntityType(), 
                request.getEntityId(), 
                tenantId
            );
        }

        // Validate assignee if provided
        // if (request.getAssignedTo() != null) {
        //     entityResolverService.resolveUserName(request.getAssignedTo());
        // }

        Task task = Task.builder()
            .tenantId(tenantId)
            .createdBy(actorId)
            .subject(request.getSubject())
            .description(request.getDescription())
            .dueDate(request.getDueDate())
            .status(request.getStatus() != null ? request.getStatus() : TaskStatus.NOT_STARTED)
            .priority(request.getPriority() != null ? request.getPriority() : TaskPriority.MEDIUM)
            .entityType(request.getEntityType())
            .entityId(request.getEntityId())
            .ownerId(request.getOwnerUserId())
            .remindAt(request.getRemindAt())
            .recurrence(request.getRecurrence())
            .customData(request.getCustomData())
            // .assignedTo(request.getAssignedTo())
            .isClosed(false)
            .build();

        Task savedTask = taskRepository.save(task);
        if (savedTask.getRecurrence() != null) {
            recurrenceScheduleService.upsertSchedule(
                    tenantId,
                    ReminderSourceType.TASK,
                    savedTask.getId(),
                    savedTask.getDueDate(),
                    savedTask.getRemindAt(),
                    savedTask.getRecurrence(),
                    null
            );
        }
        reminderPlanningService.planForTask(savedTask);
        log.info("Created task {} for tenant {}", savedTask.getId(), tenantId);

        Map<String, Object> enrichedEventMetadata = new HashMap<>();
        if (eventMetadata != null) enrichedEventMetadata.putAll(eventMetadata);
        enrichedEventMetadata.put("actorId", actorId.toString());
        if (!enrichedEventMetadata.containsKey("actorType")) {
            enrichedEventMetadata.put("actorType", "SYSTEM");
        }
        canonicalCrmEventPublisher.publish(
            savedTask.getTenantId(),
            CanonicalCrmEvent.TASK_ENTITY_TYPE,
            CanonicalCrmEvent.CREATED_EVENT_TYPE,
            savedTask.getId(),
            enrichedEventMetadata
        );

        return toResponse(savedTask);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> listTasks(
        UUID tenantId,
        String entityType,
        UUID entityId,
        TaskStatus status,
        Pageable pageable
    ) {
        Specification<Task> spec = Specification.where(TaskSpecification.hasTenant(tenantId))
            .and(TaskSpecification.notDeleted());

        if (entityType != null && entityId != null) {
            spec = spec.and(TaskSpecification.hasEntity(entityType, entityId));
        }

        if (status != null) {
            spec = spec.and(TaskSpecification.hasStatus(status));
        }

        // Apply ownership scope filtering
        List<OwnershipScope> userScopes = permissionEvaluatorService.getUserOwnershipScopes(tenantId, tenantContext.getUserId());
        if (!userScopes.contains(OwnershipScope.ALL)) {
            spec = spec.and(TaskSpecification.hasOwnershipScope(userScopes));
        }

        Page<Task> taskPage = taskRepository.findAll(spec, pageable);
        return taskPage.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID id, UUID tenantId) {
        Task task = findTaskByIdAndTenant(id, tenantId);

        // RBAC-7: single-record read must respect the task:read scope using
        // the module's established ownership convention (creator/owner).
        UUID currentUserId = tenantContext.getUserId();
        String scope = recordScopeGuard.requireScope(tenantId, currentUserId, "task", "read");
        assertTaskInScope(scope, task, currentUserId, tenantId);

        return toResponse(task);
    }

    /**
     * RBAC-7 ownership convention for tasks (mirrors hasWritePermission):
     * OWN -> creator or owner is the caller; TEAM -> creator within the
     * caller's team (owner-based TEAM coverage comes from the list spec).
     */
    private void assertTaskInScope(String scope, Task task, UUID userId, UUID tenantId) {
        boolean allowed = switch (scope) {
            case "ALL" -> true;
            case "OWN" -> userId.equals(task.getCreatedBy()) || userId.equals(task.getOwnerId());
            case "TEAM" -> userId.equals(task.getCreatedBy())
                    || permissionEvaluatorService.isInSameTeam(tenantId, userId, task.getCreatedBy());
            default -> false;
        };
        if (!allowed) {
            throw new com.shivang.crm.shared.exception.PermissionDeniedException("SCOPE_DENIED",
                    "Record is outside your access scope");
        }
    }

    public TaskResponse updateTask(UUID id, UUID tenantId, UUID userId, TaskUpdateRequest request) {
        Task task = findTaskByIdAndTenant(id, tenantId);

        // Check write permission based on ownership
        if (!hasWritePermission(task, userId, tenantId)) {
            throw new PermissionDeniedException("No permission to update this task");
        }

        // Validate linked entity if changed
        if (request.getEntityType() != null && request.getEntityId() != null) {
            if (!request.getEntityType().equals(task.getEntityType()) || 
                !request.getEntityId().equals(task.getEntityId())) {
                entityResolverService.validateEntityExists(
                    request.getEntityType(), 
                    request.getEntityId(), 
                    tenantId
                );
            }
        }

        // Update fields
        TaskStatus previousStatus = task.getStatus();
        if (request.getSubject() != null) {
            task.setSubject(request.getSubject());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
            if (Task.isStatusClosed(request.getStatus())) {
                task.setIsClosed(true);
                task.setCompletedAt(Instant.now());
            } else {
                task.setIsClosed(false);
                task.setCompletedAt(null);
            }
        }
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        if (request.getRemindAt() != null) {
            task.setRemindAt(request.getRemindAt());
        }
        if (request.getRecurrence() != null) {
            task.setRecurrence(request.getRecurrence());
        }
        if (request.getCustomData() != null) {
            task.setCustomData(request.getCustomData());
        }
        // if (request.getAssignedTo() != null) {
        //     entityResolverService.resolveUserName(request.getAssignedTo());
        //     task.setAssignedTo(request.getAssignedTo());
        // }

        task.setUpdatedBy(userId);
        task = taskRepository.save(task);
        if (task.getRecurrence() != null) {
            recurrenceScheduleService.upsertSchedule(
                    tenantId,
                    ReminderSourceType.TASK,
                    task.getId(),
                    task.getDueDate(),
                    task.getRemindAt(),
                    task.getRecurrence(),
                    null
            );
        } else {
            recurrenceScheduleService.deactivateSchedule(tenantId, ReminderSourceType.TASK, task.getId());
        }
        reminderPlanningService.cancelPending(tenantId, ReminderSourceType.TASK, task.getId());
        reminderPlanningService.planForTask(task);
        log.info("Updated task {} for tenant {}", id, tenantId);

        if (task.getStatus() != previousStatus) {
            if (task.getStatus() == TaskStatus.COMPLETED && previousStatus != TaskStatus.COMPLETED) {
                publishTaskCompleted(task, previousStatus, userId);
            } else if (previousStatus != null) {
                publishTaskStatusChanged(task, previousStatus, userId);
            }
        }

        return toResponse(task);
    }

    public void deleteTask(UUID id, UUID tenantId, UUID userId) {
        Task task = findTaskByIdAndTenant(id, tenantId);

        // Check delete permission
        if (!permissionEvaluatorService.hasPermission(tenantId, userId, "task:delete")) {
            throw new PermissionDeniedException("No permission to delete tasks");
        }

        task.setDeleted(true);
        task.setUpdatedBy(userId);
        taskRepository.save(task);
        recurrenceScheduleService.deactivateSchedule(tenantId, ReminderSourceType.TASK, task.getId());
        log.info("Soft deleted task {} for tenant {}", id, tenantId);
    }

    public TaskResponse completeTask(UUID id, UUID tenantId, UUID userId) {
        Task task = findTaskByIdAndTenant(id, tenantId);

        if (!hasWritePermission(task, userId, tenantId)) {
            throw new PermissionDeniedException("No permission to complete this task");
        }

        TaskStatus previousStatus = task.getStatus();
        task.complete(userId);
        Task completedTask = taskRepository.save(task);
        log.info("Completed task {} for tenant {}", id, tenantId);

        if (previousStatus != TaskStatus.COMPLETED) {
            publishTaskCompleted(completedTask, previousStatus, userId);
        }

        return toResponse(completedTask);
    }

    public TaskResponse reopenTask(UUID id, UUID tenantId, UUID userId) {
        Task task = findTaskByIdAndTenant(id, tenantId);

        if (!hasWritePermission(task, userId, tenantId)) {
            throw new PermissionDeniedException("No permission to reopen this task");
        }

        TaskStatus previousStatus = task.getStatus();
        task.reopen(userId);
        Task reopenedTask = taskRepository.save(task);
        log.info("Reopened task {} for tenant {}", id, tenantId);

        if (previousStatus != reopenedTask.getStatus()) {
            publishTaskStatusChanged(reopenedTask, previousStatus, userId);
        }

        return toResponse(reopenedTask);
    }

    private Task findTaskByIdAndTenant(UUID id, UUID tenantId) {
        return taskRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new NotFoundException("Task not found with id: " + id));
    }

    private void publishTaskCompleted(Task task, TaskStatus previousStatus, UUID userId) {
        Map<String, Object> eventMetadata = new HashMap<>();
        if (previousStatus != null) {
            eventMetadata.put("previousStatus", previousStatus.name());
        }
        eventMetadata.put("newStatus", task.getStatus().name());
        eventMetadata.put("actorId", userId.toString());
        eventMetadata.put("actorType", "USER");
        canonicalCrmEventPublisher.publish(
            task.getTenantId(),
            CanonicalCrmEvent.TASK_ENTITY_TYPE,
            CanonicalCrmEvent.COMPLETED_EVENT_TYPE,
            task.getId(),
            eventMetadata
        );
    }

    private void publishTaskStatusChanged(Task task, TaskStatus previousStatus, UUID userId) {
        Map<String, Object> eventMetadata = new HashMap<>();
        if (previousStatus != null) {
            eventMetadata.put("previousStatus", previousStatus.name());
        }
        eventMetadata.put("newStatus", task.getStatus().name());
        eventMetadata.put("actorId", userId.toString());
        eventMetadata.put("actorType", "USER");
        canonicalCrmEventPublisher.publish(
            task.getTenantId(),
            CanonicalCrmEvent.TASK_ENTITY_TYPE,
            CanonicalCrmEvent.STATUS_CHANGED_EVENT_TYPE,
            task.getId(),
            eventMetadata
        );
    }

    private boolean hasWritePermission(Task task, UUID userId, UUID tenantId) {
        if (permissionEvaluatorService.hasPermission(tenantId, userId, "task:write")) {
            OwnershipScope scope = permissionEvaluatorService.getOwnershipScope(tenantId, userId, "task");
            
            if (scope == OwnershipScope.ALL) {
                return true;
            }
            
            if (scope == OwnershipScope.TEAM) {
                // Check if user is in the same team as task owner
                return permissionEvaluatorService.isInSameTeam(tenantId, userId, task.getCreatedBy());
            }
            
            if (scope == OwnershipScope.OWN) {
                return task.getCreatedBy().equals(userId);
            }
        }
        return false;
    }

    private TaskResponse toResponse(Task task) {
        TaskResponse response = TaskResponse.builder()
            .id(task.getId())
            .subject(task.getSubject())
            .description(task.getDescription())
            .dueDate(task.getDueDate())
            .status(task.getStatus())
            .priority(task.getPriority())
            .entityType(task.getEntityType())
            .entityId(task.getEntityId())
            .entityName(resolveEntityName(task.getEntityType(), task.getEntityId()))
            .remindAt(task.getRemindAt())
            .recurrence(task.getRecurrence())
            .customData(task.getCustomData())
            // .assignedTo(task.getAssignedTo())
            // .assigneeName(resolveUserName(task.getAssignedTo()))
            .isClosed(task.getIsClosed())
            .completedAt(task.getCompletedAt())
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .createdBy(task.getCreatedBy())
            .ownerUserId(task.getOwnerId())
            .isOverdue(task.isOverdue())
            .build();

        return response;
    }

    private String resolveEntityName(String entityType, UUID entityId) {
        if (entityType == null || entityId == null) {
            return null;
        }
        return entityResolverService.resolveEntityName(entityType, entityId);
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return null;
        }
        try {
            return entityResolverService.resolveUserName(userId);
        } catch (Exception e) {
            return "Unknown User";
        }
    }
}
