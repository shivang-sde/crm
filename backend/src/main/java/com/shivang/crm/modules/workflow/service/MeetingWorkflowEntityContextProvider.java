package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.meeting.entity.Meeting;
import com.shivang.crm.modules.meeting.repository.MeetingRepository;

@Component
public class MeetingWorkflowEntityContextProvider implements WorkflowEntityContextProvider {

    private final MeetingRepository meetingRepository;
    private final WorkflowRelatedRecordResolver relatedRecordResolver;

    public MeetingWorkflowEntityContextProvider(MeetingRepository meetingRepository, WorkflowRelatedRecordResolver relatedRecordResolver) {
        this.meetingRepository = meetingRepository;
        this.relatedRecordResolver = relatedRecordResolver;
    }

    @Override
    public String entityType() {
        return "MEETING";
    }

    @Override
    public Optional<Map<String, Object>> load(UUID tenantId, UUID entityId) {
        return meetingRepository.findByIdAndTenantIdAndDeletedFalse(entityId, tenantId)
            .map(this::toContext);
    }

    private Map<String, Object> toContext(Meeting meeting) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("id", meeting.getId());
        context.put("tenantId", meeting.getTenantId());
        context.put("ownerId", meeting.getOwnerId());
        context.put("createdBy", meeting.getCreatedBy());
        context.put("subject", meeting.getSubject());
        context.put("description", meeting.getDescription());
        context.put("agenda", meeting.getAgenda());
        context.put("location", meeting.getLocation());
        context.put("meetingType", meeting.getMeetingType() == null ? null : meeting.getMeetingType().name());
        context.put("startTime", meeting.getStartTime());
        context.put("endTime", meeting.getEndTime());
        context.put("status", meeting.getStatus() == null ? null : meeting.getStatus().name());
        context.put("attendees", meeting.getAttendees());
        context.put("assignedTo", meeting.getAssignedTo());
        context.put("entityType", meeting.getEntityType());
        context.put("entityId", meeting.getEntityId());
        context.put("remindAt", meeting.getRemindAt());
        context.put("createdAt", meeting.getCreatedAt());
        context.put("updatedAt", meeting.getUpdatedAt());
        context.put("customFields", meeting.getCustomData() == null ? Map.of() : meeting.getCustomData());
        // Controlled one-hop relationship: Meeting → linked CRM record (LEAD/CONTACT/ACCOUNT/DEAL).
        context.put("related", relatedRecordResolver
            .related(meeting.getEntityType(), meeting.getTenantId(), meeting.getEntityId())
            .orElse(null));
        return context;
    }
}
