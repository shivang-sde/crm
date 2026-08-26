package com.shivang.crm.modules.workflow.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "workflow_executions")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecution extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_version_id", nullable = false)
    private WorkflowVersion workflowVersion;

    @Column(name = "trigger_event_id", nullable = false)
    private UUID triggerEventId;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 30)
    private WorkflowActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WorkflowExecutionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_context", columnDefinition = "jsonb")
    private Map<String, Object> triggerContext;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "replayed_from_execution_id")
    private UUID replayedFromExecutionId;

    // BE-WF-8 causal lineage. Root executions (created directly from domain
    // events) have a null causer and chainDepth 0; executions caused by another
    // execution's actions inherit lineage so cross-workflow loops can be bounded.
    @Column(name = "caused_by_execution_id")
    private UUID causedByExecutionId;

    @Column(name = "caused_by_event_id")
    private UUID causedByEventId;

    @Column(name = "chain_depth", nullable = false)
    @Builder.Default
    private Integer chainDepth = 0;
}