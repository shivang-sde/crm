package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SetContextValueWorkflowActionExecutor implements WorkflowActionExecutor {

    private final WorkflowValueResolver valueResolver;

    @Override
    public String actionType() {
        return "SET_CONTEXT_VALUE";
    }

    @Override
    public WorkflowActionExecutionResult execute(WorkflowExecutionContext context, Map<String, Object> configuration) {
        if (configuration == null || configuration.get("key") == null || String.valueOf(configuration.get("key")).isBlank()) {
            throw new WorkflowRuntimeException("WORKFLOW_ACTION_INVALID_CONFIG", "SET_CONTEXT_VALUE requires key");
        }
        Object rawValue = configuration.get("value");
        Object resolvedValue = rawValue;
        if (rawValue instanceof String text && text.trim().startsWith("{{") && text.trim().endsWith("}}")) {
            String path = text.trim().substring(2, text.trim().length() - 2).trim();
            WorkflowResolvedValue result = valueResolver.resolve(context, path);
            if (result.found()) resolvedValue = result.value();
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", true);
        output.put("key", String.valueOf(configuration.get("key")));
        output.put("value", resolvedValue);
        return WorkflowActionExecutionResult.completed(output);
    }
}