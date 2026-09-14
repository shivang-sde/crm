package com.shivang.crm.modules.records.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.records.dto.RecordTypeCreateRequest;
import com.shivang.crm.modules.records.dto.RecordTypeResponse;
import com.shivang.crm.modules.records.dto.RecordTypeUpdateRequest;
import com.shivang.crm.modules.records.entity.RecordType;
import com.shivang.crm.modules.records.mapper.RecordTypeMapper;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RecordTypeService {

    private final RecordTypeRepository recordTypeRepository;
    private final RecordTypeMapper recordTypeMapper;

    public RecordTypeResponse create(UUID tenantId, UUID userId, RecordTypeCreateRequest request) {
        String key = request.getKey().trim().toLowerCase();
        String name = request.getName().trim();

        if (recordTypeRepository.existsByTenantIdAndKeyAndDeletedFalse(tenantId, key)) {
            throw new BusinessException("DUPLICATE_RECORD_TYPE_KEY", "Record type key '" + key + "' already exists");
        }
        if (recordTypeRepository.existsByTenantIdAndNameAndDeletedFalse(tenantId, name)) {
            throw new BusinessException("DUPLICATE_RECORD_TYPE_NAME", "Record type name '" + name + "' already exists");
        }

        RecordType entity = RecordType.builder()
                .tenantId(tenantId)
                .createdBy(userId)
                .ownerId(userId)
                .key(key)
                .name(name)
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        RecordType saved = recordTypeRepository.save(entity);
        log.info("Created record type {} for tenant {}", saved.getId(), tenantId);
        return recordTypeMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RecordTypeResponse getById(UUID tenantId, UUID id) {
        RecordType entity = recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", id.toString()));
        return recordTypeMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public Page<RecordTypeResponse> list(UUID tenantId, int page, int size) {
        Page<RecordType> p = recordTypeRepository.findByTenantIdAndDeletedFalse(tenantId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
        return p.map(recordTypeMapper::toResponse);
    }

    public RecordTypeResponse update(UUID tenantId, UUID id, RecordTypeUpdateRequest request) {
        RecordType entity = recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", id.toString()));

        if (request.getName() != null) {
            String newName = request.getName().trim();
            if (!newName.equals(entity.getName())
                    && recordTypeRepository.existsByTenantIdAndNameAndDeletedFalseAndIdNot(tenantId, newName, id)) {
                throw new BusinessException("DUPLICATE_RECORD_TYPE_NAME", "Record type name '" + newName + "' already exists");
            }
            entity.setName(newName);
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        entity.setUpdatedBy(com.shivang.crm.util.UserUtil.currentUserId());
        RecordType saved = recordTypeRepository.save(entity);
        return recordTypeMapper.toResponse(saved);
    }

    public void delete(UUID tenantId, UUID id, UUID userId) {
        RecordType entity = recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", id.toString()));
        entity.softDelete(userId);
        recordTypeRepository.save(entity);
        log.info("Soft-deleted record type {} for tenant {}", id, tenantId);
    }

    @Transactional(readOnly = true)
    public RecordType requireActiveType(UUID tenantId, UUID recordTypeId) {
        RecordType rt = recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));
        if (Boolean.FALSE.equals(rt.getIsActive())) {
            throw new BusinessException("RECORD_TYPE_INACTIVE", "Record type is inactive");
        }
        return rt;
    }
}
