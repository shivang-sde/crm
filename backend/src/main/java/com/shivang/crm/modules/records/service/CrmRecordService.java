package com.shivang.crm.modules.records.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.records.dto.CrmRecordCreateRequest;
import com.shivang.crm.modules.records.dto.CrmRecordResponse;
import com.shivang.crm.modules.records.dto.CrmRecordUpdateRequest;
import com.shivang.crm.modules.records.entity.CrmRecord;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordType;
import com.shivang.crm.modules.records.mapper.CrmRecordMapper;
import com.shivang.crm.modules.records.repository.CrmRecordRepository;
import com.shivang.crm.modules.records.repository.RecordFieldRepository;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CrmRecordService {

    private final CrmRecordRepository crmRecordRepository;
    private final RecordTypeRepository recordTypeRepository;
    private final RecordFieldRepository recordFieldRepository;
    private final RecordValidationService validationService;
    private final CrmRecordMapper crmRecordMapper;

    public CrmRecordResponse create(UUID tenantId, UUID userId, CrmRecordCreateRequest request) {
        UUID recordTypeId = request.getRecordTypeId();
        RecordType recordType = recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));
        if (Boolean.FALSE.equals(recordType.getIsActive())) {
            throw new BusinessException("RECORD_TYPE_INACTIVE", "Record type is inactive");
        }

        List<RecordField> activeFields = recordFieldRepository
                .findActiveByRecordTypeIdAndTenantId(recordTypeId, tenantId);

        Map<String, Object> data = request.getData() != null ? new HashMap<>(request.getData()) : new HashMap<>();

        // Apply defaults for missing optional fields if defaultValue present
        for (RecordField f : activeFields) {
            if (!data.containsKey(f.getFieldKey()) && f.getDefaultValue() != null && !f.getDefaultValue().isBlank()) {
                // keep string default; validation will handle type-specific later if needed
                // For foundation, defaults are stored as string; only ENUM default is validated strictly
                // We don't auto-coerce; store as string for TEXT family
            }
        }

        validationService.validateAndNormalize(tenantId, activeFields, data);

        CrmRecord entity = CrmRecord.builder()
                .tenantId(tenantId)
                .recordTypeId(recordTypeId)
                .data(data)
                .createdBy(userId)
                .ownerId(userId)
                .build();

        CrmRecord saved = crmRecordRepository.save(entity);
        log.info("Created record {} type {} tenant {}", saved.getId(), recordTypeId, tenantId);
        return toResponse(saved, recordType);
    }

    @Transactional(readOnly = true)
    public CrmRecordResponse getById(UUID tenantId, UUID id) {
        CrmRecord rec = crmRecordRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("CrmRecord", id.toString()));
        RecordType rt = recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(rec.getRecordTypeId(), tenantId)
                .orElse(null);
        return toResponse(rec, rt);
    }

    @Transactional(readOnly = true)
    public Page<CrmRecordResponse> list(UUID tenantId, UUID recordTypeId, int page, int size) {
        Page<CrmRecord> p;
        if (recordTypeId != null) {
            // verify type belongs to tenant
            recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                    .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));
            p = crmRecordRepository.findByTenantIdAndRecordTypeIdAndDeletedFalse(tenantId, recordTypeId,
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        } else {
            p = crmRecordRepository.findByTenantIdAndDeletedFalse(tenantId,
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        }
        return p.map(rec -> {
            RecordType rt = recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(rec.getRecordTypeId(), tenantId).orElse(null);
            return toResponse(rec, rt);
        });
    }

    public CrmRecordResponse update(UUID tenantId, UUID userId, UUID id, CrmRecordUpdateRequest request) {
        CrmRecord rec = crmRecordRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("CrmRecord", id.toString()));

        RecordType rt = recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(rec.getRecordTypeId(), tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", rec.getRecordTypeId().toString()));

        List<RecordField> activeFields = recordFieldRepository
                .findActiveByRecordTypeIdAndTenantId(rec.getRecordTypeId(), tenantId);

        Map<String, Object> newData = request.getData() != null ? new HashMap<>(request.getData()) : new HashMap<>();
        validationService.validateAndNormalize(tenantId, activeFields, newData);

        rec.setData(newData);
        rec.setUpdatedBy(userId);
        CrmRecord saved = crmRecordRepository.save(rec);
        return toResponse(saved, rt);
    }

    // PATCH semantics: partial update - merge patch
    public CrmRecordResponse patch(UUID tenantId, UUID userId, UUID id, Map<String, Object> patchData) {
        CrmRecord rec = crmRecordRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("CrmRecord", id.toString()));
        RecordType rt = recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(rec.getRecordTypeId(), tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", rec.getRecordTypeId().toString()));
        List<RecordField> activeFields = recordFieldRepository
                .findActiveByRecordTypeIdAndTenantId(rec.getRecordTypeId(), tenantId);

        Map<String, Object> merged = new HashMap<>(rec.getData() != null ? rec.getData() : Map.of());
        if (patchData != null) {
            merged.putAll(patchData);
        }
        validationService.validateAndNormalize(tenantId, activeFields, merged);
        rec.setData(merged);
        rec.setUpdatedBy(userId);
        CrmRecord saved = crmRecordRepository.save(rec);
        return toResponse(saved, rt);
    }

    public void delete(UUID tenantId, UUID id, UUID userId) {
        CrmRecord rec = crmRecordRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("CrmRecord", id.toString()));
        rec.softDelete(userId);
        crmRecordRepository.save(rec);
        log.info("Soft-deleted record {} tenant {}", id, tenantId);
    }

    private CrmRecordResponse toResponse(CrmRecord rec, RecordType rt) {
        CrmRecordResponse resp = crmRecordMapper.toResponse(rec);
        if (rt != null) {
            resp.setRecordTypeKey(rt.getKey());
            resp.setRecordTypeName(rt.getName());
        }
        return resp;
    }
}
