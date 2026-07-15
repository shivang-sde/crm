package com.shivang.crm.modules.call.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.call.entity.Call;
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
@Schema(description = "Response payload for a call")
public class CallResponse {

    @Schema(description = "Call UUID")
    private UUID id;

    @Schema(description = "Tenant UUID")
    @JsonProperty("tenant_id")
    private UUID tenantId;

    @Schema(description = "Call subject")
    private String subject;

    @Schema(description = "Call description")
    private String description;

    @Schema(description = "Call type")
    @JsonProperty("call_type")
    private Call.CallType callType;

    @Schema(description = "Phone number")
    @JsonProperty("phone_number")
    private String phoneNumber;

    @Schema(description = "Start time")
    @JsonProperty("start_time")
    private Instant startTime;

    @Schema(description = "End time")
    @JsonProperty("end_time")
    private Instant endTime;

    @Schema(description = "Duration in minutes")
    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    @Schema(description = "Disposition captured after the call ended")
    private String disposition;

    @Schema(description = "Call notes captured with disposition")
    private String notes;

    @Schema(description = "Suggested next action")
    @JsonProperty("next_action")
    private String nextAction;

    @Schema(description = "Follow-up timestamp")
    @JsonProperty("follow_up_at")
    private Instant followUpAt;

    @Schema(description = "Entity type this call is linked to")
    @JsonProperty("entity_type")
    private String entityType;

    @Schema(description = "Entity ID this call is linked to")
    @JsonProperty("entity_id")
    private UUID entityId;

    @Schema(description = "Resolved entity name")
    @JsonProperty("entity_name")
    private String entityName;

    @Schema(description = "Call status")
    private Call.CallStatus status;

    @Schema(description = "Reminder time")
    @JsonProperty("remind_at")
    private Instant remindAt;

    @Schema(description = "Recurrence pattern")
    private Recurrence recurrence;

    @Schema(description = "Custom fields")
    @JsonProperty("custom_data")
    private Map<String, Object> customData;

    @Schema(description = "Call owner user UUID")
    @JsonProperty("owner_user_id")
    private UUID ownerUserId;

    @Schema(description = "User assigned to this call")
    @JsonProperty("assigned_to")
    private UUID assignedTo;

    @Schema(description = "Name of the assigned user")
    @JsonProperty("assignee_name")
    private String assigneeName;

    @Schema(description = "User who created this call")
    @JsonProperty("created_by")
    private UUID createdBy;

    @Schema(description = "User who last updated this call")
    @JsonProperty("updated_by")
    private UUID updatedBy;

    @Schema(description = "Timestamp when call was created")
    @JsonProperty("created_at")
    private Instant createdAt;

    @Schema(description = "Timestamp when call was last updated")
    @JsonProperty("updated_at")
    private Instant updatedAt;
}
