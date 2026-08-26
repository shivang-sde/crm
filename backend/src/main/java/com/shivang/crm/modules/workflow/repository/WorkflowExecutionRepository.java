package com.shivang.crm.modules.workflow.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, UUID> {

    @org.springframework.data.jpa.repository.Query(value = "SELECT id FROM workflow_executions WHERE status = 'PENDING' AND deleted = false AND (next_attempt_at IS NULL OR next_attempt_at <= NOW()) ORDER BY created_at ASC LIMIT :batchSize", nativeQuery = true)
    List<UUID> findPendingIds(@Param("batchSize") int batchSize);

    @Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE WorkflowExecution execution
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
        @Param("pendingStatus") WorkflowExecutionStatus pendingStatus,
        @Param("runningStatus") WorkflowExecutionStatus runningStatus
    );

    @Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE WorkflowExecution execution SET execution.lastHeartbeatAt = CURRENT_TIMESTAMP WHERE execution.id = :id AND execution.status = com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus.RUNNING AND execution.deleted = false")
    int heartbeatRunning(@Param("id") UUID id);

    @Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE WorkflowExecution execution SET execution.status = com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus.PENDING, execution.nextAttemptAt = null WHERE execution.id = :id AND execution.status = com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus.RUNNING AND execution.lastHeartbeatAt < :cutoff AND execution.deleted = false")
    int recoverStaleRunning(@Param("id") UUID id, @Param("cutoff") Instant cutoff);

    @org.springframework.data.jpa.repository.Query(value = "SELECT id FROM workflow_executions WHERE status = 'RUNNING' AND last_heartbeat_at < :cutoff AND deleted = false ORDER BY last_heartbeat_at ASC LIMIT :batchSize", nativeQuery = true)
    List<UUID> findStaleRunningIds(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    @org.springframework.data.jpa.repository.Query("SELECT execution FROM WorkflowExecution execution JOIN FETCH execution.workflowVersion version JOIN FETCH version.workflow workflow WHERE execution.id = :id AND execution.deleted = false")
    java.util.Optional<WorkflowExecution> findRuntimeExecution(@Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query("SELECT execution FROM WorkflowExecution execution JOIN FETCH execution.workflowVersion version JOIN FETCH version.workflow workflow WHERE execution.id = :id AND execution.tenantId = :tenantId AND execution.deleted = false")
    java.util.Optional<WorkflowExecution> findByIdAndTenantIdAndDeletedFalse(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    // Operator visibility list: tenant-scoped, optionally filtered, always bounded by Pageable.
    @org.springframework.data.jpa.repository.Query("""
        SELECT execution FROM WorkflowExecution execution
        WHERE execution.tenantId = :tenantId
          AND execution.deleted = false
          AND (:status IS NULL OR execution.status = :status)
          AND (:workflowId IS NULL OR execution.workflow.id = :workflowId)
          AND (:entityType IS NULL OR execution.entityType = :entityType)
          AND (:entityId IS NULL OR execution.entityId = :entityId)
        ORDER BY execution.createdAt DESC
        """)
    Page<WorkflowExecution> findByTenantIdWithFilters(
        @Param("tenantId") UUID tenantId,
        @Param("status") WorkflowExecutionStatus status,
        @Param("workflowId") UUID workflowId,
        @Param("entityType") String entityType,
        @Param("entityId") UUID entityId,
        Pageable pageable
    );

    @Modifying
    @org.springframework.data.jpa.repository.Query(value = """
        INSERT INTO workflow_executions (
            id, tenant_id, workflow_id, workflow_version_id, trigger_event_id,
            entity_type, entity_id, event_type, actor_id, actor_type, status, trigger_context,
            caused_by_execution_id, caused_by_event_id, chain_depth,
            created_at, updated_at, deleted
        ) VALUES (
            gen_random_uuid(), :tenantId, :workflowId, :workflowVersionId, :triggerEventId,
            :entityType, :entityId, :eventType, :actorId,
            CAST(:actorType AS VARCHAR),
            CAST(:status AS VARCHAR),
            CAST(:triggerContext AS jsonb),
            :causedByExecutionId, :causedByEventId, :chainDepth,
            NOW(), NOW(), FALSE
        ) ON CONFLICT (workflow_version_id, trigger_event_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("tenantId") UUID tenantId,
        @Param("workflowId") UUID workflowId,
        @Param("workflowVersionId") UUID workflowVersionId,
        @Param("triggerEventId") UUID triggerEventId,
        @Param("entityType") String entityType,
        @Param("entityId") UUID entityId,
        @Param("eventType") String eventType,
        @Param("actorId") UUID actorId,
        @Param("actorType") String actorType,
        @Param("status") String status,
        @Param("triggerContext") String triggerContext,
        @Param("causedByExecutionId") UUID causedByExecutionId,
        @Param("causedByEventId") UUID causedByEventId,
        @Param("chainDepth") Integer chainDepth
    );

    /**
     * Observable rejection row for executions suppressed by loop safety
     * (max causal depth exceeded or self-trigger suppression). Same event
     * deduplication applies: only one row per (version, event).
     */
    @Modifying
    @org.springframework.data.jpa.repository.Query(value = """
        INSERT INTO workflow_executions (
            id, tenant_id, workflow_id, workflow_version_id, trigger_event_id,
            entity_type, entity_id, event_type, actor_id, actor_type, status, trigger_context,
            caused_by_execution_id, caused_by_event_id, chain_depth,
            error_code, error_message, completed_at,
            created_at, updated_at, deleted
        ) VALUES (
            gen_random_uuid(), :tenantId, :workflowId, :workflowVersionId, :triggerEventId,
            :entityType, :entityId, :eventType, :actorId,
            CAST(:actorType AS VARCHAR),
            'FAILED',
            CAST(:triggerContext AS jsonb),
            :causedByExecutionId, :causedByEventId, :chainDepth,
            CAST(:errorCode AS VARCHAR), :errorMessage, NOW(),
            NOW(), NOW(), FALSE
        ) ON CONFLICT (workflow_version_id, trigger_event_id) DO NOTHING
        """, nativeQuery = true)
    int insertRejectedIfAbsent(
        @Param("tenantId") UUID tenantId,
        @Param("workflowId") UUID workflowId,
        @Param("workflowVersionId") UUID workflowVersionId,
        @Param("triggerEventId") UUID triggerEventId,
        @Param("entityType") String entityType,
        @Param("entityId") UUID entityId,
        @Param("eventType") String eventType,
        @Param("actorId") UUID actorId,
        @Param("actorType") String actorType,
        @Param("triggerContext") String triggerContext,
        @Param("causedByExecutionId") UUID causedByExecutionId,
        @Param("causedByEventId") UUID causedByEventId,
        @Param("chainDepth") Integer chainDepth,
        @Param("errorCode") String errorCode,
        @Param("errorMessage") String errorMessage
    );
}