package com.shivang.crm.modules.workflow.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeExecutionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutionRecoveryScheduler {

    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowNodeExecutionRepository workflowNodeExecutionRepository;
    private final WorkflowExecutionLeaseService workflowExecutionLeaseService;

    @Value("${app.workflow-runtime.execution-recovery.enabled:true}")
    private boolean enabled;

    @Value("${app.workflow-runtime.execution-recovery.stale-after-seconds:300}")
    private long staleAfterSeconds;

    @Value("${app.workflow-runtime.execution-recovery.batch-size:100}")
    private int batchSize;

    @Scheduled(
        fixedDelayString = "${app.workflow-runtime.execution-recovery.fixed-delay-ms:60000}",
        initialDelayString = "${app.workflow-runtime.execution-recovery.fixed-delay-ms:60000}"
    )
    public void recoverStaleExecutions() {
        if (!enabled) {
            return;
        }

        Instant cutoff = Instant.now().minusSeconds(staleAfterSeconds);
        int executionsFound = 0;
        int executionsRecovered = 0;
        int nodesFound = 0;
        int nodesRecovered = 0;

        List<UUID> executionIds = workflowExecutionRepository.findStaleRunningIds(cutoff, batchSize);
        executionsFound = executionIds.size();
        for (UUID executionId : executionIds) {
            try {
                if (workflowExecutionLeaseService.recoverExecution(executionId, cutoff)) {
                    executionsRecovered++;
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to recover stale workflow execution {}: {}", executionId, ex.getMessage());
            }
        }

        List<UUID> nodeExecutionIds = workflowNodeExecutionRepository.findStaleRunningIds(cutoff, batchSize);
        nodesFound = nodeExecutionIds.size();
        for (UUID nodeExecutionId : nodeExecutionIds) {
            try {
                if (workflowExecutionLeaseService.recoverNode(nodeExecutionId, cutoff)) {
                    nodesRecovered++;
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to recover stale workflow node execution {}: {}", nodeExecutionId, ex.getMessage());
            }
        }

        log.info(
            "Workflow stale recovery completed: executions={}/{} nodes={}/{}, cutoff={}",
            executionsRecovered,
            executionsFound,
            nodesRecovered,
            nodesFound,
            cutoff
        );
    }
}