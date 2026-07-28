package com.shivang.crm.modules.meeting.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
@Schema(description = "Response payload for a meeting")
public class MeetingResponse {

    @Schema(description = "Meeting UUID")
    private UUID id;

    @Schema(description = "Tenant UUID")
    private UUID tenantId;

    @Schema(description = "Meeting subject")
    private String subject;

    @Schema(description = "Meeting description")
    private String description;

    @Schema(description = "Meeting agenda")
    private String agenda;

    @Schema(description = "Location (address or video link)")
    private String location;

    @Schema(description = "Meeting type")
    private Meeting.MeetingType meetingType;

    @Schema(description = "Start time")
    private Instant startTime;

    @Schema(description = "End time")
    private Instant endTime;

    @Schema(description = "Attendees (list of emails or contact IDs)")
    private List<String> attendees;

    @Schema(description = "Entity type this meeting is linked to")
    private String entityType;

    @Schema(description = "Entity ID this meeting is linked to")
    private UUID entityId;

    @Schema(description = "Resolved entity name")
    private String entityName;

    @Schema(description = "Meeting status")
    private Meeting.MeetingStatus status;

    @Schema(description = "Reminder time")
    private Instant remindAt;

    @Schema(description = "Recurrence pattern")
    private Recurrence recurrence;

    @Schema(description = "Custom fields")
    private Map<String, Object> customData;

    @Schema(description = "Meeting owner user UUID")
    private UUID ownerUserId;

    @Schema(description = "User assigned to this meeting")
    private UUID assignedTo;

    @Schema(description = "Name of the assigned user")
    private String assigneeName;

    @Schema(description = "User who created this meeting")
    private UUID createdBy;

    @Schema(description = "User who last updated this meeting")
    private UUID updatedBy;

    @Schema(description = "Timestamp when meeting was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when meeting was last updated")
    private Instant updatedAt;
}