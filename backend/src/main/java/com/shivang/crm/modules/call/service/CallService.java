package com.shivang.crm.modules.call.service;

import com.shivang.crm.exception.NotFoundException;
import com.shivang.crm.exception.PermissionDeniedException;
import com.shivang.crm.modules.call.dto.CallCreateRequest;
import com.shivang.crm.modules.call.dto.CallResponse;
import com.shivang.crm.modules.call.dto.CallUpdateRequest;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.call.repository.CallSpecifications;
import com.shivang.crm.shared.model.OwnershipScope;
import com.shivang.crm.shared.service.EntityResolverService;
import com.shivang.crm.shared.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CallService {

    private final CallRepository callRepository;
    private final PermissionService permissionService;
    private final EntityResolverService entityResolverService;

    public CallResponse createCall(UUID tenantId, UUID userId, CallCreateRequest request) {
        // Validate permissions
        if (!permissionService.hasPermission(tenantId, userId, "call:write")) {
            throw new PermissionDeniedException("No permission to create calls");
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

        Call call = Call.builder()
            .tenantId(tenantId)
            .createdBy(userId)
            .updatedBy(userId)
            .subject(request.getSubject())
            .description(request.getDescription())
            .callType(request.getCallType() != null ? request.getCallType() : Call.CallType.OUTGOING)
            .phoneNumber(request.getPhoneNumber())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .entityType(request.getEntityType())
            .entityId(request.getEntityId())
            .remindAt(request.getRemindAt())
            .recurrence(request.getRecurrence())
            .customData(request.getCustomData())
            .build();

        Call savedCall = callRepository.save(call);
        log.info("Created call {} for tenant {}", savedCall.getId(), tenantId);

        return toResponse(savedCall);
    }

    @Transactional(readOnly = true)
    public Page<CallResponse> listCalls(
        UUID tenantId,
        String entityType,
        UUID entityId,
        Call.CallStatus status,
        Pageable pageable
    ) {
        Specification<Call> spec = Specification.where(CallSpecifications.hasTenant(tenantId))
            .and(CallSpecifications.notDeleted());

        if (entityType != null && entityId != null) {
            spec = spec.and(CallSpecifications.hasEntity(entityType, entityId));
        }

        if (status != null) {
            spec = spec.and(CallSpecifications.hasStatus(status));
        }

        // Apply ownership scope filtering
        List<OwnershipScope> userScopes = permissionService.getUserOwnershipScopes(tenantId);
        if (!userScopes.contains(OwnershipScope.ALL)) {
            spec = spec.and(CallSpecifications.hasOwnerOrAssignedTo(
                com.shivang.crm.shared.security.UserContext.getCurrentUserId()
            ));
        }

        Page<Call> callPage = callRepository.findAll(spec, pageable);
        return callPage.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CallResponse getCall(UUID id, UUID tenantId) {
        Call call = findCallByIdAndTenant(id, tenantId);
        return toResponse(call);
    }

    public CallResponse updateCall(UUID id, UUID tenantId, UUID userId, CallUpdateRequest request) {
        Call call = findCallByIdAndTenant(id, tenantId);

        // Check write permission based on ownership
        if (!hasWritePermission(call, userId, tenantId)) {
            throw new PermissionDeniedException("No permission to update this call");
        }

        // Validate linked entity if changed
        if (request.getEntityType() != null && request.getEntityId() != null) {
            if (!request.getEntityType().equals(call.getEntityType()) || 
                !request.getEntityId().equals(call.getEntityId())) {
                entityResolverService.validateEntityExists(
                    request.getEntityType(), 
                    request.getEntityId(), 
                    tenantId
                );
            }
        }

        // Update fields
        if (request.getSubject() != null) {
            call.setSubject(request.getSubject());
        }
        if (request.getDescription() != null) {
            call.setDescription(request.getDescription());
        }
        if (request.getCallType() != null) {
            call.setCallType(request.getCallType());
        }
        if (request.getPhoneNumber() != null) {
            call.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getStartTime() != null) {
            call.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            call.setEndTime(request.getEndTime());
        }
        if (request.getStatus() != null) {
            call.setStatus(request.getStatus());
        }
        if (request.getRemindAt() != null) {
            call.setRemindAt(request.getRemindAt());
        }
        if (request.getRecurrence() != null) {
            call.setRecurrence(request.getRecurrence());
        }
        if (request.getCustomData() != null) {
            call.setCustomData(request.getCustomData());
        }
        if (request.getAssignedTo() != null) {
            entityResolverService.resolveUserName(request.getAssignedTo());
            call.setAssignedTo(request.getAssignedTo());
        }

        call.setUpdatedBy(userId);
        Call updatedCall = callRepository.save(call);
        log.info("Updated call {} for tenant {}", id, tenantId);

        return toResponse(updatedCall);
    }

    public void deleteCall(UUID id, UUID tenantId, UUID userId) {
        Call call = findCallByIdAndTenant(id, tenantId);

        // Check delete permission
        if (!permissionService.hasPermission(tenantId, userId, "call:delete")) {
            throw new PermissionDeniedException("No permission to delete calls");
        }

        call.setDeleted(true);
        call.setUpdatedBy(userId);
        callRepository.save(call);
        log.info("Soft deleted call {} for tenant {}", id, tenantId);
    }

    private Call findCallByIdAndTenant(UUID id, UUID tenantId) {
        return callRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new NotFoundException("Call not found with id: " + id));
    }

    private boolean hasWritePermission(Call call, UUID userId, UUID tenantId) {
        if (permissionService.hasPermission(tenantId, userId, "call:write")) {
            OwnershipScope scope = permissionService.getOwnershipScope(tenantId, userId, "call");
            
            if (scope == OwnershipScope.ALL) {
                return true;
            }
            
            if (scope == OwnershipScope.TEAM) {
                return permissionService.isInSameTeam(tenantId, userId, call.getCreatedBy());
            }
            
            if (scope == OwnershipScope.OWN) {
                return call.getCreatedBy().equals(userId);
            }
        }
        return false;
    }

    private CallResponse toResponse(Call call) {
        CallResponse response = CallResponse.builder()
            .id(call.getId())
            .subject(call.getSubject())
            .description(call.getDescription())
            .callType(call.getCallType())
            .phoneNumber(call.getPhoneNumber())
            .startTime(call.getStartTime())
            .endTime(call.getEndTime())
            .durationMinutes(call.getDurationMinutes())
            .entityType(call.getEntityType())
            .entityId(call.getEntityId())
            .entityName(resolveEntityName(call.getEntityType(), call.getEntityId()))
            .status(call.getStatus())
            .remindAt(call.getRemindAt())
            .recurrence(call.getRecurrence())
            .customData(call.getCustomData())
            .assignedTo(call.getAssignedTo())
            .assigneeName(resolveUserName(call.getAssignedTo()))
            .createdAt(call.getCreatedAt())
            .updatedAt(call.getUpdatedAt())
            .createdBy(call.getCreatedBy())
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
