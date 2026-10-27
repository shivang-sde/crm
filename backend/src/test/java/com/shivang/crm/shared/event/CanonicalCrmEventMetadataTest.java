package com.shivang.crm.shared.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CanonicalCrmEventMetadataTest {

    @Test
    void nullMetadataYieldsEmptyImmutableMap() {
        CanonicalCrmEvent event = CanonicalCrmEvent.forEntity(
                CanonicalCrmEvent.DEAL_ENTITY_TYPE,
                CanonicalCrmEvent.CREATED_EVENT_TYPE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);

        assertThat(event.metadata()).isEmpty();
    }

    @Test
    void nullValuedEntriesAreOmittedWithoutThrowing() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "MANUAL");
        metadata.put("previousOwnerId", null);
        metadata.put("actorId", "user-1");

        CanonicalCrmEvent event = CanonicalCrmEvent.forEntity(
                CanonicalCrmEvent.ACCOUNT_ENTITY_TYPE,
                CanonicalCrmEvent.OWNER_CHANGED_EVENT_TYPE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                metadata);

        assertThat(event.metadata())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "source", "MANUAL",
                        "actorId", "user-1"));
    }

    @Test
    void metadataWithOnlyNullValuesBecomesEmptyMap() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("a", null);
        metadata.put("b", null);

        CanonicalCrmEvent event = CanonicalCrmEvent.forEntity(
                CanonicalCrmEvent.ACCOUNT_ENTITY_TYPE,
                CanonicalCrmEvent.UPDATED_EVENT_TYPE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                metadata);

        assertThat(event.metadata()).isEmpty();
    }

    @Test
    void validMetadataIsPreserved() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "MANUAL");
        metadata.put("actorType", "USER");
        metadata.put("amount", 1500);

        CanonicalCrmEvent event = CanonicalCrmEvent.forEntity(
                CanonicalCrmEvent.DEAL_ENTITY_TYPE,
                CanonicalCrmEvent.UPDATED_EVENT_TYPE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                metadata);

        assertThat(event.metadata())
                .containsOnlyKeys("source", "actorType", "amount")
                .containsEntry("source", "MANUAL")
                .containsEntry("actorType", "USER")
                .containsEntry("amount", 1500);
    }

    @Test
    void metadataRemainsImmutable() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "MANUAL");

        CanonicalCrmEvent event = CanonicalCrmEvent.forEntity(
                CanonicalCrmEvent.DEAL_ENTITY_TYPE,
                CanonicalCrmEvent.CREATED_EVENT_TYPE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                metadata);

        assertThatCode(() -> event.metadata().put("injected", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void callerMutationAfterConstructionDoesNotAffectEvent() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "MANUAL");

        CanonicalCrmEvent event = CanonicalCrmEvent.forEntity(
                CanonicalCrmEvent.DEAL_ENTITY_TYPE,
                CanonicalCrmEvent.CREATED_EVENT_TYPE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                metadata);

        metadata.put("late", "value");

        assertThat(event.metadata()).containsOnlyKeys("source");
    }
}