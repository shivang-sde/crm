package com.shivang.crm.modules.workflow.service;

import java.util.Map;

public interface WorkflowActionExecutor {

    String actionType();

    WorkflowActionExecutionResult execute(WorkflowExecutionContext context, Map<String, Object> configuration);
}