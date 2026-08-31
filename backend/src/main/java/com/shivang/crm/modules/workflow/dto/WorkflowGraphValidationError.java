package com.shivang.crm.modules.workflow.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowGraphValidationError(
    String code,
    String message,
    UUID nodeId,
    String nodeKey,
    UUID edgeId
) {
    public WorkflowGraphValidationError(String code, String message) {
        this(code, message, null, null, null);
    }
}