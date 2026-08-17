package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.deal.service.DealService;

@Component
public class DealWorkflowOwnerAssignmentAdapter implements WorkflowOwnerAssignmentAdapter {

    private final DealService dealService;

    public DealWorkflowOwnerAssignmentAdapter(DealService dealService) { this.dealService = dealService; }

    @Override public String entityType() { return "DEAL"; }

    @Override
    public WorkflowOwnerAssignmentResult assign(UUID tenantId, UUID actorId, UUID entityId, UUID ownerId) {
        dealService.assignDeal(entityId, tenantId, ownerId, actorId);
        return new WorkflowOwnerAssignmentResult("DEAL", entityId, ownerId, Map.of("assigned", true));
    }
}