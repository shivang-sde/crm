package com.shivang.crm.modules.workflow.dto;

import java.time.Instant;
import java.util.UUID;

import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;

public record WorkflowExecutionSummaryResponse(
    UUID id,
    UUID workflowId,
    UUID workflowVersionId,
    String entityType,
    UUID entityId,
    String eventType,
    WorkflowExecutionStatus status,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt,
    String errorCode,
    String errorMessage
) {
}
