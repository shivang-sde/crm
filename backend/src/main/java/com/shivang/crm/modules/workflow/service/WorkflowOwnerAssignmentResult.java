package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

public record WorkflowOwnerAssignmentResult(
    String entityType,
    UUID entityId,
    UUID ownerId,
    Map<String, Object> details
) {
}