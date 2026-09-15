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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.records.dto.CrmRecordCreateRequest;
import com.shivang.crm.modules.records.dto.CrmRecordResponse;
import com.shivang.crm.modules.records.dto.CrmRecordUpdateRequest;
import com.shivang.crm.modules.records.service.CrmRecordService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/records")
@RequiredArgsConstructor
@Tag(name = "Records", description = "Tenant record instances")
public class CrmRecordController {

    private final CrmRecordService crmRecordService;
    private final TenantContext tenantContext;

    @PostMapping
    @Operation(summary = "Create record")
    public ResponseEntity<ApiResponse<CrmRecordResponse>> create(@Valid @RequestBody CrmRecordCreateRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = tenantContext.getUserId();
        CrmRecordResponse resp = crmRecordService.create(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
    }

    @GetMapping
    @Operation(summary = "List records (optionally by recordTypeId, search, sort)")
    public ResponseEntity<ApiResponse<java.util.List<CrmRecordResponse>>> list(
            @RequestParam(required = false) UUID recordTypeId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = tenantContext.requireTenantId();
        Page<CrmRecordResponse> p = crmRecordService.list(tenantId, recordTypeId, search, sort, direction, page, size);
        Map<String, Object> meta = Map.of(
                "page", p.getNumber(),
                "size", p.getSize(),
                "total", p.getTotalElements(),
                "totalPages", p.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(p.getContent(), meta));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get record by id")
    public ResponseEntity<ApiResponse<CrmRecordResponse>> get(@PathVariable UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        CrmRecordResponse resp = crmRecordService.getById(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update record (full replacement of data)")
    public ResponseEntity<ApiResponse<CrmRecordResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody CrmRecordUpdateRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = tenantContext.getUserId();
        CrmRecordResponse resp = crmRecordService.update(tenantId, userId, id, request);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Patch record data (merge)")
    public ResponseEntity<ApiResponse<CrmRecordResponse>> patch(@PathVariable UUID id,
            @RequestBody Map<String, Object> patch) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = tenantContext.getUserId();
        // Support both {data:{...}} and flat {...} payloads; the spec uses data map.
        // If patch contains "data" key, unwrap; otherwise treat whole body as data patch
        Map<String, Object> dataPatch;
        if (patch != null && patch.containsKey("data") && patch.get("data") instanceof Map) {
            //noinspection unchecked
            dataPatch = (Map<String, Object>) patch.get("data");
        } else {
            dataPatch = patch;
        }
        CrmRecordResponse resp = crmRecordService.patch(tenantId, userId, id, dataPatch);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete (archive) record")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = tenantContext.getUserId();
        crmRecordService.delete(tenantId, id, userId);
        return ResponseEntity.ok(ApiResponse.success("Record deleted"));
    }
}
