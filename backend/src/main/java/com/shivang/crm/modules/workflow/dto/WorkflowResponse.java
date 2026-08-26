package com.shivang.crm.modules.workflow.dto;

import java.time.Instant;
import java.util.UUID;

import com.shivang.crm.modules.workflow.entity.WorkflowStatus;

public record WorkflowResponse(
    UUID id,
    String name,
    WorkflowStatus status,
    UUID activeVersionId,
    Instant createdAt,
    Instant updatedAt
) {
}
