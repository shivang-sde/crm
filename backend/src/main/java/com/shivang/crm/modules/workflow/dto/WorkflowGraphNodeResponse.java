package com.shivang.crm.modules.workflow.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;

public record WorkflowGraphNodeResponse(
    UUID id,
    String nodeKey,
    WorkflowNodeType nodeType,
    String name,
    Map<String, Object> configuration,
    Instant createdAt,
    Instant updatedAt
) {
}
