package com.shivang.crm.modules.records.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.records.dto.RecordMappingProfileCreateRequest;
import com.shivang.crm.modules.records.dto.RecordMappingProfileResponse;
import com.shivang.crm.modules.records.dto.RecordMappingProfileUpdateRequest;
import com.shivang.crm.modules.records.entity.MappingProfileMode;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordMappingProfile;
import com.shivang.crm.modules.records.mapper.RecordMappingProfileMapper;
import com.shivang.crm.modules.records.repository.RecordFieldRepository;
import com.shivang.crm.modules.records.repository.RecordMappingProfileRepository;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RecordMappingProfileService {

    private final RecordMappingProfileRepository mappingRepo;
    private final RecordTypeRepository recordTypeRepo;
    private final RecordFieldRepository recordFieldRepo;
    private final RecordMappingProfileMapper mapper;

    public RecordMappingProfileResponse create(UUID tenantId, UUID userId, RecordMappingProfileCreateRequest req) {
        UUID recordTypeId = req.getRecordTypeId();
        var recordType = recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));

        String key = req.getMappingKey().trim().toLowerCase();
        if (mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantId, key)) {
            throw new BusinessException("DUPLICATE_MAPPING_KEY", "Mapping key '" + key + "' already exists");
        }

        MappingProfileMode mode = parseMode(req.getMode());
        Map<String, Object> config = req.getConfiguration() != null ? new HashMap<>(req.getConfiguration()) : new HashMap<>();

        validateConfiguration(tenantId, recordTypeId, mode, config);

        RecordMappingProfile entity = RecordMappingProfile.builder()
                .tenantId(tenantId)
                .createdBy(userId)
                .ownerId(userId)
                .recordTypeId(recordTypeId)
                .mappingKey(key)
                .name(req.getName().trim())
                .description(req.getDescription())
                .mode(mode)
                .configuration(config)
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .build();

        RecordMappingProfile saved = mappingRepo.save(entity);
        log.info("Created mapping profile {} tenant {}", saved.getId(), tenantId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RecordMappingProfileResponse get(UUID tenantId, UUID id) {
        RecordMappingProfile e = mappingRepo.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordMappingProfile", id.toString()));
        return toResponse(e);
    }

    @Transactional(readOnly = true)
    public Page<RecordMappingProfileResponse> list(UUID tenantId, UUID recordTypeId, int page, int size) {
        Page<RecordMappingProfile> p;
        if (recordTypeId != null) {
            recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                    .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));
            p = mappingRepo.findByTenantIdAndRecordTypeIdAndDeletedFalse(tenantId, recordTypeId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        } else {
            p = mappingRepo.findByTenantIdAndDeletedFalse(tenantId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        }
        return p.map(this::toResponse);
    }

    public RecordMappingProfileResponse update(UUID tenantId, UUID id, RecordMappingProfileUpdateRequest req) {
        RecordMappingProfile entity = mappingRepo.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordMappingProfile", id.toString()));

        if (req.getName() != null) {
            if (req.getName().isBlank()) throw new BusinessException("INVALID_NAME", "Name cannot be blank");
            entity.setName(req.getName().trim());
        }
        if (req.getDescription() != null) entity.setDescription(req.getDescription());

        MappingProfileMode newMode = entity.getMode();
        if (req.getMode() != null) {
            newMode = parseMode(req.getMode());
            entity.setMode(newMode);
        }
        if (req.getConfiguration() != null) {
            Map<String, Object> config = new HashMap<>(req.getConfiguration());
            validateConfiguration(tenantId, entity.getRecordTypeId(), newMode, config);
            entity.setConfiguration(config);
        } else if (req.getMode() != null) {
            // mode changed but no config provided -> re-validate existing config against new mode
            validateConfiguration(tenantId, entity.getRecordTypeId(), newMode, entity.getConfiguration());
        }

        if (req.getIsActive() != null) entity.setIsActive(req.getIsActive());

        RecordMappingProfile saved = mappingRepo.save(entity);
        return toResponse(saved);
    }

    public void delete(UUID tenantId, UUID id, UUID userId) {
        RecordMappingProfile entity = mappingRepo.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordMappingProfile", id.toString()));
        entity.softDelete(userId);
        mappingRepo.save(entity);
        log.info("Soft-deleted mapping profile {} tenant {}", id, tenantId);
    }

    @Transactional(readOnly = true)
    public RecordMappingProfile requireProfile(UUID tenantId, UUID id) {
        return mappingRepo.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordMappingProfile", id.toString()));
    }

    @SuppressWarnings("unchecked")
    private void validateConfiguration(UUID tenantId, UUID recordTypeId, MappingProfileMode mode, Map<String, Object> config) {
        if (mode == MappingProfileMode.DIRECT) {
            if (config == null) return;
            Object mappingsObj = config.get("mappings");
            if (mappingsObj == null) return;
            if (mappingsObj instanceof List) {
                List<?> list = (List<?>) mappingsObj;
                if (!list.isEmpty()) throw new BusinessException("INVALID_CONFIGURATION", "DIRECT mode must not contain mappings");
            }
            return;
        }

        if (config == null) throw new BusinessException("INVALID_CONFIGURATION", "CUSTOM mode requires configuration with mappings");
        Object mappingsObj = config.get("mappings");
        if (!(mappingsObj instanceof List)) throw new BusinessException("INVALID_CONFIGURATION", "CUSTOM mode requires mappings list");
        List<Map<String, Object>> mappings = (List<Map<String, Object>>) mappingsObj;
        if (mappings.isEmpty()) throw new BusinessException("INVALID_CONFIGURATION", "CUSTOM mode requires at least one mapping");
        if (mappings.size() > RecordMappingService.MAX_MAPPINGS) {
            throw new BusinessException("TOO_MANY_MAPPINGS", "Too many mappings: " + mappings.size() + " max " + RecordMappingService.MAX_MAPPINGS);
        }

        List<RecordField> fields = recordFieldRepo.findByRecordTypeIdAndTenantIdAndDeletedFalseOrderByDisplayOrder(recordTypeId, tenantId);
        Map<UUID, RecordField> byId = fields.stream().collect(Collectors.toMap(RecordField::getId, f -> f));
        Map<String, RecordField> byKey = fields.stream().collect(Collectors.toMap(RecordField::getFieldKey, f -> f, (a,b)->a));

        Set<UUID> seenTarget = new HashSet<>();
        Set<String> seenSource = new HashSet<>();

        for (Map<String, Object> m : mappings) {
            Object sourceObj = m.get("source");
            Object targetFieldIdObj = m.get("targetFieldId");
            if (targetFieldIdObj == null && m.get("targetFieldKey") != null) {
                String key = m.get("targetFieldKey").toString().trim();
                RecordField f = byKey.get(key);
                if (f == null) throw new BusinessException("INVALID_FIELD_REFERENCE", "Target field key '" + key + "' does not belong to this record type");
                targetFieldIdObj = f.getId().toString();
                m.put("targetFieldId", targetFieldIdObj);
            }
            if (sourceObj == null || sourceObj.toString().isBlank()) {
                throw new BusinessException("INVALID_CONFIGURATION", "Mapping source is required");
            }
            if (targetFieldIdObj == null || targetFieldIdObj.toString().isBlank()) {
                throw new BusinessException("INVALID_CONFIGURATION", "Mapping targetFieldId is required");
            }
            String source = sourceObj.toString().trim();
            String targetIdStr = targetFieldIdObj.toString().trim();
            UUID targetId;
            try { targetId = UUID.fromString(targetIdStr); } catch (Exception e) {
                throw new BusinessException("INVALID_FIELD_REFERENCE", "Invalid targetFieldId '" + targetIdStr + "'");
            }
            RecordField field = byId.get(targetId);
            if (field == null) {
                throw new BusinessException("INVALID_FIELD_REFERENCE", "Target field " + targetId + " does not belong to this record type or is deleted");
            }
            if (Boolean.FALSE.equals(field.getIsActive())) {
                throw new BusinessException("FIELD_INACTIVE", "Target field '" + field.getFieldKey() + "' is inactive");
            }
            if (!seenTarget.add(targetId)) {
                throw new BusinessException("DUPLICATE_TARGET", "Duplicate target field mapping for '" + field.getFieldKey() + "'");
            }
            String normalizedSource = source.trim();
            if (!seenSource.add(normalizedSource)) {
                throw new BusinessException("DUPLICATE_SOURCE", "Duplicate source path '" + normalizedSource + "'");
            }
            // hardened path validation
            if (normalizedSource.length() > RecordMappingService.MAX_PATH_LENGTH) {
                throw new BusinessException("INVALID_SOURCE_PATH", "Source path too long (max " + RecordMappingService.MAX_PATH_LENGTH + "): '" + normalizedSource + "'");
            }
            String[] parts = normalizedSource.split("\\.", -1);
            if (parts.length > RecordMappingService.MAX_PATH_DEPTH) {
                throw new BusinessException("INVALID_SOURCE_PATH", "Source path too deep (max " + RecordMappingService.MAX_PATH_DEPTH + "): '" + normalizedSource + "'");
            }
            for (String p : parts) if (p.isEmpty()) {
                throw new BusinessException("INVALID_SOURCE_PATH", "Invalid source path '" + normalizedSource + "' (empty segment)");
            }
            if (normalizedSource.startsWith(".") || normalizedSource.endsWith(".") || normalizedSource.contains("..")) {
                throw new BusinessException("INVALID_SOURCE_PATH", "Invalid source path '" + normalizedSource + "'");
            }
            // transform validation
            Object transformObj = m.get("transform");
            if (transformObj != null && !transformObj.toString().isBlank()) {
                String tRaw = transformObj.toString().trim();
                com.shivang.crm.modules.records.entity.MappingTransform transform;
                try { transform = com.shivang.crm.modules.records.entity.MappingTransform.fromString(tRaw); }
                catch (Exception e) { throw new BusinessException("INVALID_TRANSFORM", "Invalid transform '" + tRaw + "'"); }
                if (transform != com.shivang.crm.modules.records.entity.MappingTransform.IDENTITY) {
                    validateTransformCompatibility(transform, field);
                }
            }
        }
    }

    private void validateTransformCompatibility(com.shivang.crm.modules.records.entity.MappingTransform transform, RecordField field) {
        var ft = field.getFieldType();
        switch (ft) {
            case TEXT, LONG_TEXT, PHONE, EMAIL, URL:
                if (transform != com.shivang.crm.modules.records.entity.MappingTransform.STRING && transform != com.shivang.crm.modules.records.entity.MappingTransform.IDENTITY)
                    throw new BusinessException("INCOMPATIBLE_TRANSFORM", "Transform " + transform + " not compatible with field type " + ft + " for '" + field.getFieldKey() + "'");
                break;
            case INTEGER:
                if (transform != com.shivang.crm.modules.records.entity.MappingTransform.INTEGER && transform != com.shivang.crm.modules.records.entity.MappingTransform.STRING && transform != com.shivang.crm.modules.records.entity.MappingTransform.IDENTITY)
                    throw new BusinessException("INCOMPATIBLE_TRANSFORM", "Transform " + transform + " not compatible with INTEGER field '" + field.getFieldKey() + "'");
                break;
            case DECIMAL:
                if (transform != com.shivang.crm.modules.records.entity.MappingTransform.DECIMAL && transform != com.shivang.crm.modules.records.entity.MappingTransform.STRING && transform != com.shivang.crm.modules.records.entity.MappingTransform.IDENTITY)
                    throw new BusinessException("INCOMPATIBLE_TRANSFORM", "Transform " + transform + " not compatible with DECIMAL field '" + field.getFieldKey() + "'");
                break;
            case BOOLEAN:
                if (transform != com.shivang.crm.modules.records.entity.MappingTransform.BOOLEAN && transform != com.shivang.crm.modules.records.entity.MappingTransform.STRING && transform != com.shivang.crm.modules.records.entity.MappingTransform.IDENTITY)
                    throw new BusinessException("INCOMPATIBLE_TRANSFORM", "Transform " + transform + " not compatible with BOOLEAN field '" + field.getFieldKey() + "'");
                break;
            case DATE:
                if (transform != com.shivang.crm.modules.records.entity.MappingTransform.DATE && transform != com.shivang.crm.modules.records.entity.MappingTransform.STRING && transform != com.shivang.crm.modules.records.entity.MappingTransform.IDENTITY)
                    throw new BusinessException("INCOMPATIBLE_TRANSFORM", "Transform " + transform + " not compatible with DATE field '" + field.getFieldKey() + "'");
                break;
            case DATETIME:
                if (transform != com.shivang.crm.modules.records.entity.MappingTransform.DATETIME && transform != com.shivang.crm.modules.records.entity.MappingTransform.STRING && transform != com.shivang.crm.modules.records.entity.MappingTransform.IDENTITY)
                    throw new BusinessException("INCOMPATIBLE_TRANSFORM", "Transform " + transform + " not compatible with DATETIME field '" + field.getFieldKey() + "'");
                break;
            case ENUM:
                if (transform != com.shivang.crm.modules.records.entity.MappingTransform.ENUM && transform != com.shivang.crm.modules.records.entity.MappingTransform.STRING && transform != com.shivang.crm.modules.records.entity.MappingTransform.IDENTITY)
                    throw new BusinessException("INCOMPATIBLE_TRANSFORM", "Transform " + transform + " not compatible with ENUM field '" + field.getFieldKey() + "'");
                break;
            case JSON:
                if (transform != com.shivang.crm.modules.records.entity.MappingTransform.IDENTITY)
                    throw new BusinessException("INCOMPATIBLE_TRANSFORM", "Only IDENTITY is allowed for JSON field '" + field.getFieldKey() + "'");
                break;
            default:
                break;
        }
    }

    private MappingProfileMode parseMode(String raw) {
        try { return MappingProfileMode.fromString(raw); }
        catch (Exception e) { throw new BusinessException("INVALID_MODE", "Invalid mapping mode '" + raw + "'. Allowed: DIRECT, CUSTOM"); }
    }

    private RecordMappingProfileResponse toResponse(RecordMappingProfile e) {
        RecordMappingProfileResponse r = mapper.toResponse(e);
        var rt = recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(e.getRecordTypeId(), e.getTenantId()).orElse(null);
        if (rt != null) {
            r.setRecordTypeKey(rt.getKey());
            r.setRecordTypeName(rt.getName());
        }
        return r;
    }
}
