package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.contact.service.ContactService;

@Component
public class ContactWorkflowOwnerAssignmentAdapter implements WorkflowOwnerAssignmentAdapter {

    private final ContactService contactService;

    public ContactWorkflowOwnerAssignmentAdapter(ContactService contactService) { this.contactService = contactService; }

    @Override public String entityType() { return "CONTACT"; }

    @Override
    public WorkflowOwnerAssignmentResult assign(UUID tenantId, UUID actorId, UUID entityId, UUID ownerId) {
        contactService.assignOwner(entityId, tenantId, ownerId, actorId);
        return new WorkflowOwnerAssignmentResult("CONTACT", entityId, ownerId, Map.of("assigned", true));
    }
}