package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

public interface WorkflowEntityUpdateAdapter {

    String entityType();

    WorkflowEntityUpdateResult update(
        UUID tenantId,
        UUID actorId,
        UUID entityId,
        String field,
        Object value,
        Map<String, Object> currentCustomFields
    );
}