package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.contact.dto.ContactUpdateRequest;
import com.shivang.crm.modules.contact.service.ContactService;

@Component
public class ContactWorkflowEntityUpdateAdapter implements WorkflowEntityUpdateAdapter {

    private final ContactService contactService;
    private final WorkflowCustomFieldValidationService customFieldValidationService;

    public ContactWorkflowEntityUpdateAdapter(ContactService contactService, WorkflowCustomFieldValidationService customFieldValidationService) { this.contactService = contactService; this.customFieldValidationService = customFieldValidationService; }

    @Override public String entityType() { return "CONTACT"; }

    @Override
    public WorkflowEntityUpdateResult update(UUID tenantId, UUID actorId, UUID entityId, String field, Object value, Map<String, Object> currentCustomFields) {
        ContactUpdateRequest.ContactUpdateRequestBuilder request = ContactUpdateRequest.builder();
        switch (field) {
            case "firstName" -> request.firstName(WorkflowUpdateValueSupport.text(value, field));
            case "lastName" -> request.lastName(WorkflowUpdateValueSupport.text(value, field));
            case "email" -> request.email(WorkflowUpdateValueSupport.text(value, field));
            case "phone" -> request.phone(WorkflowUpdateValueSupport.text(value, field));
            case "mobile" -> request.mobile(WorkflowUpdateValueSupport.text(value, field));
            case "jobTitle" -> request.jobTitle(WorkflowUpdateValueSupport.text(value, field));
            case "department" -> request.department(WorkflowUpdateValueSupport.text(value, field));
            default -> {
                if (field.startsWith("customFields.")) {
                    String key = WorkflowUpdateValueSupport.customKey(field);
                    customFieldValidationService.validate("CONTACT", tenantId, key, value);
                    request.customData(WorkflowUpdateValueSupport.customFields(currentCustomFields, key, value));
                }
                else throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_FIELD_NOT_SUPPORTED", "Contact field is not supported: " + field);
            }
        }
        contactService.updateContact(entityId, tenantId, actorId, request.build());
        return WorkflowUpdateValueSupport.result("CONTACT", entityId, field, value);
    }
}