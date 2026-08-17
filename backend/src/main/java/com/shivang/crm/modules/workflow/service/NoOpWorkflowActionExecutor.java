package com.shivang.crm.modules.workflow.service;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class NoOpWorkflowActionExecutor implements WorkflowActionExecutor {

    @Override
    public String actionType() {
        return "NO_OP";
    }

    @Override
    public WorkflowActionExecutionResult execute(WorkflowExecutionContext context, Map<String, Object> configuration) {
        String message = configuration == null || configuration.get("message") == null
            ? ""
            : String.valueOf(configuration.get("message"));
        return WorkflowActionExecutionResult.completed(Map.of(
            "success", true,
            "message", message
        ));
    }
}