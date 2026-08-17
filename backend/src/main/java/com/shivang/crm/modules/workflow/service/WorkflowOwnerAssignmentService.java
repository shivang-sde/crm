package com.shivang.crm.modules.workflow.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowOwnerAssignmentService {

    private final UserRepository userRepository;
    private final WorkflowOwnerAssignmentAdapterRegistry adapterRegistry;

    public WorkflowOwnerAssignmentResult assign(UUID tenantId, UUID actorId, String entityType, UUID entityId, UUID ownerId) {
        if (tenantId == null || actorId == null || entityId == null || ownerId == null) {
            throw new WorkflowOwnerAssignmentException("WORKFLOW_ASSIGN_OWNER_INVALID_CONFIG", "Assignment identity is incomplete");
        }

        User owner = userRepository.findByIdAndTenantIdAndDeletedFalse(ownerId, tenantId)
            .orElseThrow(() -> new WorkflowOwnerAssignmentException("WORKFLOW_ASSIGN_OWNER_USER_NOT_FOUND", "Owner user was not found"));
        if (!Boolean.TRUE.equals(owner.getIsActive())) {
            throw new WorkflowOwnerAssignmentException("WORKFLOW_ASSIGN_OWNER_USER_INACTIVE", "Owner user is inactive");
        }

        try {
            return adapterRegistry.get(entityType).assign(tenantId, actorId, entityId, ownerId);
        } catch (WorkflowOwnerAssignmentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new WorkflowOwnerAssignmentException("WORKFLOW_ASSIGN_OWNER_EXECUTION_FAILED", "Owner assignment failed");
        }
    }
}