package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowExecutionReplayService {

    private final WorkflowExecutionRepository workflowExecutionRepository;

    @Transactional
    public WorkflowExecution replay(UUID tenantId, UUID executionId) {
        WorkflowExecution original = workflowExecutionRepository.findByIdAndTenantIdAndDeletedFalse(executionId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Workflow execution not found"));

        if (!isReplayable(original.getStatus())) {
            throw new BusinessException(
                "WORKFLOW_EXECUTION_NOT_REPLAYABLE",
                "Only COMPLETED or FAILED workflow executions can be replayed"
            );
        }

        Map<String, Object> triggerContext = original.getTriggerContext() == null
            ? Map.of()
            : new LinkedHashMap<>(original.getTriggerContext());

        // New execution/node-execution identities so WORKFLOW-7.3/7.4 retry and idempotency apply independently.
        WorkflowExecution replay = WorkflowExecution.builder()
            .tenantId(original.getTenantId())
            .workflow(original.getWorkflow())
            .workflowVersion(original.getWorkflowVersion())
            .triggerEventId(UUID.randomUUID())
            .entityType(original.getEntityType())
            .entityId(original.getEntityId())
            .eventType(original.getEventType())
            .actorId(original.getActorId())
            .actorType(original.getActorType())
            .status(WorkflowExecutionStatus.PENDING)
            .triggerContext(triggerContext)
            .replayedFromExecutionId(original.getId())
            // Replay is a fresh root execution: no inherited causal chain.
            .causedByExecutionId(null)
            .causedByEventId(null)
            .chainDepth(0)
            .build();

        return workflowExecutionRepository.save(replay);
    }

    private boolean isReplayable(WorkflowExecutionStatus status) {
        return status == WorkflowExecutionStatus.COMPLETED || status == WorkflowExecutionStatus.FAILED;
    }
}
