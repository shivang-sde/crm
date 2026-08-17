package com.shivang.crm.modules.workflow.service;

import java.util.UUID;

public interface WorkflowOwnerAssignmentAdapter {

    String entityType();

    WorkflowOwnerAssignmentResult assign(UUID tenantId, UUID actorId, UUID entityId, UUID ownerId);
}