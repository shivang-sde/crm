package com.shivang.crm.modules.meeting.service;

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
import com.shivang.crm.modules.meeting.dto.MeetingCreateRequest;
import com.shivang.crm.modules.meeting.dto.MeetingResponse;
import com.shivang.crm.modules.meeting.dto.MeetingUpdateRequest;
import com.shivang.crm.modules.meeting.entity.Meeting;
import com.shivang.crm.modules.meeting.repository.MeetingRepository;
import com.shivang.crm.modules.meeting.repository.MeetingSpecifications;
import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.reminder.service.ReminderPlanningService;
import com.shivang.crm.modules.recurrence.service.RecurrenceScheduleService;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
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
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final com.shivang.crm.modules.rbac.service.RecordScopeGuard recordScopeGuard;
    private final EntityResolverService entityResolverService;
    private final ReminderPlanningService reminderPlanningService;
    private final RecurrenceScheduleService recurrenceScheduleService;
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;

    private final TenantContext tenantContext;

    public MeetingResponse createMeeting(UUID tenantId, UUID userId, MeetingCreateRequest request) {
        // Validate permissions
        if (!permissionEvaluatorService.hasPermission(tenantId, userId, "meeting:write")) {
            throw new PermissionDeniedException("No permission to create meetings");
        }

        // Validate linked entity if provided
        if (request.getEntityType() != null && request.getEntityId() != null) {
            entityResolverService.validateEntityExists(
                    request.getEntityType(),
                    request.getEntityId(),
                    tenantId
            );
        }

        // Validate assignee if provided
        if (request.getAssignedTo() != null) {
            entityResolverService.resolveUserName(request.getAssignedTo());
        }

        Meeting meeting = Meeting.builder()
                .tenantId(tenantId)
                .createdBy(userId)
                .ownerId(userId)
                .subject(request.getSubject())
                .description(request.getDescription())
                .agenda(request.getAgenda())
                .location(request.getLocation())
                .meetingType(request.getMeetingType())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .attendees(request.getAttendees())
                .assignedTo(request.getAssignedTo())
                .status(Meeting.MeetingStatus.PLANNED)
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .remindAt(request.getRemindAt())
                .recurrence(request.getRecurrence())
                .customData(request.getCustomData())
                .build();

        Meeting savedMeeting = meetingRepository.save(meeting);
        if (savedMeeting.getRecurrence() != null) {
            recurrenceScheduleService.upsertSchedule(
                    tenantId,
                    ReminderSourceType.MEETING,
                    savedMeeting.getId(),
                    savedMeeting.getStartTime(),
                    savedMeeting.getRemindAt(),
                    savedMeeting.getRecurrence(),
                    null
            );
        }
        reminderPlanningService.planForMeeting(savedMeeting);
        log.info("Created meeting {} for tenant {}", savedMeeting.getId(), tenantId);

        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("source", "MANUAL");
        eventMetadata.put("actorId", userId.toString());
        eventMetadata.put("actorType", "USER");
        canonicalCrmEventPublisher.publish(
            savedMeeting.getTenantId(),
            CanonicalCrmEvent.MEETING_ENTITY_TYPE,
            CanonicalCrmEvent.CREATED_EVENT_TYPE,
            savedMeeting.getId(),
            eventMetadata
        );

        return toResponse(savedMeeting);
    }

    @Transactional(readOnly = true)
    public Page<MeetingResponse> listMeetings(
            UUID tenantId,
            String entityType,
            UUID entityId,
            Meeting.MeetingStatus status,
            Pageable pageable
    ) {
        Specification<Meeting> spec = Specification.where(MeetingSpecifications.hasTenant(tenantId))
                .and(MeetingSpecifications.notDeleted());

        if (entityType != null && entityId != null) {
            spec = spec.and(MeetingSpecifications.hasEntity(entityType, entityId));
        }

        if (status != null) {
            spec = spec.and(MeetingSpecifications.hasStatus(status));
        }

        // Apply ownership scope filtering
        List<OwnershipScope> userScopes = permissionEvaluatorService.getUserOwnershipScopes(tenantId, tenantContext.getUserId());
        if (!userScopes.contains(OwnershipScope.ALL)) {
            spec = spec.and(MeetingSpecifications.hasOwnerOrAssignedToOrCreatedBy(tenantContext.getUserId()));
        }

        Page<Meeting> meetingPage = meetingRepository.findAll(spec, pageable);
        return meetingPage.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(UUID id, UUID tenantId) {
        Meeting meeting = findMeetingByIdAndTenant(id, tenantId);

        // RBAC-7: single-record read must respect the meeting:read scope
        // using the module's established creator-based convention.
        UUID currentUserId = tenantContext.getUserId();
        String scope = recordScopeGuard.requireScope(tenantId, currentUserId, "meeting", "read");
        assertMeetingInScope(scope, meeting, currentUserId, tenantId);

        return toResponse(meeting);
    }

    /**
     * RBAC-7 ownership convention for meetings (mirrors hasWritePermission):
     * OWN -> creator is the caller; TEAM -> creator within the caller's team.
     */
    private void assertMeetingInScope(String scope, Meeting meeting, UUID userId, UUID tenantId) {
        boolean allowed = switch (scope) {
            case "ALL" -> true;
            case "OWN" -> userId.equals(meeting.getCreatedBy());
            case "TEAM" -> permissionEvaluatorService.isInSameTeam(tenantId, userId, meeting.getCreatedBy());
            default -> false;
        };
        if (!allowed) {
            throw new com.shivang.crm.shared.exception.PermissionDeniedException("SCOPE_DENIED",
                    "Record is outside your access scope");
        }
    }

    public MeetingResponse updateMeeting(UUID id, UUID tenantId, UUID userId, MeetingUpdateRequest request) {
        Meeting meeting = findMeetingByIdAndTenant(id, tenantId);

        // Check write permission based on ownership
        if (!hasWritePermission(meeting, userId, tenantId)) {
            throw new PermissionDeniedException("No permission to update this meeting");
        }

        // Validate linked entity if changed
        if (request.getEntityType() != null && request.getEntityId() != null) {
            if (!request.getEntityType().equals(meeting.getEntityType())
                    || !request.getEntityId().equals(meeting.getEntityId())) {
                entityResolverService.validateEntityExists(
                        request.getEntityType(),
                        request.getEntityId(),
                        tenantId
                );
            }
        }

        // Update fields
        Meeting.MeetingStatus previousStatus = meeting.getStatus();
        if (request.getSubject() != null) {
            meeting.setSubject(request.getSubject());
        }
        if (request.getDescription() != null) {
            meeting.setDescription(request.getDescription());
        }
        if (request.getAgenda() != null) {
            meeting.setAgenda(request.getAgenda());
        }
        if (request.getLocation() != null) {
            meeting.setLocation(request.getLocation());
        }
        if (request.getMeetingType() != null) {
            meeting.setMeetingType(request.getMeetingType());
        }
        if (request.getStartTime() != null) {
            meeting.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            meeting.setEndTime(request.getEndTime());
        }
        if (request.getAttendees() != null) {
            meeting.setAttendees(request.getAttendees());
        }
        if (request.getStatus() != null) {
            meeting.setStatus(request.getStatus());
        }
        if (request.getRemindAt() != null) {
            meeting.setRemindAt(request.getRemindAt());
        }
        if (request.getRecurrence() != null) {
            meeting.setRecurrence(request.getRecurrence());
        }
        if (request.getCustomData() != null) {
            meeting.setCustomData(request.getCustomData());
        }
        if (request.getEntityType() != null || request.getEntityId() != null) {
            String newEntityType = request.getEntityType() != null
                    ? request.getEntityType()
                    : meeting.getEntityType();

            UUID newEntityId = request.getEntityId() != null
                    ? request.getEntityId()
                    : meeting.getEntityId();

            if (newEntityType == null || newEntityId == null) {
                throw new IllegalArgumentException(
                        "Both entity type and entity ID are required when linking a meeting"
                );
            }

            entityResolverService.validateEntityExists(
                    newEntityType,
                    newEntityId,
                    tenantId
            );

            meeting.setEntityType(newEntityType);
            meeting.setEntityId(newEntityId);
        }
        if (request.getAssignedTo() != null) {
            entityResolverService.resolveUserName(request.getAssignedTo());
            meeting.setAssignedTo(request.getAssignedTo());
        }

        meeting.setUpdatedBy(userId);
        Meeting updatedMeeting = meetingRepository.save(meeting);
        if (updatedMeeting.getRecurrence() != null) {
            recurrenceScheduleService.upsertSchedule(
                    tenantId,
                    ReminderSourceType.MEETING,
                    updatedMeeting.getId(),
                    updatedMeeting.getStartTime(),
                    updatedMeeting.getRemindAt(),
                    updatedMeeting.getRecurrence(),
                    null
            );
        } else {
            recurrenceScheduleService.deactivateSchedule(tenantId, ReminderSourceType.MEETING, updatedMeeting.getId());
        }
        reminderPlanningService.cancelPending(tenantId, ReminderSourceType.MEETING, updatedMeeting.getId());
        reminderPlanningService.planForMeeting(updatedMeeting);
        log.info("Updated meeting {} for tenant {}", id, tenantId);

        if (updatedMeeting.getStatus() != previousStatus) {
            Map<String, Object> eventMetadata = new HashMap<>();
            if (previousStatus != null) {
                eventMetadata.put("previousStatus", previousStatus.name());
            }
            eventMetadata.put("newStatus", updatedMeeting.getStatus().name());
            eventMetadata.put("actorId", userId.toString());
            eventMetadata.put("actorType", "USER");
            canonicalCrmEventPublisher.publish(
                updatedMeeting.getTenantId(),
                CanonicalCrmEvent.MEETING_ENTITY_TYPE,
                CanonicalCrmEvent.STATUS_CHANGED_EVENT_TYPE,
                updatedMeeting.getId(),
                eventMetadata
            );
        }

        return toResponse(updatedMeeting);
    }

    public void deleteMeeting(UUID id, UUID tenantId, UUID userId) {
        Meeting meeting = findMeetingByIdAndTenant(id, tenantId);

        // Check delete permission
        if (!permissionEvaluatorService.hasPermission(tenantId, userId, "meeting:delete")) {
            throw new PermissionDeniedException("No permission to delete meetings");
        }

        // RBAC-7: deletion must also fall within the caller's meeting:delete scope.
        String deleteScope = recordScopeGuard.requireScope(tenantId, userId, "meeting", "delete");
        assertMeetingInScope(deleteScope, meeting, userId, tenantId);

        meeting.setDeleted(true);
        meeting.setUpdatedBy(userId);
        meetingRepository.save(meeting);
        recurrenceScheduleService.deactivateSchedule(tenantId, ReminderSourceType.MEETING, meeting.getId());
        log.info("Soft deleted meeting {} for tenant {}", id, tenantId);
    }

    private Meeting findMeetingByIdAndTenant(UUID id, UUID tenantId) {
        return meetingRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Meeting not found with id: " + id));
    }

    private boolean hasWritePermission(Meeting meeting, UUID userId, UUID tenantId) {
        if (permissionEvaluatorService.hasPermission(tenantId, userId, "meeting:write")) {
            OwnershipScope scope = permissionEvaluatorService.getOwnershipScope(tenantId, userId, "meeting");

            if (scope == OwnershipScope.ALL) {
                return true;
            }

            if (scope == OwnershipScope.TEAM) {
                return permissionEvaluatorService.isInSameTeam(tenantId, userId, meeting.getCreatedBy());
            }

            if (scope == OwnershipScope.OWN) {
                return meeting.getCreatedBy().equals(userId);
            }
        }
        return false;
    }

    private MeetingResponse toResponse(Meeting meeting) {
        MeetingResponse response = MeetingResponse.builder()
                .id(meeting.getId())
                .subject(meeting.getSubject())
                .description(meeting.getDescription())
                .agenda(meeting.getAgenda())
                .location(meeting.getLocation())
                .meetingType(meeting.getMeetingType())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .attendees(meeting.getAttendees())
                .entityType(meeting.getEntityType())
                .entityId(meeting.getEntityId())
                .entityName(resolveEntityName(meeting.getEntityType(), meeting.getEntityId()))
                .status(meeting.getStatus())
                .remindAt(meeting.getRemindAt())
                .recurrence(meeting.getRecurrence())
                .customData(meeting.getCustomData())
                .assignedTo(meeting.getAssignedTo())
                .assigneeName(resolveUserName(meeting.getAssignedTo()))
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt())
                .createdBy(meeting.getCreatedBy())
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
