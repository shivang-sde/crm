package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class WorkflowEntityContextProviderRegistry {

    private final Map<String, WorkflowEntityContextProvider> providers;

    public WorkflowEntityContextProviderRegistry(List<WorkflowEntityContextProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
            provider -> provider.entityType().toUpperCase(),
            Function.identity(),
            (first, second) -> first
        ));
    }

    public Optional<Map<String, Object>> load(UUID tenantId, String entityType, UUID entityId) {
        if (entityType == null || entityId == null) {
            return Optional.empty();
        }
        WorkflowEntityContextProvider provider = providers.get(entityType.toUpperCase());
        return provider == null ? Optional.empty() : provider.load(tenantId, entityId);
    }
}