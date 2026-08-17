package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.workflow.entity.WorkflowEdge;
import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;

@Component
public class EndNodeExecutor implements WorkflowNodeExecutor, WorkflowNodeExecutorRegistrationProvider {

    @Override
    public WorkflowNodeExecutionResult execute(
        WorkflowExecution execution,
        WorkflowNode node,
        List<WorkflowEdge> outgoingEdges,
        WorkflowExecutionContext context
    ) {
        if (!outgoingEdges.isEmpty()) {
            throw new WorkflowRuntimeException("WORKFLOW_EDGE_INVALID", "END node cannot have outgoing edges");
        }
        return WorkflowNodeExecutionResult.completed(Map.of("status", "completed"));
    }

    @Override
    public WorkflowNodeExecutorRegistration registration() {
        return new WorkflowNodeExecutorRegistration(WorkflowNodeType.END, this);
    }
}