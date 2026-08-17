package com.shivang.crm.shared.event;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;

public record CanonicalCrmEvent(
    UUID eventId,
    String entityType,
    String eventType,
    UUID tenantId,
    UUID entityId,
    Instant occurredAt,
    Map<String, Object> metadata
) {

    public static final String LEAD_ENTITY_TYPE = "LEAD";
    public static final String CREATED_EVENT_TYPE = "CREATED";
    public static final String UNIVERSAL_LEAD_INGESTION_SOURCE = "UNIVERSAL_LEAD_INGESTION";

    public static CanonicalCrmEvent leadCreated(
        UUID tenantId,
        UUID leadId,
        Map<String, Object> metadata
    ) {
        return new CanonicalCrmEvent(
            UUID.randomUUID(),
            LEAD_ENTITY_TYPE,
            CREATED_EVENT_TYPE,
            tenantId,
            leadId,
            Instant.now(),
            metadata == null ? Map.of() : Map.copyOf(metadata)
        );
    }
}