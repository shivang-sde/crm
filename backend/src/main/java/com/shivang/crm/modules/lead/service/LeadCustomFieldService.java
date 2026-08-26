package com.shivang.crm.modules.lead.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.lead.dto.LeadCustomFieldCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadCustomFieldResponse;
import com.shivang.crm.modules.lead.entity.LeadCustomField;
import com.shivang.crm.modules.lead.mapper.LeadCustomFieldMapper;
import com.shivang.crm.modules.lead.repository.LeadCustomFieldRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LeadCustomFieldService {

    private final LeadCustomFieldRepository leadCustomFieldRepository;
    private final LeadCustomFieldMapper leadCustomFieldMapper;
    private final ObjectMapper objectMapper;

    /**
     * Create a custom field
     */
    public LeadCustomFieldResponse createField(UUID tenantId, LeadCustomFieldCreateRequest request) {
        log.info("Creating custom field: {} for tenant: {}", request.getFieldKey(), tenantId);

        LeadCustomField field = leadCustomFieldMapper.toEntity(request);
        field.setTenantId(tenantId);

        LeadCustomField savedField = leadCustomFieldRepository.save(field);
        return leadCustomFieldMapper.toResponse(savedField);
    }

    /**
     * Get active custom fields for tenant
     */
    @Transactional(readOnly = true)
    public List<LeadCustomFieldResponse> getActiveFields(UUID tenantId) {
        log.info("Fetching active custom fields for tenant: {}", tenantId);

        List<LeadCustomField> fields = leadCustomFieldRepository.findActiveFieldsByTenant(tenantId);
        return leadCustomFieldMapper.toResponseList(fields);
    }

    /**
     * Get all custom fields for tenant (including inactive)
     */
    @Transactional(readOnly = true)
    public List<LeadCustomFieldResponse> getAllFields(UUID tenantId) {
        log.info("Fetching all custom fields for tenant: {}", tenantId);

        List<LeadCustomField> fields = leadCustomFieldRepository.findByTenantIdOrderByDisplayOrder(tenantId);
        return leadCustomFieldMapper.toResponseList(fields);
    }

    /**
     * Update a custom field
     */
    public LeadCustomFieldResponse updateField(UUID id, UUID tenantId, LeadCustomFieldCreateRequest request) {
        log.info("Updating custom field: {} for tenant: {}", id, tenantId);

        LeadCustomField field = leadCustomFieldRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Custom field not found"));

        field.setFieldLabel(request.getFieldLabel());
        field.setFieldType(request.getFieldType());
        field.setIsRequired(request.getIsRequired());
        field.setIsActive(request.getIsActive());
        field.setDisplayOrder(request.getDisplayOrder());

        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
           field.setOptionsJson(request.getOptions());
        }

        LeadCustomField updatedField = leadCustomFieldRepository.save(field);
        return leadCustomFieldMapper.toResponse(updatedField);
    }

    /**
     * Delete a custom field
     */
    public void deleteField(UUID id, UUID tenantId) {
        log.info("Deleting custom field: {} for tenant: {}", id, tenantId);

        LeadCustomField field = leadCustomFieldRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Custom field not found"));

        leadCustomFieldRepository.delete(field);
    }
}
