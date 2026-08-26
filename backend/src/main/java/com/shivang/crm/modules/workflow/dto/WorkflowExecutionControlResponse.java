package com.shivang.crm.modules.workflow.dto;

import java.util.UUID;

import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;

public record WorkflowExecutionControlResponse(
    UUID executionId,
    WorkflowExecutionStatus status
) {
}
