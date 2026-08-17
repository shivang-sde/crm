package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class WorkflowOwnerAssignmentAdapterRegistry {

    private final Map<String, WorkflowOwnerAssignmentAdapter> adapters;

    public WorkflowOwnerAssignmentAdapterRegistry(List<WorkflowOwnerAssignmentAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(
            adapter -> adapter.entityType().toUpperCase(),
            Function.identity(),
            (first, second) -> first
        ));
    }

    public WorkflowOwnerAssignmentAdapter get(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            throw new WorkflowOwnerAssignmentException("WORKFLOW_ASSIGN_OWNER_ENTITY_TYPE_NOT_SUPPORTED", "Entity type is required");
        }
        WorkflowOwnerAssignmentAdapter adapter = adapters.get(entityType.trim().toUpperCase());
        if (adapter == null) {
            throw new WorkflowOwnerAssignmentException("WORKFLOW_ASSIGN_OWNER_ENTITY_TYPE_NOT_SUPPORTED", "Entity type is not supported: " + entityType);
        }
        return adapter;
    }
}