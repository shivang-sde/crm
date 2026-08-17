package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.lead.service.LeadService;

@Component
public class LeadWorkflowOwnerAssignmentAdapter implements WorkflowOwnerAssignmentAdapter {

    private final LeadService leadService;

    public LeadWorkflowOwnerAssignmentAdapter(LeadService leadService) { this.leadService = leadService; }

    @Override public String entityType() { return "LEAD"; }

    @Override
    public WorkflowOwnerAssignmentResult assign(UUID tenantId, UUID actorId, UUID entityId, UUID ownerId) {
        leadService.assignLead(entityId, tenantId, ownerId, actorId);
        return new WorkflowOwnerAssignmentResult("LEAD", entityId, ownerId, Map.of("assigned", true));
    }
}