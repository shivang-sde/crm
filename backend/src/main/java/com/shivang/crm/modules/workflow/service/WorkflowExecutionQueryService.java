package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecution;
import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeExecutionRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowExecutionQueryService {

    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowNodeExecutionRepository workflowNodeExecutionRepository;

    @Transactional(readOnly = true)
    public Page<WorkflowExecution> list(
        UUID tenantId,
        WorkflowExecutionStatus status,
        UUID workflowId,
        String entityType,
        UUID entityId,
        int page,
        int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return workflowExecutionRepository.findByTenantIdWithFilters(tenantId, status, workflowId, entityType, entityId, pageable);
    }

    @Transactional(readOnly = true)
    public WorkflowExecution getExecution(UUID tenantId, UUID executionId) {
        return workflowExecutionRepository.findByIdAndTenantIdAndDeletedFalse(executionId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Workflow execution not found"));
    }

    @Transactional(readOnly = true)
    public List<WorkflowNodeExecution> getNodeExecutions(UUID tenantId, UUID executionId) {
        return workflowNodeExecutionRepository.findByTenantIdAndWorkflowExecutionIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, executionId);
    }
}
