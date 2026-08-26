package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.dto.WorkflowExecutionControlResponse;
import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeExecutionRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runtime control operations for persisted workflow executions.
 *
 * Retry semantics: a FAILED execution is returned to PENDING so the dispatcher
 * re-runs it. The graph walk short-circuits COMPLETED node executions from
 * their persisted output (no repeated side effects), and durable node
 * idempotency independently protects external actions. Only the non-completed
 * portion of the graph executes again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutionControlService {

    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowNodeExecutionRepository workflowNodeExecutionRepository;

    @Transactional
    public WorkflowExecutionControlResponse retryExecution(UUID tenantId, UUID executionId) {
        WorkflowExecution execution = workflowExecutionRepository
            .findByIdAndTenantIdAndDeletedFalse(executionId, tenantId)
            .orElseThrow(() -> new BusinessException(
                "WORKFLOW_EXECUTION_NOT_FOUND", "Workflow execution not found"));

        if (execution.getStatus() != WorkflowExecutionStatus.FAILED) {
            throw new BusinessException(
                "WORKFLOW_EXECUTION_NOT_RETRYABLE", "Only FAILED workflow executions can be retried");
        }

        // Loop-safety rejections are observability records, not resumable work:
        // retrying one would bypass the causal-depth / self-trigger protection.
        if (WorkflowTriggerService.ERROR_MAX_CHAIN_DEPTH.equals(execution.getErrorCode())
            || WorkflowTriggerService.ERROR_SELF_TRIGGER_SUPPRESSED.equals(execution.getErrorCode())) {
            throw new BusinessException(
                "WORKFLOW_EXECUTION_NOT_RETRYABLE",
                "Loop-suppressed executions cannot be retried; trigger a new domain event instead");
        }

        List<WorkflowNodeExecution> nodeExecutions = workflowNodeExecutionRepository
            .findByTenantIdAndWorkflowExecutionIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, executionId);

        for (WorkflowNodeExecution node : nodeExecutions) {
            // Completed/skipped work is preserved so the runtime skips it safely.
            if (node.getStatus() == WorkflowNodeExecutionStatus.COMPLETED
                || node.getStatus() == WorkflowNodeExecutionStatus.SKIPPED) {
                continue;
            }
            node.setStatus(WorkflowNodeExecutionStatus.PENDING);
            node.setCompletedAt(null);
            node.setNextAttemptAt(null);
            node.setErrorCode(null);
            node.setErrorMessage(null);
            // lastErrorCode/lastErrorMessage retained as retry history.
        }

        execution.setStatus(WorkflowExecutionStatus.PENDING);
        execution.setCompletedAt(null);
        execution.setNextAttemptAt(null);
        execution.setErrorCode(null);
        execution.setErrorMessage(null);
        execution.setLastHeartbeatAt(java.time.Instant.now());
        // lastErrorCode/lastErrorMessage retained until the new attempt finishes.

        workflowExecutionRepository.save(execution);

        log.info("Workflow execution {} retry scheduled tenant={} workflow={}",
            executionId, tenantId, execution.getWorkflow() != null
                ? execution.getWorkflow().getId()
                : null);

        return new WorkflowExecutionControlResponse(execution.getId(), execution.getStatus());
    }
}
