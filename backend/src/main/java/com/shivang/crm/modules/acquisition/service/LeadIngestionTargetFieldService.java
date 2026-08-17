package com.shivang.crm.modules.acquisition.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.acquisition.dto.LeadIngestionTargetField;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionTargetType;
import com.shivang.crm.modules.lead.entity.LeadCustomField;
import com.shivang.crm.modules.lead.repository.LeadCustomFieldRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeadIngestionTargetFieldService {

    private static final Map<String, String> STANDARD_FIELDS = Map.of(
        "firstName", "STRING",
        "lastName", "STRING",
        "email", "STRING",
        "phone", "STRING",
        "company", "STRING"
    );

    private static final Map<String, String> SYSTEM_FIELDS = Map.of(
        "status", "STRING",
        "source", "STRING"
    );

    private final LeadCustomFieldRepository leadCustomFieldRepository;

    public List<LeadIngestionTargetField> listTargetFields(UUID tenantId) {
        List<LeadIngestionTargetField> targetFields = new ArrayList<>();

        STANDARD_FIELDS.forEach((fieldKey, dataType) -> targetFields.add(LeadIngestionTargetField.builder()
            .targetType(LeadIngestionTargetType.STANDARD_FIELD)
            .fieldKey(fieldKey)
            .label(fieldKey)
            .dataType(dataType)
            .required("firstName".equals(fieldKey))
            .build()));

        SYSTEM_FIELDS.forEach((fieldKey, dataType) -> targetFields.add(LeadIngestionTargetField.builder()
            .targetType(LeadIngestionTargetType.SYSTEM_FIELD)
            .fieldKey(fieldKey)
            .label(fieldKey)
            .dataType(dataType)
            .required(false)
            .build()));

        List<LeadCustomField> customFields = leadCustomFieldRepository.findActiveFieldsByTenant(tenantId);
        for (LeadCustomField customField : customFields) {
            targetFields.add(LeadIngestionTargetField.builder()
                .targetType(LeadIngestionTargetType.CUSTOM_FIELD)
                .fieldKey(customField.getFieldKey())
                .label(customField.getFieldLabel())
                .dataType(customField.getFieldType() == null ? "TEXT" : customField.getFieldType())
                .required(Boolean.TRUE.equals(customField.getIsRequired()))
                .build());
        }

        targetFields.sort(Comparator
            .comparing((LeadIngestionTargetField f) -> f.getTargetType().name())
            .thenComparing(f -> f.getFieldKey().toLowerCase(Locale.ROOT)));

        return targetFields;
    }

    public boolean isSupportedTarget(LeadIngestionTargetType targetType, String targetField, UUID tenantId) {
        if (targetType == null || targetField == null || targetField.isBlank()) {
            return false;
        }

        if (targetType == LeadIngestionTargetType.STANDARD_FIELD) {
            return STANDARD_FIELDS.containsKey(targetField);
        }

        if (targetType == LeadIngestionTargetType.SYSTEM_FIELD) {
            return SYSTEM_FIELDS.containsKey(targetField);
        }

        return leadCustomFieldRepository
            .findActiveByTenantIdAndFieldKey(tenantId, targetField)
            .isPresent();
    }
}
