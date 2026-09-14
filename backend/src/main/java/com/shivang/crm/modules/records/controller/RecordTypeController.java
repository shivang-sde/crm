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
import com.shivang.crm.modules.records.dto.RecordTypeCreateRequest;
import com.shivang.crm.modules.records.dto.RecordTypeResponse;
import com.shivang.crm.modules.records.dto.RecordTypeUpdateRequest;
import com.shivang.crm.modules.records.service.RecordTypeService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/record-types")
@RequiredArgsConstructor
@Tag(name = "Record Types", description = "Tenant-defined record type management")
public class RecordTypeController {

    private final RecordTypeService recordTypeService;
    private final TenantContext tenantContext;

    @PostMapping
    @Operation(summary = "Create record type")
    public ResponseEntity<ApiResponse<RecordTypeResponse>> create(@Valid @RequestBody RecordTypeCreateRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = tenantContext.getUserId();
        RecordTypeResponse resp = recordTypeService.create(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
    }

    @GetMapping
    @Operation(summary = "List record types")
    public ResponseEntity<ApiResponse<java.util.List<RecordTypeResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = tenantContext.requireTenantId();
        Page<RecordTypeResponse> p = recordTypeService.list(tenantId, page, size);
        Map<String, Object> meta = Map.of(
                "page", p.getNumber(),
                "size", p.getSize(),
                "total", p.getTotalElements(),
                "totalPages", p.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(p.getContent(), meta));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get record type by id")
    public ResponseEntity<ApiResponse<RecordTypeResponse>> get(@PathVariable UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        RecordTypeResponse resp = recordTypeService.getById(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update record type")
    public ResponseEntity<ApiResponse<RecordTypeResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody RecordTypeUpdateRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        RecordTypeResponse resp = recordTypeService.update(tenantId, id, request);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete (archive) record type")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = tenantContext.getUserId();
        recordTypeService.delete(tenantId, id, userId);
        return ResponseEntity.ok(ApiResponse.success("Record type deleted"));
    }
}
