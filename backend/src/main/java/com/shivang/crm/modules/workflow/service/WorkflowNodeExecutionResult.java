package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus;

public record WorkflowNodeExecutionResult(
    WorkflowNodeExecutionStatus status,
    Map<String, Object> outputContext,
    List<UUID> selectedEdgeIds,
    String errorCode,
    String errorMessage
) {

    public static WorkflowNodeExecutionResult completed(Map<String, Object> outputContext) {
        return new WorkflowNodeExecutionResult(
            WorkflowNodeExecutionStatus.COMPLETED,
            outputContext == null ? Map.of() : outputContext,
            List.of(),
            null,
            null
        );
    }
}