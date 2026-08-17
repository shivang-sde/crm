package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class WorkflowEntityUpdateAdapterRegistry {

    private final Map<String, WorkflowEntityUpdateAdapter> adapters;

    public WorkflowEntityUpdateAdapterRegistry(List<WorkflowEntityUpdateAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(
            adapter -> adapter.entityType().toUpperCase(),
            Function.identity(),
            (first, second) -> first
        ));
    }

    public WorkflowEntityUpdateAdapter get(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_ENTITY_TYPE_NOT_SUPPORTED", "Entity type is required");
        }
        WorkflowEntityUpdateAdapter adapter = adapters.get(entityType.trim().toUpperCase());
        if (adapter == null) {
            throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_ENTITY_TYPE_NOT_SUPPORTED", "Entity type is not supported: " + entityType);
        }
        return adapter;
    }
}