package com.shivang.crm.modules.workflow.service;

import java.util.Map;

public record WorkflowEntityUpdateResult(
    String entityType,
    java.util.UUID entityId,
    String field,
    Object value,
    Map<String, Object> details
) {
}