package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class WorkflowActionExecutorRegistry {

    private final Map<String, WorkflowActionExecutor> executors;

    public WorkflowActionExecutorRegistry(List<WorkflowActionExecutor> executors) {
        this.executors = executors.stream().collect(Collectors.toUnmodifiableMap(
            executor -> executor.actionType().toUpperCase(),
            Function.identity(),
            (first, second) -> first
        ));
    }

    public WorkflowActionExecutor get(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            throw new WorkflowRuntimeException("WORKFLOW_ACTION_INVALID_CONFIG", "Action type is required");
        }
        WorkflowActionExecutor executor = executors.get(actionType.trim().toUpperCase());
        if (executor == null) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_ACTION_TYPE_NOT_SUPPORTED",
                "Action type is not supported: " + actionType
            );
        }
        return executor;
    }
}