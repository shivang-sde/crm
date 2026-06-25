package com.shivang.crm.modules.meeting.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.meeting.entity.Meeting;
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
@Schema(description = "Request payload for creating a meeting")
public class MeetingCreateRequest {

    @Schema(description = "Meeting subject", required = true)
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

    @Schema(description = "Start time", required = true)
    @JsonProperty("start_time")
    private Instant startTime;

    @Schema(description = "End time")
    @JsonProperty("end_time")
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
