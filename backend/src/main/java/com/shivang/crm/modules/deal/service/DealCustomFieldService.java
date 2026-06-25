package com.shivang.crm.modules.deal.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.deal.dto.DealCustomFieldCreateRequest;
import com.shivang.crm.modules.deal.dto.DealCustomFieldResponse;
import com.shivang.crm.modules.deal.entity.DealCustomField;
import com.shivang.crm.modules.deal.mapper.DealCustomFieldMapper;
import com.shivang.crm.modules.deal.repository.DealCustomFieldRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DealCustomFieldService {

    private final DealCustomFieldRepository dealCustomFieldRepository;
    private final DealCustomFieldMapper dealCustomFieldMapper;

    public DealCustomFieldResponse createField(UUID tenantId, DealCustomFieldCreateRequest request) {
        log.info("Creating deal custom field: {} for tenant: {}", request.getFieldKey(), tenantId);

        DealCustomField field = dealCustomFieldMapper.toEntity(request);
        field.setTenantId(tenantId);

        DealCustomField savedField = dealCustomFieldRepository.save(field);
        return dealCustomFieldMapper.toResponse(savedField);
    }

    @Transactional(readOnly = true)
    public List<DealCustomFieldResponse> getActiveFields(UUID tenantId) {
        log.info("Fetching active deal custom fields for tenant: {}", tenantId);
        return dealCustomFieldMapper.toResponseList(dealCustomFieldRepository.findActiveFieldsByTenant(tenantId));
    }

    @Transactional(readOnly = true)
    public List<DealCustomFieldResponse> getAllFields(UUID tenantId) {
        log.info("Fetching all deal custom fields for tenant: {}", tenantId);
        return dealCustomFieldMapper.toResponseList(dealCustomFieldRepository.findByTenantIdOrderByDisplayOrder(tenantId));
    }

    public DealCustomFieldResponse updateField(UUID id, UUID tenantId, DealCustomFieldCreateRequest request) {
        log.info("Updating deal custom field: {} for tenant: {}", id, tenantId);

        DealCustomField field = dealCustomFieldRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Custom field not found"));

        if (!field.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to field");
        }

        field.setFieldLabel(request.getFieldLabel());
        field.setFieldType(request.getFieldType());
        field.setIsRequired(request.getIsRequired());
        field.setIsActive(request.getIsActive());
        field.setDisplayOrder(request.getDisplayOrder());

        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            field.setOptionsJson(request.getOptions());
        }

        DealCustomField updatedField = dealCustomFieldRepository.save(field);
        return dealCustomFieldMapper.toResponse(updatedField);
    }

    public void deleteField(UUID id, UUID tenantId) {
        log.info("Deleting deal custom field: {} for tenant: {}", id, tenantId);

        DealCustomField field = dealCustomFieldRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Custom field not found"));

        if (!field.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to field");
        }

        dealCustomFieldRepository.delete(field);
    }
}
