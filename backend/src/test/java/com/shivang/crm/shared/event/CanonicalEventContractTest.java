package com.shivang.crm.shared.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * L3.0-P6: the canonical event contract shared by every new event.
 *
 * <p>P6 added new entity and event types to {@link CanonicalCrmEvent}. This test
 * pins the envelope those events rely on, and re-asserts P1's null-metadata fix
 * so extending the vocabulary cannot quietly undo it.
 */
class CanonicalEventContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // --- P6 vocabulary ---

    @Test
    void theP6EntityAndEventTypesAreDeclared() {
        assertThat(CanonicalCrmEvent.DEAL_LINE_ITEM_ENTITY_TYPE).isEqualTo("DEAL_LINE_ITEM");
        assertThat(CanonicalCrmEvent.OFFERING_ENTITY_TYPE).isEqualTo("OFFERING");
        assertThat(CanonicalCrmEvent.ENTITLEMENT_ENTITY_TYPE).isEqualTo("ENTITLEMENT");
        assertThat(CanonicalCrmEvent.FEEDBACK_ENTITY_TYPE).isEqualTo("FEEDBACK");

        assertThat(CanonicalCrmEvent.CREATED_EVENT_TYPE).isEqualTo("CREATED");
        assertThat(CanonicalCrmEvent.UPDATED_EVENT_TYPE).isEqualTo("UPDATED");
        assertThat(CanonicalCrmEvent.DELETED_EVENT_TYPE).isEqualTo("DELETED");
        assertThat(CanonicalCrmEvent.ACTIVATED_EVENT_TYPE).isEqualTo("ACTIVATED");
        assertThat(CanonicalCrmEvent.DEACTIVATED_EVENT_TYPE).isEqualTo("DEACTIVATED");
        assertThat(CanonicalCrmEvent.EXPIRED_EVENT_TYPE).isEqualTo("EXPIRED");
    }

    @Test
    void theP6EventNamesDoNotCollideWithExistingVocabulary() {
        // The outbox derives eventName as "<entityType>.<eventType>", so a collision
        // would make two different aggregates indistinguishable to a consumer.
        List<String> entities = List.of(CanonicalCrmEvent.LEAD_ENTITY_TYPE,
                CanonicalCrmEvent.DEAL_ENTITY_TYPE, CanonicalCrmEvent.DEAL_LINE_ITEM_ENTITY_TYPE,
                CanonicalCrmEvent.OFFERING_ENTITY_TYPE, CanonicalCrmEvent.ENTITLEMENT_ENTITY_TYPE,
                CanonicalCrmEvent.CONTACT_ENTITY_TYPE, CanonicalCrmEvent.ACCOUNT_ENTITY_TYPE,
                CanonicalCrmEvent.TASK_ENTITY_TYPE, CanonicalCrmEvent.MEETING_ENTITY_TYPE,
                CanonicalCrmEvent.CALL_ENTITY_TYPE, CanonicalCrmEvent.RECORD_ENTITY_TYPE,
                CanonicalCrmEvent.FEEDBACK_ENTITY_TYPE);
        assertThat(entities).doesNotHaveDuplicates();

        List<String> events = List.of(CanonicalCrmEvent.CREATED_EVENT_TYPE,
                CanonicalCrmEvent.UPDATED_EVENT_TYPE, CanonicalCrmEvent.DELETED_EVENT_TYPE,
                CanonicalCrmEvent.ACTIVATED_EVENT_TYPE, CanonicalCrmEvent.DEACTIVATED_EVENT_TYPE,
                CanonicalCrmEvent.EXPIRED_EVENT_TYPE, CanonicalCrmEvent.STATUS_CHANGED_EVENT_TYPE,
                CanonicalCrmEvent.OWNER_CHANGED_EVENT_TYPE,
                CanonicalCrmEvent.STAGE_TRANSITIONED_EVENT_TYPE);
        assertThat(events).doesNotHaveDuplicates();
    }

    // --- envelope completeness ---

    @Test
    void anEventCarriesEveryEnvelopeField() {
        UUID tenantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        Map<String, Object> metadata = Map.of("actorId", "u1", "actorType", "USER");

        CanonicalCrmEvent event = CanonicalCrmEvent.forEntity(
                CanonicalCrmEvent.DEAL_LINE_ITEM_ENTITY_TYPE,
                CanonicalCrmEvent.CREATED_EVENT_TYPE,
                tenantId, entityId, metadata);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.entityId()).isEqualTo(entityId);
        assertThat(event.entityType()).isEqualTo("DEAL_LINE_ITEM");
        assertThat(event.eventType()).isEqualTo("CREATED");
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.metadata()).containsAllEntriesOf(metadata);
    }

    @Test
    void everyP6EventTypeSerializesIntoTheOutboxPayload() {
        for (String entity : List.of(CanonicalCrmEvent.DEAL_ENTITY_TYPE,
                CanonicalCrmEvent.DEAL_LINE_ITEM_ENTITY_TYPE,
                CanonicalCrmEvent.OFFERING_ENTITY_TYPE,
                CanonicalCrmEvent.ENTITLEMENT_ENTITY_TYPE)) {
            for (String event : List.of(CanonicalCrmEvent.CREATED_EVENT_TYPE,
                    CanonicalCrmEvent.UPDATED_EVENT_TYPE, CanonicalCrmEvent.DELETED_EVENT_TYPE,
                    CanonicalCrmEvent.ACTIVATED_EVENT_TYPE, CanonicalCrmEvent.DEACTIVATED_EVENT_TYPE,
                    CanonicalCrmEvent.EXPIRED_EVENT_TYPE,
                    CanonicalCrmEvent.STATUS_CHANGED_EVENT_TYPE)) {
                CanonicalCrmEvent canonical = CanonicalCrmEvent.forEntity(
                        entity, event, UUID.randomUUID(), UUID.randomUUID(), Map.of("k", "v"));

                var node = mapper.readTree(mapper.writeValueAsString(canonical));
                assertThat(node.get("entityType").asString()).isEqualTo(entity);
                assertThat(node.get("eventType").asString()).isEqualTo(event);
                assertThat(node.get("tenantId")).isNotNull();
                assertThat(node.get("entityId")).isNotNull();
                assertThat(node.get("eventId")).isNotNull();
                assertThat(node.get("occurredAt")).isNotNull();
                assertThat(node.get("metadata")).isNotNull();
            }
        }
    }

    // --- P1's null-safety must survive P6 ---

    @Test
    void nullMetadataIsSafe() {
        assertThatNoException().isThrownBy(() -> CanonicalCrmEvent.forEntity(
                CanonicalCrmEvent.OFFERING_ENTITY_TYPE, CanonicalCrmEvent.UPDATED_EVENT_TYPE,
                UUID.randomUUID(), UUID.randomUUID(), null));

        assertThat(CanonicalCrmEvent.forEntity(
                CanonicalCrmEvent.OFFERING_ENTITY_TYPE, CanonicalCrmEvent.UPDATED_EVENT_TYPE,
                UUID.randomUUID(), UUID.randomUUID(), null).metadata()).isEmpty();
    }

    @Test
    void nullMetadataValuesAreDroppedRatherThanThrowing() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("actorId", "u1");
        metadata.put("dealId", null);
        metadata.put("offeringId", null);

        CanonicalCrmEvent event = CanonicalCrmEvent.forEntity(
                CanonicalCrmEvent.DEAL_LINE_ITEM_ENTITY_TYPE, CanonicalCrmEvent.DELETED_EVENT_TYPE,
                UUID.randomUUID(), UUID.randomUUID(), metadata);

        // This is the P1 fix: Map.copyOf would have thrown NullPointerException here.
        assertThat(event.metadata()).containsOnlyKeys("actorId");
    }

    @Test
    void aMetadataMapOfOnlyNullsBecomesEmpty() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("a", null);
        metadata.put("b", null);

        assertThat(CanonicalCrmEvent.forEntity(CanonicalCrmEvent.ENTITLEMENT_ENTITY_TYPE,
                CanonicalCrmEvent.EXPIRED_EVENT_TYPE, UUID.randomUUID(), UUID.randomUUID(), metadata).metadata())
                .isEmpty();
    }

    @Test
    void emptyMetadataIsSafe() {
        assertThat(CanonicalCrmEvent.forEntity(CanonicalCrmEvent.ENTITLEMENT_ENTITY_TYPE,
                CanonicalCrmEvent.CREATED_EVENT_TYPE, UUID.randomUUID(), UUID.randomUUID(), Map.of()).metadata())
                .isEmpty();
    }

    @Test
    void metadataIsImmutableSoAProducerCannotMutateACommittedEvent() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("dealId", "d1");

        CanonicalCrmEvent event = CanonicalCrmEvent.forEntity(
                CanonicalCrmEvent.DEAL_LINE_ITEM_ENTITY_TYPE, CanonicalCrmEvent.CREATED_EVENT_TYPE,
                UUID.randomUUID(), UUID.randomUUID(), metadata);

        // Mutating the source map afterwards must not change the event.
        metadata.put("dealId", "tampered");
        assertThat(event.metadata()).containsEntry("dealId", "d1");
    }

    // --- publisher path ---

    @Test
    void publishingEnqueuesExactlyOneOutboxRowForOneEvent() {
        var outboxService = mock(CanonicalCrmEventOutboxService.class);
        var publisher = new CanonicalCrmEventPublisher(outboxService);
        UUID tenantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        CanonicalCrmEvent published = publisher.publishAndReturn(tenantId,
                CanonicalCrmEvent.DEAL_LINE_ITEM_ENTITY_TYPE,
                CanonicalCrmEvent.DELETED_EVENT_TYPE, entityId,
                Map.of("actorId", "u1", "actorType", "USER"));

        verify(outboxService).enqueue(published);
        assertThat(published.tenantId()).isEqualTo(tenantId);
        assertThat(published.entityId()).isEqualTo(entityId);
    }

    @Test
    void thePublisherAppendsCausalLineageWhenAWorkflowCausedTheMutation() {
        var outboxService = mock(CanonicalCrmEventOutboxService.class);
        var publisher = new CanonicalCrmEventPublisher(outboxService);
        UUID workflowId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        try {
            CausalEventContext.set(new CausalEventContext.Lineage(executionId, workflowId, 1));
            CanonicalCrmEvent event = publisher.publishAndReturn(UUID.randomUUID(),
                    CanonicalCrmEvent.DEAL_ENTITY_TYPE, CanonicalCrmEvent.UPDATED_EVENT_TYPE,
                    UUID.randomUUID(), Map.of("actorId", "u1", "actorType", "USER"));

            assertThat(event.metadata())
                    .containsEntry(CausalEventContext.METADATA_CAUSED_BY_WORKFLOW_ID, workflowId.toString())
                    .containsEntry(CausalEventContext.METADATA_CAUSED_BY_EXECUTION_ID, executionId.toString());
            // The producer's own metadata survives alongside the lineage.
            assertThat(event.metadata()).containsEntry("actorType", "USER");
        } finally {
            CausalEventContext.clear();
        }
    }

    @Test
    void publishingWithNullMetadataStillEnqueues() {
        var outboxService = mock(CanonicalCrmEventOutboxService.class);
        var publisher = new CanonicalCrmEventPublisher(outboxService);

        CanonicalCrmEvent event = publisher.publishAndReturn(UUID.randomUUID(),
                CanonicalCrmEvent.OFFERING_ENTITY_TYPE, CanonicalCrmEvent.ACTIVATED_EVENT_TYPE,
                UUID.randomUUID(), null);

        verify(outboxService).enqueue(event);
        assertThat(event.metadata()).isEmpty();
    }

    @Test
    void theOutboxEventNameIsDerivedFromEntityAndEventType() {
        var outboxService = mock(CanonicalCrmEventOutboxService.class);
        var publisher = new CanonicalCrmEventPublisher(outboxService);
        var repository = mock(OutboxEventRepository.class);
        var service = new CanonicalCrmEventOutboxService(repository, mapper);
        var realPublisher = new CanonicalCrmEventPublisher(service);

        realPublisher.publish(UUID.randomUUID(), CanonicalCrmEvent.DEAL_LINE_ITEM_ENTITY_TYPE,
                CanonicalCrmEvent.DELETED_EVENT_TYPE, UUID.randomUUID(), Map.of());

        var captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEventName()).isEqualTo("DEAL_LINE_ITEM.DELETED");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("DEAL_LINE_ITEM");
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(publisher).isNotNull();
    }
}
