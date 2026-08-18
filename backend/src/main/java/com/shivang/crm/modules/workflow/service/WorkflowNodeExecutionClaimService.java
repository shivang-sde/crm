package com.shivang.crm.modules.workflow.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeExecutionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowNodeExecutionClaimService {

    private final WorkflowNodeExecutionRepository repository;

    @Transactional
    public boolean claim(UUID nodeExecutionId) {
        return repository.claimPendingWithLease(
            nodeExecutionId,
            WorkflowNodeExecutionStatus.PENDING,
            WorkflowNodeExecutionStatus.RUNNING
        ) == 1;
    }

    @Transactional
    public boolean heartbeat(UUID nodeExecutionId) {
        return repository.heartbeatRunning(nodeExecutionId) == 1;
    }
}