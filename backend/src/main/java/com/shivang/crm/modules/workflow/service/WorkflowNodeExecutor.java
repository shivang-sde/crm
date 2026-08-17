package com.shivang.crm.modules.workflow.service;

import java.util.List;

import com.shivang.crm.modules.workflow.entity.WorkflowEdge;
import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;

public interface WorkflowNodeExecutor {

    WorkflowNodeExecutionResult execute(
        WorkflowExecution execution,
        WorkflowNode node,
        List<WorkflowEdge> outgoingEdges,
        WorkflowExecutionContext context
    );
}