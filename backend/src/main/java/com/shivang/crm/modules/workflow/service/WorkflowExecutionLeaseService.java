package com.shivang.crm.modules.workflow.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowExecutionLeaseService {

    private final WorkflowExecutionRepository executionRepository;
    private final com.shivang.crm.modules.workflow.repository.WorkflowNodeExecutionRepository nodeRepository;

    @Transactional
    public boolean heartbeatExecution(UUID executionId) {
        return executionRepository.heartbeatRunning(executionId) == 1;
    }

    @Transactional
    public boolean heartbeatNode(UUID nodeExecutionId) {
        return nodeRepository.heartbeatRunning(nodeExecutionId) == 1;
    }

    @Transactional
    public boolean recoverExecution(UUID executionId, Instant cutoff) {
        return executionRepository.recoverStaleRunning(executionId, cutoff) == 1;
    }

    @Transactional
    public boolean recoverNode(UUID nodeExecutionId, Instant cutoff) {
        return nodeRepository.recoverStaleRunning(nodeExecutionId, cutoff) == 1;
    }
}