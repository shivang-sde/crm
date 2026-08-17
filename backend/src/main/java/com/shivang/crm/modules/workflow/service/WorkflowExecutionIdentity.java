package com.shivang.crm.modules.workflow.service;

import java.util.UUID;

import com.shivang.crm.modules.workflow.entity.WorkflowActorType;

public record WorkflowExecutionIdentity(
    UUID tenantId,
    UUID actorId,
    WorkflowActorType actorType
) {
}