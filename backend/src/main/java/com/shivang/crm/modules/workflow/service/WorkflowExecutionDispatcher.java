package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WorkflowExecutionDispatcher {

    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowExecutionClaimService workflowExecutionClaimService;
    private final WorkflowExecutionRuntimeService workflowExecutionRuntimeService;

    @Value("${app.workflow-runtime.batch-size:25}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.workflow-runtime.poll-delay-ms:5000}", initialDelayString = "${app.workflow-runtime.poll-delay-ms:5000}")
    public void dispatchPendingExecutions() {
        List<UUID> pendingIds = workflowExecutionRepository.findPendingIds(batchSize);
        for (UUID executionId : pendingIds) {
            if (workflowExecutionClaimService.claim(executionId)) {
                workflowExecutionRuntimeService.execute(executionId);
            }
        }
    }
}