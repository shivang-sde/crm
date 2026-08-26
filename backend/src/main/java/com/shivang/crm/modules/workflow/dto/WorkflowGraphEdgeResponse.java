package com.shivang.crm.modules.workflow.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WorkflowGraphEdgeResponse(
    UUID id,
    UUID sourceNodeId,
    UUID targetNodeId,
    String edgeKey,
    Map<String, Object> configuration,
    Instant createdAt,
    Instant updatedAt
) {
}
