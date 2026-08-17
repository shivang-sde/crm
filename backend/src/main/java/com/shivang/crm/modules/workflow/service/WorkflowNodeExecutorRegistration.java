package com.shivang.crm.modules.workflow.service;

import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;

public record WorkflowNodeExecutorRegistration(WorkflowNodeType nodeType, WorkflowNodeExecutor executor) {
}