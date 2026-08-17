package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.lead.dto.LeadUpdateRequest;
import com.shivang.crm.modules.lead.repository.LeadSourceRepository;
import com.shivang.crm.modules.lead.repository.LeadStatusRepository;
import com.shivang.crm.modules.lead.service.LeadService;

@Component
public class LeadWorkflowEntityUpdateAdapter implements WorkflowEntityUpdateAdapter {

    private final LeadService leadService;
    private final LeadStatusRepository statusRepository;
    private final LeadSourceRepository sourceRepository;
    private final WorkflowCustomFieldValidationService customFieldValidationService;

    public LeadWorkflowEntityUpdateAdapter(LeadService leadService, LeadStatusRepository statusRepository, LeadSourceRepository sourceRepository, WorkflowCustomFieldValidationService customFieldValidationService) {
        this.leadService = leadService;
        this.statusRepository = statusRepository;
        this.sourceRepository = sourceRepository;
        this.customFieldValidationService = customFieldValidationService;
    }

    @Override public String entityType() { return "LEAD"; }

    @Override
    public WorkflowEntityUpdateResult update(UUID tenantId, UUID actorId, UUID entityId, String field, Object value, Map<String, Object> currentCustomFields) {
        LeadUpdateRequest.LeadUpdateRequestBuilder request = LeadUpdateRequest.builder();
        if (field.startsWith("customFields.")) {
            String key = WorkflowUpdateValueSupport.customKey(field);
            customFieldValidationService.validate("LEAD", tenantId, key, value);
            request.customData(WorkflowUpdateValueSupport.customFields(currentCustomFields, key, value));
        } else {
            switch (field) {
                case "firstName" -> request.firstName(WorkflowUpdateValueSupport.text(value, field));
                case "lastName" -> request.lastName(WorkflowUpdateValueSupport.text(value, field));
                case "email" -> request.email(WorkflowUpdateValueSupport.text(value, field));
                case "phone" -> request.phone(WorkflowUpdateValueSupport.text(value, field));
                case "company" -> request.company(WorkflowUpdateValueSupport.text(value, field));
                case "score" -> request.score(WorkflowUpdateValueSupport.integer(value, field));
                case "status" -> request.statusId(resolveStatus(tenantId, value));
                case "source" -> request.sourceId(resolveSource(tenantId, value));
                default -> throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_FIELD_NOT_SUPPORTED", "Lead field is not supported: " + field);
            }
        }
        leadService.updateLead(entityId, tenantId, actorId, request.build());
        return WorkflowUpdateValueSupport.result("LEAD", entityId, field, value);
    }

    private UUID resolveStatus(UUID tenantId, Object value) {
        try { return statusRepository.findByIdAndTenantId(UUID.fromString(String.valueOf(value)), tenantId).orElseThrow().getId(); }
        catch (Exception ignored) { return statusRepository.findByTenantIdAndName(tenantId, String.valueOf(value)).orElseThrow(() -> new WorkflowEntityUpdateException("WORKFLOW_UPDATE_VALIDATION_FAILED", "Lead status not found")).getId(); }
    }

    private UUID resolveSource(UUID tenantId, Object value) {
        try { return sourceRepository.findById(UUID.fromString(String.valueOf(value))).filter(source -> tenantId.equals(source.getTenantId())).orElseThrow().getId(); }
        catch (Exception ignored) { return sourceRepository.findByTenantIdAndName(tenantId, String.valueOf(value)).orElseThrow(() -> new WorkflowEntityUpdateException("WORKFLOW_UPDATE_VALIDATION_FAILED", "Lead source not found")).getId(); }
    }
}