package com.shivang.crm.shared.event;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    PUBLISHED
}