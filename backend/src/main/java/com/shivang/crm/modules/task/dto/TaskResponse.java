package com.shivang.crm.modules.task.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.shivang.crm.modules.task.entity.TaskPriority;
import com.shivang.crm.modules.task.entity.TaskStatus;
import com.shivang.crm.shared.model.Recurrence;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload for a task")
public class TaskResponse {

    @Schema(description = "Task UUID")
    private UUID id;

    @Schema(description = "Tenant UUID")
    private UUID tenantId;

    @Schema(description = "Task subject")
    private String subject;

    @Schema(description = "Task description")
    private String description;

    @Schema(description = "Due date and time")
    private Instant dueDate;

    @Schema(description = "Task status")
    private TaskStatus status;

    @Schema(description = "Task priority")
    private TaskPriority priority;

    @Schema(description = "Entity type this task is linked to")
    private String entityType;

    @Schema(description = "Entity ID this task is linked to")
    private UUID entityId;

    @Schema(description = "Resolved entity name")
    private String entityName;

    @Schema(description = "Reminder time")
    private Instant remindAt;

    @Schema(description = "Recurrence pattern")
    private Recurrence recurrence;

    @Schema(description = "Completion timestamp")
    private Instant completedAt;

    @Schema(description = "Whether task is closed")
    private Boolean isClosed;

    @Schema(description = "Whether task is overdue")
    private Boolean isOverdue;

    @Schema(description = "Custom fields")
    private Map<String, Object> customData;

    @Schema(description = "Task owner user UUID")
    private UUID ownerUserId;

    @Schema(description = "User who created this task")
    private UUID createdBy;

    @Schema(description = "User who last updated this task")
    private UUID updatedBy;

    @Schema(description = "Timestamp when task was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when task was last updated")
    private Instant updatedAt;
}