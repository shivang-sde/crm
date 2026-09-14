package com.shivang.crm.modules.records.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.records.dto.RecordFieldCreateRequest;
import com.shivang.crm.modules.records.dto.RecordFieldResponse;
import com.shivang.crm.modules.records.dto.RecordFieldUpdateRequest;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordFieldType;
import com.shivang.crm.modules.records.mapper.RecordFieldMapper;
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
public class RecordFieldService {

    private final RecordFieldRepository recordFieldRepository;
    private final RecordTypeRepository recordTypeRepository;
    private final RecordFieldMapper recordFieldMapper;

    public RecordFieldResponse create(UUID tenantId, UUID recordTypeId, RecordFieldCreateRequest request) {
        var recordType = recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));

        String key = request.getFieldKey().trim().toLowerCase();
        RecordFieldType type = parseType(request.getFieldType());

        if (recordFieldRepository.existsByRecordTypeIdAndFieldKeyAndDeletedFalse(recordTypeId, key)) {
            throw new BusinessException("DUPLICATE_FIELD_KEY", "Field key '" + key + "' already exists for this record type");
        }

        validateEnumOptions(type, request.getOptions());
        validateReference(type, request.getReferenceEntityType());

        RecordField field = RecordField.builder()
                .tenantId(tenantId)
                .recordTypeId(recordType.getId())
                .fieldKey(key)
                .fieldLabel(request.getFieldLabel().trim())
                .fieldType(type)
                .isRequired(request.getIsRequired() != null ? request.getIsRequired() : false)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .optionsJson(type == RecordFieldType.ENUM ? normalizeOptions(request.getOptions()) : null)
                .defaultValue(request.getDefaultValue())
                .referenceEntityType(type == RecordFieldType.REFERENCE ? normalizeReference(request.getReferenceEntityType()) : null)
                .build();

        // Validate default value if present
        if (field.getDefaultValue() != null && !field.getDefaultValue().isBlank()) {
            validateDefaultValue(field, field.getDefaultValue());
        }

        RecordField saved = recordFieldRepository.save(field);
        log.info("Created field {} for record type {}", saved.getId(), recordTypeId);
        return recordFieldMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RecordFieldResponse> list(UUID tenantId, UUID recordTypeId) {
        recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));
        List<RecordField> fields = recordFieldRepository
                .findByRecordTypeIdAndTenantIdAndDeletedFalseOrderByDisplayOrder(recordTypeId, tenantId);
        return fields.stream().map(recordFieldMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RecordField> listActiveFields(UUID tenantId, UUID recordTypeId) {
        return recordFieldRepository.findActiveByRecordTypeIdAndTenantId(recordTypeId, tenantId);
    }

    public RecordFieldResponse update(UUID tenantId, UUID recordTypeId, UUID fieldId, RecordFieldUpdateRequest request) {
        recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));

        RecordField field = recordFieldRepository.findByIdAndRecordTypeIdAndTenantIdAndDeletedFalse(fieldId, recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordField", fieldId.toString()));

        if (request.getFieldLabel() != null) {
            field.setFieldLabel(request.getFieldLabel().trim());
        }
        if (request.getFieldType() != null) {
            RecordFieldType newType = parseType(request.getFieldType());
            field.setFieldType(newType);
            // handle reference change with type
            if (newType == RecordFieldType.REFERENCE) {
                validateReference(newType, request.getReferenceEntityType());
                field.setReferenceEntityType(normalizeReference(request.getReferenceEntityType()));
            } else {
                field.setReferenceEntityType(null);
            }
        }
        if (request.getReferenceEntityType() != null && field.getFieldType() == RecordFieldType.REFERENCE) {
            validateReference(field.getFieldType(), request.getReferenceEntityType());
            field.setReferenceEntityType(normalizeReference(request.getReferenceEntityType()));
        } else if (request.getReferenceEntityType() != null) {
            throw new BusinessException("INVALID_FIELD_CONFIG", "Only REFERENCE fields may have referenceEntityType");
        }
        if (request.getIsRequired() != null) {
            field.setIsRequired(request.getIsRequired());
        }
        if (request.getIsActive() != null) {
            field.setIsActive(request.getIsActive());
        }
        if (request.getDisplayOrder() != null) {
            field.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getOptions() != null) {
            validateEnumOptions(field.getFieldType(), request.getOptions());
            field.setOptionsJson(field.getFieldType() == RecordFieldType.ENUM ? normalizeOptions(request.getOptions()) : null);
        }
        if (request.getDefaultValue() != null) {
            field.setDefaultValue(request.getDefaultValue());
            if (!field.getDefaultValue().isBlank()) {
                validateDefaultValue(field, field.getDefaultValue());
            }
        }

        // Re-validate enum options if type is ENUM
        if (field.getFieldType() == RecordFieldType.ENUM) {
            validateEnumOptions(field.getFieldType(), field.getOptionsJson());
        } else {
            field.setOptionsJson(null);
        }
        // Re-validate reference
        if (field.getFieldType() == RecordFieldType.REFERENCE) {
            validateReference(field.getFieldType(), field.getReferenceEntityType());
        } else {
            field.setReferenceEntityType(null);
        }

        RecordField saved = recordFieldRepository.save(field);
        return recordFieldMapper.toResponse(saved);
    }

    public void delete(UUID tenantId, UUID recordTypeId, UUID fieldId, UUID userId) {
        recordTypeRepository.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));

        RecordField field = recordFieldRepository.findByIdAndRecordTypeIdAndTenantIdAndDeletedFalse(fieldId, recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordField", fieldId.toString()));

        // Soft-delete: deactivate and mark deleted, preserve historical data in records' JSON
        field.softDelete(userId);
        field.setIsActive(false);
        recordFieldRepository.save(field);
        log.info("Soft-deleted field {} for record type {} tenant {}", fieldId, recordTypeId, tenantId);
    }

    private RecordFieldType parseType(String raw) {
        try {
            return RecordFieldType.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_FIELD_TYPE", "Invalid field type '" + raw + "'. Allowed: TEXT, LONG_TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, ENUM, URL, PHONE, EMAIL, JSON, REFERENCE");
        }
    }

    private void validateReference(RecordFieldType type, String ref) {
        if (type == RecordFieldType.REFERENCE) {
            if (ref == null || ref.isBlank()) {
                throw new BusinessException("INVALID_FIELD_CONFIG", "REFERENCE field requires referenceEntityType (LEAD, CONTACT, ACCOUNT, DEAL, TASK, MEETING, CALL)");
            }
            String norm = ref.trim().toUpperCase();
            if (!java.util.Set.of("LEAD","CONTACT","ACCOUNT","DEAL","TASK","MEETING","CALL").contains(norm)) {
                throw new BusinessException("INVALID_FIELD_CONFIG", "Invalid referenceEntityType '" + ref + "'");
            }
            // REFERENCE fields must not have enum options
            // options already validated separately
        } else {
            if (ref != null && !ref.isBlank()) {
                throw new BusinessException("INVALID_FIELD_CONFIG", "Only REFERENCE fields may have referenceEntityType");
            }
        }
    }

    private String normalizeReference(String ref) {
        return ref == null ? null : ref.trim().toUpperCase();
    }

    private void validateEnumOptions(RecordFieldType type, List<String> options) {
        if (type == RecordFieldType.ENUM) {
            if (options == null || options.isEmpty()) {
                throw new BusinessException("INVALID_FIELD_CONFIG", "ENUM field requires non-empty options");
            }
            for (String opt : options) {
                if (opt == null || opt.isBlank()) {
                    throw new BusinessException("INVALID_FIELD_CONFIG", "ENUM options must be non-blank strings");
                }
                if (!opt.matches("^[A-Za-z0-9_\\- ]+$")) {
                    // allow simple values; keep lenient
                }
            }
            long distinct = options.stream().map(s -> s.trim()).distinct().count();
            if (distinct != options.size()) {
                throw new BusinessException("INVALID_FIELD_CONFIG", "ENUM options must be unique");
            }
        } else {
            if (options != null && !options.isEmpty()) {
                throw new BusinessException("INVALID_FIELD_CONFIG", "Only ENUM fields may have options");
            }
        }
    }

    private List<String> normalizeOptions(List<String> options) {
        if (options == null) return null;
        return options.stream().map(String::trim).toList();
    }

    private void validateDefaultValue(RecordField field, String defaultValue) {
        // Only validate ENUM default against options
        if (field.getFieldType() == RecordFieldType.ENUM) {
            List<String> opts = field.getOptionsJson();
            if (opts != null && !opts.contains(defaultValue.trim())) {
                throw new BusinessException("INVALID_DEFAULT_VALUE", "Default value '" + defaultValue + "' is not in ENUM options");
            }
        }
        // For other types, basic non-empty is sufficient for foundation
    }
}
