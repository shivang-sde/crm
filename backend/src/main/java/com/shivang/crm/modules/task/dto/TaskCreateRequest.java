package com.shivang.crm.modules.task.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.task.entity.TaskPriority;
import com.shivang.crm.modules.task.entity.TaskStatus;
import com.shivang.crm.shared.model.Recurrence;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Request payload for creating a new task")
public class TaskCreateRequest {

    @NotBlank(message = "Task subject is required")
    @Schema(description = "Task subject", example = "Follow up with client")
    private String subject;

    @Schema(description = "Task description")
    private String description;

    @Schema(description = "Due date and time", example = "2026-12-31T17:00:00Z")
    @JsonProperty("due_date")
    private Instant dueDate;

    @Schema(description = "Task status", example = "NOT_STARTED")
    @Builder.Default
    private TaskStatus status = TaskStatus.NOT_STARTED;

    @Schema(description = "Task priority", example = "MEDIUM")
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Schema(description = "Entity type this task is linked to (e.g., LEAD, CONTACT, ACCOUNT, DEAL)", example = "LEAD")
    @JsonProperty("entity_type")
    private String entityType;

    @Schema(description = "Entity ID this task is linked to", example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("entity_id")
    private UUID entityId;

    @Schema(description = "Reminder time", example = "2026-12-31T09:00:00Z")
    @JsonProperty("remind_at")
    private Instant remindAt;

    @Schema(description = "Recurrence pattern")
    private Recurrence recurrence;

    @Schema(description = "Task owner user UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("owner_user_id")
    private UUID ownerUserId;

    @Schema(description = "User assigned to this task", example = "550e8400-e29b-41d4-a716-446655440001")
    @JsonProperty("assigned_to")
    private UUID assignedTo;

    @Schema(description = "Custom fields as key-value map")
    @JsonProperty("custom_data")
    private Map<String, Object> customData;
}
