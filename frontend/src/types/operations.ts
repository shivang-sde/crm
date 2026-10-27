/**
 * Types for the operator message recovery tooling (OPS-01 / OPS-02).
 *
 * These mirror the backend DTOs. Note what is absent on purpose: there is no
 * `payload` or `body` field on any of these, because the backend does not return
 * one. Do not add one without a backend change that sanitizes it.
 */

export interface DeadLetterView {
  /** Stable AMQP message id, equal to the canonical event id. */
  messageId: string | null;
  /** Only present if the publisher set one. Never synthesised. */
  correlationId: string | null;
  /** Exchange the message currently sits behind (the dead-letter exchange). */
  exchange: string | null;
  routingKey: string | null;
  queue: string | null;
  contentType: string | null;
  /** AMQP timestamp property, epoch seconds. Null when the publisher set none. */
  timestamp: number | null;
  redelivered: boolean;
  /** Set only on a copy produced by a re-drive. */
  redriveOf: string | null;
  /** x-death[0].count. Not a broader retry counter. */
  deathCount: number | null;
  /** x-death[0].reason, e.g. "rejected". */
  deathReason: string | null;
  /** Where the message was published before it died. Re-drive targets this. */
  originalExchange: string | null;
  originalRoutingKey: string | null;
  originalQueue: string | null;
  firstDeathExchange: string | null;
  firstDeathRoutingKey: string | null;
  firstDeathQueue: string | null;
  eventId: string | null;
  eventType: string | null;
  entityType: string | null;
  entityId: string | null;
  /**
   * Ownership derived from the declared tenantId field of the canonical event.
   * Null when the body was not a canonical event, in which case the message is
   * visible at platform scope only and the UI should say so.
   */
  tenantId: string | null;
  occurredAt: string | null;
  /** False once a successful re-drive has been recorded for this message id. */
  replayable: boolean;
  /** replayable AND the resolved destination is inside the approved topology. */
  redriveable: boolean;
}

export interface RedriveResult {
  originalMessageId: string;
  /** New AMQP id given to the republished copy. */
  republishedMessageId: string;
  exchange: string;
  routingKey: string;
  eventId: string | null;
  eventType: string | null;
  tenantId: string | null;
  replayedAt: string;
  /**
   * Always true: the re-drive republishes a copy and does not consume the dead
   * letter, because AMQP cannot delete a specific message from a queue. The audit
   * record is what marks the message as already re-driven.
   */
  originalDeadLetterRetained: boolean;
}

export interface OutboxDeadEventView {
  eventId: string;
  eventName: string | null;
  entityType: string | null;
  eventType: string | null;
  entityId: string | null;
  /** The persisted ownership column, never inferred from the payload. */
  tenantId: string | null;
  status: string;
  /** Preserved across a requeue, so the operator sees the real failure count. */
  attempts: number | null;
  deadAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  /** Sanitised and length-capped by the backend. */
  lastError: string | null;
  /**
   * True when a payload exists. The payload itself is never returned, so this only
   * tells the operator that there is a body to look at in the database.
   */
  payloadAvailable: boolean;
  requeueable: boolean;
  requeued: boolean;
}

export interface OperatorActionView {
  id: string;
  actionType: "DLQ_REDRIVE" | "OUTBOX_REQUEUE";
  targetId: string;
  tenantId: string | null;
  operatorUserId: string;
  operatorRole: string | null;
  requestId: string | null;
  originalExchange: string | null;
  originalRoutingKey: string | null;
  destinationExchange: string | null;
  destinationRoutingKey: string | null;
  result: "SUCCESS" | "FAILED" | "CONFLICT" | "NOT_FOUND" | "NOT_ALLOWED";
  failureReason: string | null;
  createdAt: string | null;
}
