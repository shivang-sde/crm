package com.shivang.crm.modules.workflow.dto;

import java.util.List;

/**
 * Read-only capability description consumed by the visual workflow builder.
 * Static vocabulary only: tenant-specific values (statuses, sources, stages,
 * custom field keys, users) are intentionally excluded and must be loaded from
 * their existing tenant-scoped endpoints.
 */
public record WorkflowMetadataResponse(
    List<EntityMetadata> entities,
    List<String> actions,
    List<String> operators
) {

    public record EntityMetadata(
        String entityType,
        String label,
        List<EventMetadata> events,
        List<String> fields,
        boolean customFieldsSupported,
        List<RelationshipMetadata> relationships
    ) {
    }

    /**
     * Controlled one-hop relationship exposed by the entity context provider.
     * relatedEntityType is null for polymorphic references (e.g. activity
     * entities whose target is addressed by entityType + entityId).
     */
    public record RelationshipMetadata(
        String key,
        String label,
        String relatedEntityType,
        List<String> fields,
        boolean customFieldsSupported
    ) {
    }

    public record EventMetadata(
        String eventType,
        String label,
        List<String> metadataFields
    ) {
    }

    public static EntityMetadata entity(
        String entityType,
        String label,
        List<EventMetadata> events,
        List<String> fields
    ) {
        return new EntityMetadata(entityType, label, events, fields, true, List.of());
    }

    public static EntityMetadata entity(
        String entityType,
        String label,
        List<EventMetadata> events,
        List<String> fields,
        boolean customFieldsSupported
    ) {
        return new EntityMetadata(entityType, label, events, fields, customFieldsSupported, List.of());
    }

    public static EntityMetadata entityWithRelationships(
        String entityType,
        String label,
        List<EventMetadata> events,
        List<String> fields,
        List<RelationshipMetadata> relationships
    ) {
        return new EntityMetadata(entityType, label, events, fields, true, relationships);
    }

    public static RelationshipMetadata relationship(
        String key, String label, String relatedEntityType,
        List<String> fields, boolean customFieldsSupported
    ) {
        return new RelationshipMetadata(key, label, relatedEntityType, fields, customFieldsSupported);
    }

    public static EventMetadata event(String eventType, String label, String... metadataFields) {
        return new EventMetadata(eventType, label, List.of(metadataFields));
    }
}
