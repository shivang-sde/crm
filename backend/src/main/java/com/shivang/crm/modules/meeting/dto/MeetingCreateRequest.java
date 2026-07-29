package com.shivang.crm.modules.meeting.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.meeting.entity.Meeting;
import com.shivang.crm.shared.model.Recurrence;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Request payload for creating a meeting")
public class MeetingCreateRequest {

    @NotBlank(message = "Meeting subject is required")
    @Size(max = 255, message = "Meeting subject must not exceed 255 characters")
    @Schema(description = "Meeting subject", requiredMode = Schema.RequiredMode.REQUIRED)
    private String subject;

    @Schema(description = "Meeting description")
    private String description;

    @Schema(description = "Meeting agenda")
    private String agenda;

    @Schema(description = "Location (address or video link)")
    private String location;

    @Schema(description = "Meeting type")
    @JsonProperty("meeting_type")
    private Meeting.MeetingType meetingType;

    @NotNull(message = "Meeting start time is required")
    @Schema(
            description = "Start time",
            example = "2026-07-28T10:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @JsonProperty("start_time")
    private Instant startTime;

    @Schema(description = "End time", example = "2026-07-28T10:30:00Z")
    @JsonProperty("end_time")
    @NotNull(message = "Meeting end time is required")
    private Instant endTime;

    @Schema(description = "Attendees (list of emails or contact IDs)")
    private List<String> attendees;

    @Schema(description = "Entity type this meeting is linked to")
    @JsonProperty("entity_type")
    private String entityType;

    @Schema(description = "Entity ID this meeting is linked to")
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

    @Schema(description = "User assigned to this meeting")
    @JsonProperty("assigned_to")
    private UUID assignedTo;
}