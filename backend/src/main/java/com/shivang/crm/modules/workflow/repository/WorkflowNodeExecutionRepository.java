package com.shivang.crm.modules.workflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecution;

@Repository
public interface WorkflowNodeExecutionRepository extends JpaRepository<WorkflowNodeExecution, UUID> {

    Optional<WorkflowNodeExecution> findByWorkflowExecutionIdAndWorkflowNodeIdAndDeletedFalse(UUID workflowExecutionId, UUID workflowNodeId);
}