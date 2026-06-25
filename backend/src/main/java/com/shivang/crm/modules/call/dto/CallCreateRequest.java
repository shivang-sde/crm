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
@Schema(description = "Request payload for creating a call")
public class CallCreateRequest {

    @Schema(description = "Call subject", required = true)
    private String subject;

    @Schema(description = "Call description")
    private String description;

    @Schema(description = "Call type (INCOMING/OUTGOING)", required = true)
    private Call.CallType callType;

    @Schema(description = "Phone number")
    private String phoneNumber;

    @Schema(description = "Start time")
    @JsonProperty("start_time")
    private Instant startTime;

    @Schema(description = "End time")
    @JsonProperty("end_time")
    private Instant endTime;

    @Schema(description = "Entity type this call is linked to")
    @JsonProperty("entity_type")
    private String entityType;

    @Schema(description = "Entity ID this call is linked to")
    @JsonProperty("entity_id")
    private UUID entityId;

    @Schema(description = "Reminder time")
    @JsonProperty("remind_at")
    private Instant remindAt;

    @Schema(description = "Recurrence pattern")
    private Recurrence recurrence;

    @Schema(description = "Custom fields")
    @JsonProperty("custom_data")
    private Map<String, Object> customData;

    @Schema(description = "User assigned to this call")
    @JsonProperty("assigned_to")
    private UUID assignedTo;
}
