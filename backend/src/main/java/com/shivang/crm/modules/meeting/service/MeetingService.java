package com.shivang.crm.modules.meeting.service;

import java.util.List;
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
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.shared.enums.OwnershipScope;
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
    private final EntityResolverService entityResolverService;

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
        log.info("Created meeting {} for tenant {}", savedMeeting.getId(), tenantId);

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
            spec = spec.and(MeetingSpecifications.hasOwnerOrAssignedTo(tenantContext.getUserId()));
        }

        Page<Meeting> meetingPage = meetingRepository.findAll(spec, pageable);
        return meetingPage.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(UUID id, UUID tenantId) {
        Meeting meeting = findMeetingByIdAndTenant(id, tenantId);
        return toResponse(meeting);
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
        log.info("Updated meeting {} for tenant {}", id, tenantId);

        return toResponse(updatedMeeting);
    }

    public void deleteMeeting(UUID id, UUID tenantId, UUID userId) {
        Meeting meeting = findMeetingByIdAndTenant(id, tenantId);

        // Check delete permission
        if (!permissionEvaluatorService.hasPermission(tenantId, userId, "meeting:delete")) {
            throw new PermissionDeniedException("No permission to delete meetings");
        }

        meeting.setDeleted(true);
        meeting.setUpdatedBy(userId);
        meetingRepository.save(meeting);
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
