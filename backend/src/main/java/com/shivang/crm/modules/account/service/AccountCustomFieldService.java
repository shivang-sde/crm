package com.shivang.crm.modules.account.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.dto.AccountCustomFieldCreateRequest;
import com.shivang.crm.modules.account.dto.AccountCustomFieldResponse;
import com.shivang.crm.modules.account.entity.AccountCustomField;
import com.shivang.crm.modules.account.mapper.AccountCustomFieldMapper;
import com.shivang.crm.modules.account.repository.AccountCustomFieldRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountCustomFieldService {

    private final AccountCustomFieldRepository accountCustomFieldRepository;
    private final AccountCustomFieldMapper accountCustomFieldMapper;

    public AccountCustomFieldResponse createField(UUID tenantId, AccountCustomFieldCreateRequest request) {
        log.info("Creating account custom field: {} for tenant: {}", request.getFieldKey(), tenantId);

        AccountCustomField field = accountCustomFieldMapper.toEntity(request);
        field.setTenantId(tenantId);

        AccountCustomField savedField = accountCustomFieldRepository.save(field);
        return accountCustomFieldMapper.toResponse(savedField);
    }

    @Transactional(readOnly = true)
    public List<AccountCustomFieldResponse> getActiveFields(UUID tenantId) {
        log.info("Fetching active account custom fields for tenant: {}", tenantId);
        return accountCustomFieldMapper.toResponseList(accountCustomFieldRepository.findActiveFieldsByTenant(tenantId));
    }

    @Transactional(readOnly = true)
    public List<AccountCustomFieldResponse> getAllFields(UUID tenantId) {
        log.info("Fetching all account custom fields for tenant: {}", tenantId);
        return accountCustomFieldMapper.toResponseList(accountCustomFieldRepository.findByTenantIdOrderByDisplayOrder(tenantId));
    }

    public AccountCustomFieldResponse updateField(UUID id, UUID tenantId, AccountCustomFieldCreateRequest request) {
        log.info("Updating account custom field: {} for tenant: {}", id, tenantId);

        AccountCustomField field = accountCustomFieldRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Custom field not found"));

        field.setFieldLabel(request.getFieldLabel());
        field.setFieldType(request.getFieldType());
        field.setIsRequired(request.getIsRequired());
        field.setIsActive(request.getIsActive());
        field.setDisplayOrder(request.getDisplayOrder());

        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            field.setOptionsJson(request.getOptions());
        }

        AccountCustomField updatedField = accountCustomFieldRepository.save(field);
        return accountCustomFieldMapper.toResponse(updatedField);
    }

    public void deleteField(UUID id, UUID tenantId) {
        log.info("Deleting account custom field: {} for tenant: {}", id, tenantId);

        AccountCustomField field = accountCustomFieldRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Custom field not found"));

        accountCustomFieldRepository.delete(field);
    }
}
