package com.shivang.crm.modules.operations.dto;

import java.time.Instant;
import java.util.UUID;

import com.shivang.crm.shared.event.OutboxEventStatus;

import lombok.Builder;
import lombok.Data;

/**
 * Operator-facing view of one terminal (DEAD) outbox event.
 *
 * <p>The event {@code payload} is deliberately not mapped. The column holds the
 * serialised canonical event including its free-form metadata map, which is not
 * needed for triage and is not somewhere sensitive data should flow. The
 * {@code payloadAvailable} flag tells the operator that a body exists without
 * shipping it.
 */
@Data
@Builder
public class OutboxDeadEventView {

    private UUID eventId;
    private String eventName;
    private String entityType;
    private String eventType;
    private UUID entityId;

    /** The persisted ownership column. Never inferred from the payload. */
    private UUID tenantId;

    private OutboxEventStatus status;

    /** Preserved across a requeue; the operator sees the real failure count. */
    private Integer attempts;

    private Instant deadAt;
    private Instant createdAt;
    private Instant updatedAt;

    /** Sanitised and length-capped. Never a raw exception with embedded secrets. */
    private String lastError;

    /** True when a payload exists. The payload itself is not returned. */
    private boolean payloadAvailable;

    private boolean requeueable;

    /** Set on the response of a successful requeue so the UI can refresh. */
    private boolean requeued;
}
