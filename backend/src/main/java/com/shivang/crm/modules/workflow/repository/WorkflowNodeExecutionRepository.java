package com.shivang.crm.modules.workflow.repository;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus;

@Repository
public interface WorkflowNodeExecutionRepository extends JpaRepository<WorkflowNodeExecution, UUID> {

    Optional<WorkflowNodeExecution> findByWorkflowExecutionIdAndWorkflowNodeIdAndDeletedFalse(UUID workflowExecutionId, UUID workflowNodeId);

    @Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE WorkflowNodeExecution execution
        SET execution.status = :runningStatus,
            execution.startedAt = CASE WHEN execution.attemptCount = 0 THEN CURRENT_TIMESTAMP ELSE execution.startedAt END,
            execution.lastHeartbeatAt = CURRENT_TIMESTAMP,
            execution.attemptCount = execution.attemptCount + 1,
            execution.nextAttemptAt = null
        WHERE execution.id = :id
          AND execution.status = :pendingStatus
          AND execution.deleted = false
        """)
    int claimPendingWithLease(
        @Param("id") UUID id,
        @Param("pendingStatus") WorkflowNodeExecutionStatus pendingStatus,
        @Param("runningStatus") WorkflowNodeExecutionStatus runningStatus
    );

    @Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE WorkflowNodeExecution execution SET execution.lastHeartbeatAt = CURRENT_TIMESTAMP WHERE execution.id = :id AND execution.status = com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.RUNNING AND execution.deleted = false")
    int heartbeatRunning(@Param("id") UUID id);

    @Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE WorkflowNodeExecution execution SET execution.status = com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.PENDING, execution.nextAttemptAt = null WHERE execution.id = :id AND execution.status = com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.RUNNING AND execution.lastHeartbeatAt < :cutoff AND execution.deleted = false")
    int recoverStaleRunning(@Param("id") UUID id, @Param("cutoff") Instant cutoff);

    @org.springframework.data.jpa.repository.Query(value = "SELECT id FROM workflow_node_executions WHERE status = 'RUNNING' AND last_heartbeat_at < :cutoff AND deleted = false ORDER BY last_heartbeat_at ASC LIMIT :batchSize", nativeQuery = true)
    java.util.List<UUID> findStaleRunningIds(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    @org.springframework.data.jpa.repository.Query(value = "SELECT id FROM workflow_node_executions WHERE status = 'PENDING' AND next_attempt_at IS NOT NULL AND next_attempt_at <= now() AND deleted = false ORDER BY next_attempt_at ASC LIMIT :batchSize", nativeQuery = true)
    java.util.List<UUID> findDuePendingIds(@Param("batchSize") int batchSize);

    @org.springframework.data.jpa.repository.Query("SELECT execution FROM WorkflowNodeExecution execution JOIN FETCH execution.workflowExecution workflowExecution JOIN FETCH execution.workflowNode node WHERE execution.id = :id AND execution.deleted = false")
    java.util.Optional<WorkflowNodeExecution> findRuntimeNodeExecution(@Param("id") UUID id);
}