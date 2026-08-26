package com.shivang.crm.modules.workflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;

public record WorkflowExecutionDetailResponse(
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
    Integer attemptCount,
    Instant lastHeartbeatAt,
    String lastErrorCode,
    String lastErrorMessage,
    UUID replayedFromExecutionId,
    UUID causedByExecutionId,
    UUID causedByEventId,
    Integer chainDepth,
    List<WorkflowNodeExecutionResponse> nodeExecutions
) {
}
