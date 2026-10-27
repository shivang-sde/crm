package com.shivang.crm.modules.ticket.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * L3.1-P1: Support Ticket lifecycle states.
 *
 * <p>Persisted as a {@code VARCHAR(30)} string via {@code @Enumerated(EnumType.STRING)},
 * matching {@code EntitlementStatus} and every other status in the schema. Ordinal
 * persistence is never used, so reordering this enum cannot corrupt existing rows.
 *
 * <p>Deliberately absent: {@code PENDING}, {@code WAITING}, {@code ESCALATED}. A
 * Ticket is either open work or a delivered outcome; a "waiting on customer" state
 * and escalation belong to later L3.1 phases and are not modelled speculatively.
 *
 * <p>{@link #CLOSED} is terminal for ordinary forward progression, but an explicit
 * status operation may reopen it to {@link #OPEN}. Reopening is an audited
 * transition, never a side effect.
 */
public enum TicketStatus {

    /** Newly raised. Nothing has been started yet. */
    OPEN,

    /** An agent has picked the ticket up and is actively working it. */
    IN_PROGRESS,

    /** The customer's problem has been solved; awaiting confirmation or closure. */
    RESOLVED,

    /** Terminal for forward progression. Reopenable only by explicit request. */
    CLOSED;

    /**
     * The permitted target states for an explicit status change.
     *
     * <p>Idempotent self-assignment is permitted on purpose: repeating the current
     * status is a no-op, and rejecting it would make a retried request an error.
     * Callers suppress the {@code STATUS_CHANGED} event when the status did not
     * actually move, so permitting it here does not create a phantom event.
     */
    public Set<TicketStatus> allowedTransitions() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_PROGRESS, RESOLVED);
            case IN_PROGRESS -> EnumSet.of(RESOLVED);
            // Reopening a resolved ticket is a normal support outcome: the customer
            // reports the problem persisted.
            case RESOLVED -> EnumSet.of(CLOSED, OPEN);
            case CLOSED -> EnumSet.of(OPEN);
        };
    }

    /** True when moving to {@code target} is a permitted transition. */
    public boolean canTransitionTo(TicketStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    /** True when {@code target} is this same status, i.e. an idempotent no-op. */
    public boolean isSameAs(TicketStatus target) {
        return this == target;
    }

    /**
     * Whether reaching this state means the ticket is finished work.
     *
     * <p>Used to decide whether an open ticket still counts as active. A resolved
     * ticket is still open business (it can be reopened), so it remains active.
     */
    public boolean isTerminal() {
        return this == CLOSED;
    }
}
