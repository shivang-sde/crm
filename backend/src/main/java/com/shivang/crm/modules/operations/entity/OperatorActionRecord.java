package com.shivang.crm.modules.operations.entity;

import java.util.UUID;

import com.shivang.crm.modules.operations.enums.OperatorActionResult;
import com.shivang.crm.modules.operations.enums.OperatorActionType;
import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Audit record for one explicit operator recovery action.
 *
 * <p>Deliberately stores identifiers and short sanitised text only. There is no
 * column for a message body, an event payload, a credential, a token or an
 * encryption key, so this table cannot become a secret-leak vector.
 *
 * <p>{@code tenantId} is nullable: a platform operator (no tenant) records NULL.
 */
@Entity
@Table(
    name = "operator_message_actions",
    indexes = {
        @Index(name = "idx_operator_action_created", columnList = "created_at"),
        @Index(name = "idx_operator_action_tenant", columnList = "tenant_id, action_type, created_at"),
        @Index(name = "idx_operator_action_target", columnList = "action_type, target_id, created_at")
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OperatorActionRecord extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private OperatorActionType actionType;

    /**
     * Idempotency target. DLQ re-drive: the original AMQP message id. Outbox
     * requeue: {@code crm_event_outbox.event_id} rendered as text.
     */
    @Column(name = "target_id", nullable = false, length = 255)
    private String targetId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "operator_user_id", nullable = false)
    private UUID operatorUserId;

    @Column(name = "operator_role", length = 100)
    private String operatorRole;

    @Column(name = "request_id", length = 255)
    private String requestId;

    @Column(name = "original_exchange", length = 255)
    private String originalExchange;

    @Column(name = "original_routing_key", length = 255)
    private String originalRoutingKey;

    @Column(name = "destination_exchange", length = 255)
    private String destinationExchange;

    @Column(name = "destination_routing_key", length = 255)
    private String destinationRoutingKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 40)
    private OperatorActionResult result;

    /** Sanitised and length-capped by the caller. Never a raw payload. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
