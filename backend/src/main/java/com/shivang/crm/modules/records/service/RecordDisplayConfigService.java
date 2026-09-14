package com.shivang.crm.modules.records.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.records.dto.display.DisplayConfigRequest;
import com.shivang.crm.modules.records.dto.display.DisplayConfigResponse;
import com.shivang.crm.modules.records.entity.RecordDisplayConfig;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.repository.RecordDisplayConfigRepository;
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
public class RecordDisplayConfigService {

    private final RecordDisplayConfigRepository displayRepo;
    private final RecordTypeRepository recordTypeRepo;
    private final RecordFieldRepository recordFieldRepo;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public DisplayConfigResponse get(UUID tenantId, UUID recordTypeId) {
        var recordType = recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));

        var existing = displayRepo.findByRecordTypeIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId);

        List<RecordField> activeFields = recordFieldRepo
                .findActiveByRecordTypeIdAndTenantId(recordTypeId, tenantId);
        // For validation need all not-deleted fields
        List<RecordField> allFields = recordFieldRepo
                .findByRecordTypeIdAndTenantIdAndDeletedFalseOrderByDisplayOrder(recordTypeId, tenantId);

        if (existing.isEmpty()) {
            return buildDefaultResponse(tenantId, recordTypeId, activeFields);
        }

        RecordDisplayConfig cfg = existing.get();
        try {
            DisplayConfigRequest parsed = parseConfig(cfg.getConfig());
            // Return stored config even if some fields became inactive/deleted – frontend will mark invalid
            // But filter out completely deleted fieldIds for safety? Keep as stored per least-destructive policy.
            return DisplayConfigResponse.builder()
                    .recordTypeId(recordTypeId)
                    .tenantId(tenantId)
                    .list(parsed.getList())
                    .detail(parsed.getDetail())
                    .isCustom(true)
                    .createdAt(cfg.getCreatedAt())
                    .updatedAt(cfg.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse display config for type {}, returning default", recordTypeId, e);
            return buildDefaultResponse(tenantId, recordTypeId, activeFields);
        }
    }

    public DisplayConfigResponse put(UUID tenantId, UUID recordTypeId, DisplayConfigRequest request) {
        var recordType = recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));

        // Validate against existing fields (not deleted)
        List<RecordField> allFields = recordFieldRepo
                .findByRecordTypeIdAndTenantIdAndDeletedFalseOrderByDisplayOrder(recordTypeId, tenantId);
        Map<UUID, RecordField> fieldById = allFields.stream()
                .collect(Collectors.toMap(RecordField::getId, f -> f));

        // Normalize incoming request: ensure list/detail not null
        if (request == null) {
            throw new BusinessException("INVALID_DISPLAY_CONFIG", "Display config is required");
        }
        if (request.getList() == null) request.setList(DisplayConfigRequest.ListConfig.builder().columns(List.of()).build());
        if (request.getDetail() == null) request.setDetail(DisplayConfigRequest.DetailConfig.builder().sections(List.of()).build());
        if (request.getList().getColumns() == null) request.getList().setColumns(List.of());
        if (request.getDetail().getSections() == null) request.getDetail().setSections(List.of());

        // Validate list
        Set<UUID> seenList = new HashSet<>();
        for (var col : request.getList().getColumns()) {
            if (col.getFieldId() == null) throw new BusinessException("INVALID_DISPLAY_CONFIG", "List column fieldId is required");
            RecordField f = fieldById.get(col.getFieldId());
            if (f == null) {
                throw new BusinessException("INVALID_FIELD_REFERENCE", "List field " + col.getFieldId() + " does not belong to this record type or is deleted");
            }
            if (!seenList.add(col.getFieldId())) {
                throw new BusinessException("DUPLICATE_FIELD", "Duplicate field in list columns: " + col.getFieldId());
            }
        }

        // Validate detail
        Set<UUID> seenDetail = new HashSet<>();
        for (var sec : request.getDetail().getSections()) {
            if (sec.getName() == null || sec.getName().isBlank()) {
                throw new BusinessException("INVALID_DISPLAY_CONFIG", "Section name is required");
            }
            if (sec.getName().trim().length() > 100) {
                throw new BusinessException("INVALID_DISPLAY_CONFIG", "Section name too long");
            }
            if (sec.getFieldIds() == null) sec.setFieldIds(List.of());
            for (UUID fid : sec.getFieldIds()) {
                if (fid == null) throw new BusinessException("INVALID_DISPLAY_CONFIG", "Detail fieldId is required");
                RecordField f = fieldById.get(fid);
                if (f == null) {
                    throw new BusinessException("INVALID_FIELD_REFERENCE", "Detail field " + fid + " does not belong to this record type or is deleted");
                }
                if (!seenDetail.add(fid)) {
                    throw new BusinessException("DUPLICATE_FIELD", "Field " + fid + " appears in multiple detail sections");
                }
                // Also ensure not duplicate with list? For V1 we enforce one field = one detail location, but list vs detail may overlap – allowed. Only detail duplicate is forbidden. List duplicate already checked.
            }
            // Normalize section id if missing
            if (sec.getId() == null || sec.getId().isBlank()) {
                sec.setId(UUID.randomUUID().toString());
            }
            sec.setName(sec.getName().trim());
        }

        // Ensure no field appears twice across list? No – list vs detail overlap is allowed per spec (list shows field, detail also shows field). Only within list and within detail duplicates are forbidden. Already done.

        Map<String, Object> configMap = toConfigMap(request);

        var existing = displayRepo.findByRecordTypeIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId);
        RecordDisplayConfig entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setConfig(configMap);
        } else {
            entity = RecordDisplayConfig.builder()
                    .tenantId(tenantId)
                    .recordTypeId(recordTypeId)
                    .config(configMap)
                    .build();
        }
        RecordDisplayConfig saved = displayRepo.save(entity);
        log.info("Saved display config for type {} tenant {}", recordTypeId, tenantId);

        return DisplayConfigResponse.builder()
                .recordTypeId(recordTypeId)
                .tenantId(tenantId)
                .list(request.getList())
                .detail(request.getDetail())
                .isCustom(true)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    public void delete(UUID tenantId, UUID recordTypeId) {
        var recordType = recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordType", recordTypeId.toString()));
        var existing = displayRepo.findByRecordTypeIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId);
        existing.ifPresent(entity -> {
            // hard delete to reset to default (implicit)
            displayRepo.delete(entity);
            log.info("Reset display config for type {} tenant {}", recordTypeId, tenantId);
        });
    }

    private DisplayConfigResponse buildDefaultResponse(UUID tenantId, UUID recordTypeId, List<RecordField> activeFields) {
        // List: active fields ordered by displayOrder
        List<DisplayConfigRequest.ListConfig.Column> cols = new ArrayList<>();
        for (RecordField f : activeFields) {
            cols.add(DisplayConfigRequest.ListConfig.Column.builder().fieldId(f.getId()).build());
        }
        DisplayConfigRequest.ListConfig list = DisplayConfigRequest.ListConfig.builder().columns(cols).build();

        // Detail: single section "Details" containing all active fields ordered
        List<UUID> fieldIds = activeFields.stream().map(RecordField::getId).toList();
        DisplayConfigRequest.DetailConfig.Section section = DisplayConfigRequest.DetailConfig.Section.builder()
                .id("default")
                .name("Details")
                .fieldIds(fieldIds)
                .build();
        DisplayConfigRequest.DetailConfig detail = DisplayConfigRequest.DetailConfig.builder()
                .sections(fieldIds.isEmpty() ? List.of() : List.of(section))
                .build();

        return DisplayConfigResponse.builder()
                .recordTypeId(recordTypeId)
                .tenantId(tenantId)
                .list(list)
                .detail(detail)
                .isCustom(false)
                .createdAt(null)
                .updatedAt(null)
                .build();
    }

    private DisplayConfigRequest parseConfig(Map<String, Object> configMap) {
        if (configMap == null || configMap.isEmpty()) {
            return DisplayConfigRequest.builder()
                    .list(DisplayConfigRequest.ListConfig.builder().columns(List.of()).build())
                    .detail(DisplayConfigRequest.DetailConfig.builder().sections(List.of()).build())
                    .build();
        }
        // Use ObjectMapper to convert
        DisplayConfigRequest req = objectMapper.convertValue(configMap, DisplayConfigRequest.class);
        if (req.getList() == null) req.setList(DisplayConfigRequest.ListConfig.builder().columns(List.of()).build());
        if (req.getDetail() == null) req.setDetail(DisplayConfigRequest.DetailConfig.builder().sections(List.of()).build());
        if (req.getList().getColumns() == null) req.getList().setColumns(List.of());
        if (req.getDetail().getSections() == null) req.getDetail().setSections(List.of());
        for (var sec : req.getDetail().getSections()) {
            if (sec.getFieldIds() == null) sec.setFieldIds(List.of());
        }
        return req;
    }

    private Map<String, Object> toConfigMap(DisplayConfigRequest request) {
        // Convert to Map for JSONB storage
        return objectMapper.convertValue(request, new TypeReference<Map<String, Object>>() {});
    }
}
