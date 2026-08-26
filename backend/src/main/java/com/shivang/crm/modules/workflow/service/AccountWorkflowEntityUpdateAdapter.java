package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.account.dto.AccountUpdateRequest;
import com.shivang.crm.modules.account.service.AccountService;

@Component
public class AccountWorkflowEntityUpdateAdapter implements WorkflowEntityUpdateAdapter {

    private final AccountService accountService;
    private final WorkflowCustomFieldValidationService customFieldValidationService;

    public AccountWorkflowEntityUpdateAdapter(AccountService accountService, WorkflowCustomFieldValidationService customFieldValidationService) { this.accountService = accountService; this.customFieldValidationService = customFieldValidationService; }

    @Override public String entityType() { return "ACCOUNT"; }

    @Override
    public WorkflowEntityUpdateResult update(UUID tenantId, UUID actorId, UUID entityId, String field, Object value, Map<String, Object> currentCustomFields) {
        if ("owner".equals(field)) {
            accountService.assignOwner(entityId, tenantId, WorkflowUpdateValueSupport.uuid(value, field), actorId);
            return WorkflowUpdateValueSupport.result("ACCOUNT", entityId, field, value);
        }
        AccountUpdateRequest.AccountUpdateRequestBuilder request = AccountUpdateRequest.builder();
        switch (field) {
            case "name" -> request.name(WorkflowUpdateValueSupport.text(value, field));
            case "website" -> request.website(WorkflowUpdateValueSupport.text(value, field));
            case "industry" -> request.industry(WorkflowUpdateValueSupport.text(value, field));
            case "phone" -> request.phone(WorkflowUpdateValueSupport.text(value, field));
            case "email" -> request.email(WorkflowUpdateValueSupport.text(value, field));
            case "annualRevenue" -> request.annualRevenue(WorkflowUpdateValueSupport.decimal(value, field));
            case "employeeCount" -> request.employeeCount(WorkflowUpdateValueSupport.integer(value, field));
            case "description" -> request.description(WorkflowUpdateValueSupport.text(value, field));
            case "country" -> request.country(WorkflowUpdateValueSupport.text(value, field));
            case "state" -> request.state(WorkflowUpdateValueSupport.text(value, field));
            case "city" -> request.city(WorkflowUpdateValueSupport.text(value, field));
            case "addressLine1" -> request.addressLine1(WorkflowUpdateValueSupport.text(value, field));
            case "postalCode" -> request.postalCode(WorkflowUpdateValueSupport.text(value, field));
            default -> {
                if (field.startsWith("customFields.")) {
                    String key = WorkflowUpdateValueSupport.customKey(field);
                    customFieldValidationService.validate("ACCOUNT", tenantId, key, value);
                    request.customData(WorkflowUpdateValueSupport.customFields(currentCustomFields, key, value));
                }
                else throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_FIELD_NOT_SUPPORTED", "Account field is not supported: " + field);
            }
        }
        accountService.updateAccount(entityId, tenantId, actorId, request.build());
        return WorkflowUpdateValueSupport.result("ACCOUNT", entityId, field, value);
    }
}