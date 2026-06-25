package com.shivang.crm.modules.contact.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.contact.dto.ContactCustomFieldCreateRequest;
import com.shivang.crm.modules.contact.dto.ContactCustomFieldResponse;
import com.shivang.crm.modules.contact.entity.ContactCustomField;
import com.shivang.crm.modules.contact.mapper.ContactCustomFieldMapper;
import com.shivang.crm.modules.contact.repository.ContactCustomFieldRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContactCustomFieldService {

    private final ContactCustomFieldRepository contactCustomFieldRepository;
    private final ContactCustomFieldMapper contactCustomFieldMapper;

    public ContactCustomFieldResponse createField(UUID tenantId, ContactCustomFieldCreateRequest request) {
        log.info("Creating contact custom field: {} for tenant: {}", request.getFieldKey(), tenantId);

        ContactCustomField field = contactCustomFieldMapper.toEntity(request);
        field.setTenantId(tenantId);

        ContactCustomField savedField = contactCustomFieldRepository.save(field);
        return contactCustomFieldMapper.toResponse(savedField);
    }

    @Transactional(readOnly = true)
    public List<ContactCustomFieldResponse> getActiveFields(UUID tenantId) {
        log.info("Fetching active contact custom fields for tenant: {}", tenantId);
        return contactCustomFieldMapper.toResponseList(contactCustomFieldRepository.findActiveFieldsByTenant(tenantId));
    }

    @Transactional(readOnly = true)
    public List<ContactCustomFieldResponse> getAllFields(UUID tenantId) {
        log.info("Fetching all contact custom fields for tenant: {}", tenantId);
        return contactCustomFieldMapper.toResponseList(contactCustomFieldRepository.findByTenantIdOrderByDisplayOrder(tenantId));
    }

    public ContactCustomFieldResponse updateField(UUID id, UUID tenantId, ContactCustomFieldCreateRequest request) {
        log.info("Updating contact custom field: {} for tenant: {}", id, tenantId);

        ContactCustomField field = contactCustomFieldRepository.findById(id)
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

        ContactCustomField updatedField = contactCustomFieldRepository.save(field);
        return contactCustomFieldMapper.toResponse(updatedField);
    }

    public void deleteField(UUID id, UUID tenantId) {
        log.info("Deleting contact custom field: {} for tenant: {}", id, tenantId);

        ContactCustomField field = contactCustomFieldRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Custom field not found"));

        if (!field.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to field");
        }

        contactCustomFieldRepository.delete(field);
    }
}
