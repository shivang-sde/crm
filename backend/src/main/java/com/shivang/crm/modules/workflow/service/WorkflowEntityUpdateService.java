package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowEntityUpdateService {

    private final WorkflowEntityUpdateAdapterRegistry adapterRegistry;

    public WorkflowEntityUpdateResult update(
        UUID tenantId,
        UUID actorId,
        String entityType,
        UUID entityId,
        String field,
        Object value,
        Map<String, Object> currentCustomFields
    ) {
        if (tenantId == null || actorId == null || entityId == null || field == null || field.isBlank()) {
            throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_INVALID_CONFIG", "Update identity and field are required");
        }
        try {
            return adapterRegistry.get(entityType).update(
                tenantId, actorId, entityId, field.trim(), value, currentCustomFields
            );
        } catch (WorkflowEntityUpdateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_EXECUTION_FAILED", "Entity update failed");
        }
    }
}