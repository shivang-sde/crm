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
    public static final String DEAL_ENTITY_TYPE = "DEAL";
    public static final String CONTACT_ENTITY_TYPE = "CONTACT";
    public static final String ACCOUNT_ENTITY_TYPE = "ACCOUNT";
    public static final String TASK_ENTITY_TYPE = "TASK";
    public static final String MEETING_ENTITY_TYPE = "MEETING";
    public static final String CALL_ENTITY_TYPE = "CALL";
    public static final String CREATED_EVENT_TYPE = "CREATED";
    public static final String UPDATED_EVENT_TYPE = "UPDATED";
    public static final String COMPLETED_EVENT_TYPE = "COMPLETED";
    public static final String CONVERTED_EVENT_TYPE = "CONVERTED";
    public static final String STATUS_CHANGED_EVENT_TYPE = "STATUS_CHANGED";
    public static final String OWNER_CHANGED_EVENT_TYPE = "OWNER_CHANGED";
    public static final String STAGE_TRANSITIONED_EVENT_TYPE = "STAGE_TRANSITIONED";
    public static final String UNIVERSAL_LEAD_INGESTION_SOURCE = "UNIVERSAL_LEAD_INGESTION";

    public static CanonicalCrmEvent leadCreated(
        UUID tenantId,
        UUID leadId,
        Map<String, Object> metadata
    ) {
        return forEntity(LEAD_ENTITY_TYPE, CREATED_EVENT_TYPE, tenantId, leadId, metadata);
    }

    public static CanonicalCrmEvent forEntity(
        String entityType,
        String eventType,
        UUID tenantId,
        UUID entityId,
        Map<String, Object> metadata
    ) {
        return new CanonicalCrmEvent(
            UUID.randomUUID(),
            entityType,
            eventType,
            tenantId,
            entityId,
            Instant.now(),
            metadata == null ? Map.of() : Map.copyOf(metadata)
        );
    }
}