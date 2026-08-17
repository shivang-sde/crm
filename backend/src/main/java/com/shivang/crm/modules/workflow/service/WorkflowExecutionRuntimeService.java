package com.shivang.crm.modules.workflow.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutionRuntimeService {

    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowGraphRuntimeService workflowGraphRuntimeService;

    @Transactional
    public void execute(UUID executionId) {
        WorkflowExecution execution = workflowExecutionRepository.findRuntimeExecution(executionId).orElse(null);
        if (execution == null || execution.getStatus() != WorkflowExecutionStatus.RUNNING) {
            return;
        }

        try {
            validateExecutionOwnership(execution);
            validateExecutionIdentity(execution);
            workflowGraphRuntimeService.execute(execution);
            execution.setStatus(WorkflowExecutionStatus.COMPLETED);
            execution.setCompletedAt(Instant.now());
            execution.setErrorCode(null);
            execution.setErrorMessage(null);
        } catch (WorkflowRuntimeException ex) {
            markFailed(execution, ex.getErrorCode(), ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            markFailed(execution, "WORKFLOW_RUNTIME_FAILED", "Workflow runtime failed", ex);
        }

        workflowExecutionRepository.save(execution);
    }

    private void validateExecutionIdentity(WorkflowExecution execution) {
        if (execution.getActorType() == null || execution.getActorId() == null) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_EXECUTION_IDENTITY_MISSING",
                "Workflow execution has no persisted actor identity"
            );
        }
    }

    private void validateExecutionOwnership(WorkflowExecution execution) {
        if (execution.getWorkflowVersion() == null || execution.getWorkflowVersion().getWorkflow() == null
            || !execution.getTenantId().equals(execution.getWorkflowVersion().getTenantId())
            || !execution.getTenantId().equals(execution.getWorkflowVersion().getWorkflow().getTenantId())) {
            throw new WorkflowRuntimeException("WORKFLOW_VERSION_NOT_FOUND", "Workflow version is not owned by the execution tenant");
        }
    }

    private void markFailed(WorkflowExecution execution, String errorCode, String message, RuntimeException cause) {
        log.error("Workflow execution {} failed with {}", execution.getId(), errorCode, cause);
        execution.setStatus(WorkflowExecutionStatus.FAILED);
        execution.setCompletedAt(Instant.now());
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(message);
    }
}