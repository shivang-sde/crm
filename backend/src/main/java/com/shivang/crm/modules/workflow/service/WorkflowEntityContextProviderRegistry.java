package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Authoritative registry of entity types the workflow engine can resolve
 * context for. Registration is keyed by {@code entityType().toUpperCase()}.
 *
 * <p>L3.0-P1 Change D: duplicate registrations now fail fast at startup instead
 * of silently keeping the first bean. A collision would otherwise make one
 * entity's provider unreachable with no diagnostic.
 */
@Component
public class WorkflowEntityContextProviderRegistry {

    private static final String REGISTRY_NAME = "WorkflowEntityContextProviderRegistry";

    private final Map<String, WorkflowEntityContextProvider> providers;

    public WorkflowEntityContextProviderRegistry(List<WorkflowEntityContextProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
            provider -> provider.entityType().toUpperCase(),
            Function.identity(),
            (first, second) -> {
                throw new IllegalStateException(duplicateMessage(first.entityType(), first, second));
            }
        ));
    }

    private static String duplicateMessage(String entityType, Object first, Object second) {
        return REGISTRY_NAME + " has a duplicate registration for entity type '" + entityType
            + "': " + first.getClass().getName() + " and " + second.getClass().getName()
            + ". Remove or rename one of them; the engine cannot resolve both.";
    }

    /**
     * Entity types this registry can load. Used by workflow definition
     * validation to reject triggers on entity types the engine cannot resolve.
     */
    public Set<String> supportedEntityTypes() {
        return providers.keySet();
    }

    public boolean supports(String entityType) {
        return entityType != null && providers.containsKey(entityType.trim().toUpperCase());
    }

    public Optional<Map<String, Object>> load(UUID tenantId, String entityType, UUID entityId) {
        if (entityType == null || entityId == null) {
            return Optional.empty();
        }
        WorkflowEntityContextProvider provider = providers.get(entityType.toUpperCase());
        return provider == null ? Optional.empty() : provider.load(tenantId, entityId);
    }
}