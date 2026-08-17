package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;

@Component
public class ContactWorkflowEntityContextProvider implements WorkflowEntityContextProvider {

    private final ContactRepository contactRepository;

    public ContactWorkflowEntityContextProvider(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Override
    public String entityType() {
        return "CONTACT";
    }

    @Override
    public Optional<Map<String, Object>> load(UUID tenantId, UUID entityId) {
        return contactRepository.findByIdAndTenantId(entityId, tenantId)
            .filter(contact -> !Boolean.TRUE.equals(contact.getDeleted()))
            .map(this::toContext);
    }

    private Map<String, Object> toContext(Contact contact) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("id", contact.getId());
        context.put("tenantId", contact.getTenantId());
        context.put("ownerId", contact.getOwnerId());
        context.put("accountId", contact.getAccountId());
        context.put("leadId", contact.getLeadId());
        context.put("firstName", contact.getFirstName());
        context.put("lastName", contact.getLastName());
        context.put("email", contact.getEmail());
        context.put("phone", contact.getPhone());
        context.put("mobile", contact.getMobile());
        context.put("jobTitle", contact.getJobTitle());
        context.put("department", contact.getDepartment());
        context.put("isPrimary", contact.getIsPrimary());
        context.put("isActive", contact.getIsActive());
        context.put("customFields", contact.getCustomData() == null ? Map.of() : contact.getCustomData());
        return context;
    }
}