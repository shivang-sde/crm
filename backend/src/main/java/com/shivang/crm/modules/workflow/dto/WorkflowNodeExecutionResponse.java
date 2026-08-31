package com.shivang.crm.modules.workflow.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;

public record WorkflowNodeExecutionResponse(
    UUID id,
    UUID nodeId,
    String nodeKey,
    WorkflowNodeType nodeType,
    WorkflowNodeExecutionStatus status,
    Integer attemptCount,
    Instant startedAt,
    Instant completedAt,
    Instant nextAttemptAt,
    Map<String, Object> inputContext,
    Map<String, Object> outputContext,
    String lastErrorCode,
    String lastErrorMessage
) {
}
