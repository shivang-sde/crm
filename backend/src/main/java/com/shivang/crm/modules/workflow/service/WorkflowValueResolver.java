package com.shivang.crm.modules.workflow.service;

public interface WorkflowValueResolver {

    WorkflowResolvedValue resolve(WorkflowExecutionContext context, String fieldPath);
}