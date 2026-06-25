package com.shivang.crm.modules.task.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("tenant_id")
    private UUID tenantId;

    @Schema(description = "Task subject")
    private String subject;

    @Schema(description = "Task description")
    private String description;

    @Schema(description = "Due date and time")
    @JsonProperty("due_date")
    private Instant dueDate;

    @Schema(description = "Task status")
    private TaskStatus status;

    @Schema(description = "Task priority")
    private TaskPriority priority;

    @Schema(description = "Entity type this task is linked to")
    @JsonProperty("entity_type")
    private String entityType;

    @Schema(description = "Entity ID this task is linked to")
    @JsonProperty("entity_id")
    private UUID entityId;

    @Schema(description = "Resolved entity name (e.g., Lead name, Contact name)")
    @JsonProperty("entity_name")
    private String entityName;

    @Schema(description = "Reminder time")
    @JsonProperty("remind_at")
    private Instant remindAt;

    @Schema(description = "Recurrence pattern")
    private Recurrence recurrence;

    @Schema(description = "Completion timestamp")
    @JsonProperty("completed_at")
    private Instant completedAt;

    @Schema(description = "Whether task is closed")
    @JsonProperty("is_closed")
    private Boolean isClosed;

    @Schema(description = "Whether task is overdue")
    @JsonProperty("is_overdue")
    private Boolean isOverdue;

    @Schema(description = "Custom fields")
    @JsonProperty("custom_data")
    private Map<String, Object> customData;

    @Schema(description = "Task owner user UUID")
    @JsonProperty("owner_user_id")
    private UUID ownerUserId;

    @Schema(description = "User assigned to this task")
    @JsonProperty("assigned_to")
    private UUID assignedTo;

    @Schema(description = "Name of the assigned user")
    @JsonProperty("assignee_name")
    private String assigneeName;

    @Schema(description = "User who created this task")
    @JsonProperty("created_by")
    private UUID createdBy;

    @Schema(description = "User who last updated this task")
    @JsonProperty("updated_by")
    private UUID updatedBy;

    @Schema(description = "Timestamp when task was created")
    @JsonProperty("created_at")
    private Instant createdAt;

    @Schema(description = "Timestamp when task was last updated")
    @JsonProperty("updated_at")
    private Instant updatedAt;
}
