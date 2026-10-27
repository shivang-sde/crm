package com.shivang.crm.shared.event;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    /**
     * Terminal failure state. The publish attempt budget was exhausted and the row
     * is no longer selected for publishing. It is never resurrected automatically;
     * an operator must requeue it explicitly.
     */
    DEAD
}