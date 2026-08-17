package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.LinkedHashMap;

public record WorkflowActionExecutionResult(
    boolean success,
    Map<String, Object> output,
    String errorCode,
    String errorMessage
) {

    public static WorkflowActionExecutionResult completed(Map<String, Object> output) {
        return new WorkflowActionExecutionResult(
            true,
            output == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(output)),
            null,
            null
        );
    }
}