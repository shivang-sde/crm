package com.shivang.crm.modules.workflow.dto;

import java.util.UUID;

import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;

public record WorkflowExecutionReplayResponse(
    UUID executionId,
    WorkflowExecutionStatus status,
    UUID replayedFromExecutionId
) {
}
