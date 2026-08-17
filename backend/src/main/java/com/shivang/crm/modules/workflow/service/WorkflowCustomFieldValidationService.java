package com.shivang.crm.modules.workflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.account.entity.AccountCustomField;
import com.shivang.crm.modules.account.repository.AccountCustomFieldRepository;
import com.shivang.crm.modules.contact.entity.ContactCustomField;
import com.shivang.crm.modules.contact.repository.ContactCustomFieldRepository;
import com.shivang.crm.modules.deal.entity.DealCustomField;
import com.shivang.crm.modules.deal.repository.DealCustomFieldRepository;
import com.shivang.crm.modules.lead.entity.LeadCustomField;
import com.shivang.crm.modules.lead.repository.LeadCustomFieldRepository;

@Service
public class WorkflowCustomFieldValidationService {

    private final LeadCustomFieldRepository leadFields;
    private final ContactCustomFieldRepository contactFields;
    private final AccountCustomFieldRepository accountFields;
    private final DealCustomFieldRepository dealFields;

    public WorkflowCustomFieldValidationService(
        LeadCustomFieldRepository leadFields,
        ContactCustomFieldRepository contactFields,
        AccountCustomFieldRepository accountFields,
        DealCustomFieldRepository dealFields
    ) {
        this.leadFields = leadFields;
        this.contactFields = contactFields;
        this.accountFields = accountFields;
        this.dealFields = dealFields;
    }

    public void validate(String entityType, UUID tenantId, String fieldKey, Object value) {
        String fieldType;
        List<Map<String, String>> options;
        switch (entityType.toUpperCase()) {
            case "LEAD" -> {
                LeadCustomField field = leadFields.findActiveByTenantIdAndFieldKey(tenantId, fieldKey)
                    .orElseThrow(() -> invalid("Custom field is not active or does not belong to the tenant"));
                fieldType = field.getFieldType(); options = field.getOptionsJson();
            }
            case "CONTACT" -> {
                ContactCustomField field = contactFields.findActiveFieldsByTenant(tenantId).stream().filter(item -> fieldKey.equals(item.getFieldKey())).findFirst()
                    .orElseThrow(() -> invalid("Custom field is not active or does not belong to the tenant"));
                fieldType = field.getFieldType(); options = field.getOptionsJson();
            }
            case "ACCOUNT" -> {
                AccountCustomField field = accountFields.findActiveFieldsByTenant(tenantId).stream().filter(item -> fieldKey.equals(item.getFieldKey())).findFirst()
                    .orElseThrow(() -> invalid("Custom field is not active or does not belong to the tenant"));
                fieldType = field.getFieldType(); options = field.getOptionsJson();
            }
            case "DEAL" -> {
                DealCustomField field = dealFields.findByTenantIdAndFieldKey(tenantId, fieldKey)
                    .filter(item -> Boolean.TRUE.equals(item.getIsActive()) && !Boolean.TRUE.equals(item.getDeleted()))
                    .orElseThrow(() -> invalid("Custom field is not active or does not belong to the tenant"));
                fieldType = field.getFieldType(); options = field.getOptionsJson();
            }
            default -> throw invalid("Custom fields are not supported for this entity");
        }
        validateValue(fieldType, options, value);
    }

    private void validateValue(String rawType, List<Map<String, String>> options, Object value) {
        String type = rawType == null ? "TEXT" : rawType.trim().toUpperCase();
        if (value == null) return;
        try {
            switch (type) {
                case "TEXT", "TEXTAREA", "EMAIL", "PHONE", "URL" -> require(value, String.class);
                case "NUMBER" -> new BigDecimal(String.valueOf(value));
                case "BOOLEAN" -> Boolean.parseBoolean(String.valueOf(value));
                case "DATE" -> LocalDate.parse(String.valueOf(value));
                case "SELECT" -> validateOption(options, String.valueOf(value));
                case "MULTISELECT" -> {
                    if (!(value instanceof List<?> values)) throw new IllegalArgumentException();
                    for (Object item : values) validateOption(options, String.valueOf(item));
                }
                default -> throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            throw invalid("Value does not match custom field type " + type);
        }
    }

    private void validateOption(List<Map<String, String>> options, String value) {
        if (options != null && options.stream().noneMatch(option -> value.equals(option.get("value")))) {
            throw new IllegalArgumentException();
        }
    }

    private void require(Object value, Class<?> type) {
        if (!type.isInstance(value)) throw new IllegalArgumentException();
    }

    private WorkflowEntityUpdateException invalid(String message) {
        return new WorkflowEntityUpdateException("WORKFLOW_UPDATE_VALIDATION_FAILED", message);
    }
}