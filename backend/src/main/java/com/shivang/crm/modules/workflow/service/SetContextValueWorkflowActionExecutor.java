package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;

@Component
public class SetContextValueWorkflowActionExecutor implements WorkflowActionExecutor {

    @Override
    public String actionType() {
        return "SET_CONTEXT_VALUE";
    }

    @Override
    public WorkflowActionExecutionResult execute(WorkflowExecutionContext context, Map<String, Object> configuration) {
        if (configuration == null || configuration.get("key") == null || String.valueOf(configuration.get("key")).isBlank()) {
            throw new WorkflowRuntimeException("WORKFLOW_ACTION_INVALID_CONFIG", "SET_CONTEXT_VALUE requires key");
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", true);
        output.put("key", String.valueOf(configuration.get("key")));
        output.put("value", configuration.get("value"));
        return WorkflowActionExecutionResult.completed(output);
    }
}