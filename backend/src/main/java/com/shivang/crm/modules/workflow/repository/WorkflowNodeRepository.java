package com.shivang.crm.modules.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.workflow.entity.WorkflowNode;

@Repository
public interface WorkflowNodeRepository extends JpaRepository<WorkflowNode, UUID> {

    List<WorkflowNode> findByTenantIdAndWorkflowVersionIdAndDeletedFalse(UUID tenantId, UUID workflowVersionId);

    Optional<WorkflowNode> findByIdAndTenantIdAndWorkflowVersionIdAndDeletedFalse(UUID id, UUID tenantId, UUID workflowVersionId);

    Optional<WorkflowNode> findByTenantIdAndWorkflowVersionIdAndNodeKeyAndDeletedFalse(UUID tenantId, UUID workflowVersionId, String nodeKey);

    List<WorkflowNode> findByTenantIdAndDeletedFalse(UUID tenantId);
}