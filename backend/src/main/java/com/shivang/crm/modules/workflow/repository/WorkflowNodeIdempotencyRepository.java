package com.shivang.crm.modules.workflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.workflow.entity.WorkflowNodeIdempotency;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeIdempotencyStatus;

@Repository
public interface WorkflowNodeIdempotencyRepository extends JpaRepository<WorkflowNodeIdempotency, UUID> {

    Optional<WorkflowNodeIdempotency> findByTenantIdAndWorkflowExecutionIdAndWorkflowNodeExecutionId(
        UUID tenantId, UUID workflowExecutionId, UUID workflowNodeExecutionId
    );

    @Modifying
    @Query(value = """
        INSERT INTO workflow_node_idempotency
            (tenant_id, workflow_execution_id, workflow_node_execution_id, idempotency_key, status, created_at, updated_at)
        VALUES (:tenantId, :executionId, :nodeExecutionId, :key, 'IN_PROGRESS', NOW(), NOW())
        ON CONFLICT (idempotency_key) DO NOTHING
        """, nativeQuery = true)
    int claimIfAbsent(
        @Param("tenantId") UUID tenantId,
        @Param("executionId") UUID executionId,
        @Param("nodeExecutionId") UUID nodeExecutionId,
        @Param("key") String key
    );

    // Atomic conditional transition so concurrent retry claims cannot both reclaim the same FAILED record.
    @Modifying
    @Query(value = """
        UPDATE workflow_node_idempotency
        SET status = 'IN_PROGRESS', updated_at = NOW()
        WHERE id = :id AND status = 'FAILED'
        """, nativeQuery = true)
    int reclaimFailed(@Param("id") UUID id);
}