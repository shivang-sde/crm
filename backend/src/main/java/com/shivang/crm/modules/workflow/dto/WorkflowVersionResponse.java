package com.shivang.crm.modules.workflow.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.shivang.crm.modules.workflow.entity.WorkflowVersionStatus;

public record WorkflowVersionResponse(
    UUID id,
    UUID workflowId,
    Integer versionNumber,
    WorkflowVersionStatus status,
    String triggerEntityType,
    String triggerEventType,
    Map<String, Object> triggerFilter,
    Instant createdAt,
    Instant updatedAt
) {
}
