package com.shivang.crm.shared.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Publishes canonical CRM events into the transactional outbox.
 *
 * Domain services call {@link #publish} after a successful persistence change;
 * the outbox shares the domain transaction and the asynchronous publisher
 * delivers to RabbitMQ after commit. Never publish directly to RabbitMQ from
 * domain code.
 *
 * When a mutation originates from a workflow action, the graph runtime exposes
 * causal lineage via {@link CausalEventContext}; it is appended to the event
 * metadata here so the trigger matcher can bound cross-workflow recursion.
 */
@Component
@RequiredArgsConstructor
public class CanonicalCrmEventPublisher {

    private final CanonicalCrmEventOutboxService canonicalCrmEventOutboxService;

    /**
     * Generic canonical event entry point.
     *
     * @param entityType aggregate type, e.g. LEAD / DEAL / CONTACT
     * @param eventType  event within the aggregate, e.g. CREATED / STATUS_CHANGED
     */
    public void publish(
        UUID tenantId,
        String entityType,
        String eventType,
        UUID entityId,
        Map<String, Object> metadata
    ) {
        CausalEventContext.Lineage lineage = CausalEventContext.get();
        Map<String, Object> effectiveMetadata = metadata;
        if (lineage != null) {
            effectiveMetadata = new HashMap<>(metadata == null ? Map.of() : metadata);
            effectiveMetadata.put(CausalEventContext.METADATA_CAUSED_BY_EXECUTION_ID, lineage.executionId().toString());
            effectiveMetadata.put(CausalEventContext.METADATA_CAUSED_BY_WORKFLOW_ID, lineage.workflowId().toString());
            effectiveMetadata.put(CausalEventContext.METADATA_CHAIN_DEPTH, lineage.chainDepth());
        }
        canonicalCrmEventOutboxService.enqueue(
            CanonicalCrmEvent.forEntity(entityType, eventType, tenantId, entityId, effectiveMetadata)
        );
    }

    public void publishLeadCreated(UUID tenantId, UUID leadId, UUID ingestionConfigId, UUID ingestionEventId) {
        publishLeadCreated(tenantId, leadId, Map.of(
            "source", CanonicalCrmEvent.UNIVERSAL_LEAD_INGESTION_SOURCE,
            "ingestionConfigId", ingestionConfigId,
            "ingestionEventId", ingestionEventId
        ));
    }

    public void publishLeadCreated(UUID tenantId, UUID leadId, Map<String, Object> metadata) {
        publish(
            tenantId,
            CanonicalCrmEvent.LEAD_ENTITY_TYPE,
            CanonicalCrmEvent.CREATED_EVENT_TYPE,
            leadId,
            metadata
        );
    }
}
