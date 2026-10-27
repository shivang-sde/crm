package com.shivang.crm.shared.event;

import java.util.Collections;
import java.util.LinkedHashMap;
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
    /**
     * L3.0-P6: the commercial line item is its own aggregate for eventing, even
     * though it is stored as a child of a Deal. It has a distinct lifecycle
     * (P4/P5 freeze it with the parent Deal) and its own consumers, so collapsing
     * it into DEAL would lose that.
     */
    public static final String DEAL_LINE_ITEM_ENTITY_TYPE = "DEAL_LINE_ITEM";
    public static final String OFFERING_ENTITY_TYPE = "OFFERING";
    public static final String ENTITLEMENT_ENTITY_TYPE = "ENTITLEMENT";
    /**
     * L3.1-P1: Support Ticket. A first-class tenant-owned domain entity attached to
     * the existing Account/Contact identity, so it can be resolved and referenced
     * alongside every other customer record.
     */
    public static final String TICKET_ENTITY_TYPE = "TICKET";
    /**
     * L3.1-P3: a Ticket message is its own eventing aggregate, not a TICKET event.
     *
     * <p>This follows the DEAL_LINE_ITEM precedent: the message has its own id, its
     * own lifecycle (created, edited, deleted) and its own consumers, so collapsing
     * it into TICKET would lose the identity of what was actually written. It is
     * deliberately not registered as a workflow trigger in P3 — that would require
     * a label on the entity, and entity resolution stays a Ticket-level concern.
     */
    public static final String TICKET_MESSAGE_ENTITY_TYPE = "TICKET_MESSAGE";
    /**
     * L3.3-P1: a customer signal submitted against the canonical Account identity.
     *
     * <p>Its own eventing aggregate rather than an ACCOUNT event, following the
     * TICKET_MESSAGE precedent: a feedback record has its own id, its own lifecycle
     * (submitted once, then immutable) and its own consumers — NPS/CSAT/CES scoring
     * and, later, L3.6 CX automation — so collapsing it into ACCOUNT would lose the
     * identity of what was actually answered.
     *
     * <p>Not registered as a workflow trigger in P1: doing so requires a
     * {@code WorkflowEntityContextProvider} bean, and a partial registration that
     * cannot resolve Feedback context correctly is worse than none.
     */
    public static final String FEEDBACK_ENTITY_TYPE = "FEEDBACK";
    public static final String CONTACT_ENTITY_TYPE = "CONTACT";
    public static final String ACCOUNT_ENTITY_TYPE = "ACCOUNT";
    public static final String TASK_ENTITY_TYPE = "TASK";
    public static final String MEETING_ENTITY_TYPE = "MEETING";
    public static final String CALL_ENTITY_TYPE = "CALL";
    public static final String RECORD_ENTITY_TYPE = "RECORD";
    public static final String RECEIVED_EVENT_TYPE = "RECEIVED";
    public static final String CREATED_EVENT_TYPE = "CREATED";
    public static final String UPDATED_EVENT_TYPE = "UPDATED";
    public static final String DELETED_EVENT_TYPE = "DELETED";
    /**
     * L3.0-P6: Offering lifecycle transitions are distinct from a plain field
     * update, because "this plan went on sale / off sale" is what downstream
     * catalogue consumers care about, not "a description was edited".
     */
    public static final String ACTIVATED_EVENT_TYPE = "ACTIVATED";
    public static final String DEACTIVATED_EVENT_TYPE = "DEACTIVATED";
    /** L3.0-P6: the terminal expiration transition, distinct from a generic status change. */
    public static final String EXPIRED_EVENT_TYPE = "EXPIRED";
    public static final String COMPLETED_EVENT_TYPE = "COMPLETED";
    public static final String CONVERTED_EVENT_TYPE = "CONVERTED";
    public static final String STATUS_CHANGED_EVENT_TYPE = "STATUS_CHANGED";
    public static final String OWNER_CHANGED_EVENT_TYPE = "OWNER_CHANGED";
    /**
     * L3.1-P4: an SLA deadline was missed. Distinct from STATUS_CHANGED because the
     * ticket's own lifecycle did not move — a clock expired, not a person acted — and
     * a consumer alerting on breaches must not have to correlate it with a status
     * event. Produced only by the SLA breach scanner.
     */
    public static final String SLA_BREACHED_EVENT_TYPE = "SLA_BREACHED";
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
            immutableMetadata(metadata)
        );
    }

    /**
     * Defensive, null-tolerant copy of producer metadata.
     *
     * <p>{@link Map#copyOf} rejects null keys and null values, so an otherwise
     * valid business mutation would fail with a NullPointerException inside the
     * caller's transaction whenever an optional metadata slot happened to be
     * null. Optional metadata is dropped instead: null keys and null values are
     * omitted, all other entries are preserved, and the result stays immutable.
     */
    private static Map<String, Object> immutableMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitized.isEmpty() ? Map.of() : Collections.unmodifiableMap(sanitized);
    }
}