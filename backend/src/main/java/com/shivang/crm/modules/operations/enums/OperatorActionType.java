package com.shivang.crm.modules.operations.enums;

/**
 * The operator recovery actions this subsystem can record.
 *
 * <p>Neither value is ever driven by a scheduler, a retry loop or a listener.
 * A row only appears because a human called the API.
 */
public enum OperatorActionType {
    /** OPS-01: an operator re-drove one RabbitMQ dead letter. */
    DLQ_REDRIVE,
    /** OPS-02: an operator requeued one terminal outbox event. */
    OUTBOX_REQUEUE
}
