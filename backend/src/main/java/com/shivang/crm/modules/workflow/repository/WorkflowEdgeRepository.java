package com.shivang.crm.modules.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.workflow.entity.WorkflowEdge;

@Repository
public interface WorkflowEdgeRepository extends JpaRepository<WorkflowEdge, UUID> {

    List<WorkflowEdge> findByTenantIdAndWorkflowVersionIdAndDeletedFalse(UUID tenantId, UUID workflowVersionId);

    Optional<WorkflowEdge> findByIdAndTenantIdAndWorkflowVersionIdAndDeletedFalse(UUID id, UUID tenantId, UUID workflowVersionId);
}