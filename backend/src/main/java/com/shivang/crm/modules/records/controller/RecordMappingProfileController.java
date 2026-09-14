package com.shivang.crm.modules.records.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.records.dto.MappingPreviewRequest;
import com.shivang.crm.modules.records.dto.MappingPreviewResponse;
import com.shivang.crm.modules.records.dto.RecordMappingProfileCreateRequest;
import com.shivang.crm.modules.records.dto.RecordMappingProfileResponse;
import com.shivang.crm.modules.records.dto.RecordMappingProfileUpdateRequest;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.repository.RecordFieldRepository;
import com.shivang.crm.modules.records.service.RecordMappingProfileService;
import com.shivang.crm.modules.records.service.RecordMappingService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/record-mapping-profiles")
@RequiredArgsConstructor
@Tag(name = "Record Mapping Profiles", description = "Tenant mapping profiles for record ingestion")
public class RecordMappingProfileController {

    private final RecordMappingProfileService mappingService;
    private final RecordMappingService recordMappingService;
    private final RecordFieldRepository recordFieldRepository;
    private final TenantContext tenantContext;

    @PostMapping
    @Operation(summary = "Create mapping profile")
    public ResponseEntity<ApiResponse<RecordMappingProfileResponse>> create(@Valid @RequestBody RecordMappingProfileCreateRequest req) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = tenantContext.getUserId();
        RecordMappingProfileResponse resp = mappingService.create(tenantId, userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
    }

    @GetMapping
    @Operation(summary = "List mapping profiles")
    public ResponseEntity<ApiResponse<java.util.List<RecordMappingProfileResponse>>> list(
            @RequestParam(required = false) UUID recordTypeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = tenantContext.requireTenantId();
        Page<RecordMappingProfileResponse> p = mappingService.list(tenantId, recordTypeId, page, size);
        Map<String, Object> meta = Map.of("page", p.getNumber(), "size", p.getSize(), "total", p.getTotalElements(), "totalPages", p.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(p.getContent(), meta));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get mapping profile")
    public ResponseEntity<ApiResponse<RecordMappingProfileResponse>> get(@PathVariable UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        RecordMappingProfileResponse resp = mappingService.get(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update mapping profile")
    public ResponseEntity<ApiResponse<RecordMappingProfileResponse>> update(@PathVariable UUID id, @Valid @RequestBody RecordMappingProfileUpdateRequest req) {
        UUID tenantId = tenantContext.requireTenantId();
        RecordMappingProfileResponse resp = mappingService.update(tenantId, id, req);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete mapping profile")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = tenantContext.getUserId();
        mappingService.delete(tenantId, id, userId);
        return ResponseEntity.ok(ApiResponse.success("Mapping profile deleted"));
    }

    @PostMapping("/{id}/preview")
    @Operation(summary = "Preview mapping without persistence")
    public ResponseEntity<ApiResponse<MappingPreviewResponse>> preview(
            @PathVariable UUID id,
            @RequestBody MappingPreviewRequest req) {
        UUID tenantId = tenantContext.requireTenantId();
        var profile = mappingService.requireProfile(tenantId, id);
        var fields = recordFieldRepository.findByRecordTypeIdAndTenantIdAndDeletedFalseOrderByDisplayOrder(
                profile.getRecordTypeId(), tenantId);
        java.util.Map<UUID, RecordField> byId = new java.util.HashMap<>();
        for (RecordField f : fields) byId.put(f.getId(), f);
        java.util.Map<String, Object> payload = req != null && req.getPayload() != null ? req.getPayload() : java.util.Map.of();
        java.util.Map<String, Object> mapped = recordMappingService.map(profile, payload, byId);
        return ResponseEntity.ok(ApiResponse.success(MappingPreviewResponse.builder().mappedData(mapped).build()));
    }
}
