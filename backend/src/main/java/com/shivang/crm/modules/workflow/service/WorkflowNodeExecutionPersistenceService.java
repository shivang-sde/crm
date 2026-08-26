package com.shivang.crm.modules.workflow.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeExecutionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowNodeExecutionPersistenceService {

    private final WorkflowNodeExecutionRepository workflowNodeExecutionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkflowNodeExecution ensureCommitted(WorkflowExecution execution, WorkflowNode node) {
        UUID tenantId = execution.getTenantId();
        UUID executionId = execution.getId();

        WorkflowNodeExecution nodeExecution = workflowNodeExecutionRepository
            .findByWorkflowExecutionIdAndWorkflowNodeIdAndDeletedFalse(executionId, node.getId())
            .orElseGet(() -> WorkflowNodeExecution.builder()
                .tenantId(tenantId)
                .workflowExecution(execution)
                .workflowNode(node)
                .nodeKey(node.getNodeKey())
                .nodeType(node.getNodeType())
                .inputContext(execution.getTriggerContext())
                .status(WorkflowNodeExecutionStatus.PENDING)
                .build());

        if (nodeExecution.getId() == null) {
            workflowNodeExecutionRepository.saveAndFlush(nodeExecution);
        }
        return nodeExecution;
    }
}
