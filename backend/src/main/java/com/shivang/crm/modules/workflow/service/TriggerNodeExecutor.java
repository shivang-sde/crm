package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.workflow.entity.WorkflowEdge;
import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;

@Component
public class TriggerNodeExecutor implements WorkflowNodeExecutor, WorkflowNodeExecutorRegistrationProvider {

    @Override
    public WorkflowNodeExecutionResult execute(
        WorkflowExecution execution,
        WorkflowNode node,
        List<WorkflowEdge> outgoingEdges,
        WorkflowExecutionContext context
    ) {
        if (outgoingEdges.size() != 1) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_BRANCH_NOT_SUPPORTED",
                "TRIGGER node must have exactly one outgoing edge"
            );
        }
        return new WorkflowNodeExecutionResult(
            com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.COMPLETED,
            Map.of("status", "completed"),
            List.of(outgoingEdges.get(0).getId()),
            null,
            null
        );
    }

    @Override
    public WorkflowNodeExecutorRegistration registration() {
        return new WorkflowNodeExecutorRegistration(WorkflowNodeType.TRIGGER, this);
    }
}