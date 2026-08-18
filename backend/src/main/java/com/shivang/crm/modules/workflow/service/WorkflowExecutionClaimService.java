package com.shivang.crm.modules.workflow.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowExecutionClaimService {

    private final WorkflowExecutionRepository workflowExecutionRepository;

    @Transactional
    public boolean claim(UUID executionId) {
        return workflowExecutionRepository.claimPendingWithLease(
            executionId,
            WorkflowExecutionStatus.PENDING,
            WorkflowExecutionStatus.RUNNING
        ) == 1;
    }
}