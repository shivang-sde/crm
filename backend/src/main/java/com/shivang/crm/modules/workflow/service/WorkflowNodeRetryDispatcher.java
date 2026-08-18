package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shivang.crm.modules.workflow.repository.WorkflowNodeExecutionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WorkflowNodeRetryDispatcher {

    private final WorkflowNodeExecutionRepository nodeRepository;
    private final WorkflowExecutionRuntimeService runtimeService;

    @Value("${app.workflow-runtime.retry.enabled:false}")
    private boolean enabled;

    @Value("${app.workflow-runtime.retry.batch-size:25}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.workflow-runtime.retry.poll-delay-ms:5000}", initialDelayString = "${app.workflow-runtime.retry.poll-delay-ms:5000}")
    public void dispatchDueNodeRetries() {
        if (!enabled) {
            return;
        }
        List<UUID> nodeIds = nodeRepository.findDuePendingIds(batchSize);
        for (UUID nodeId : nodeIds) {
            nodeRepository.findRuntimeNodeExecution(nodeId)
                .ifPresent(node -> runtimeService.execute(node.getWorkflowExecution().getId()));
        }
    }
}