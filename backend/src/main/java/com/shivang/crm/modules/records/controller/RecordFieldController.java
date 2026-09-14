package com.shivang.crm.modules.records.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.records.dto.RecordFieldCreateRequest;
import com.shivang.crm.modules.records.dto.RecordFieldResponse;
import com.shivang.crm.modules.records.dto.RecordFieldUpdateRequest;
import com.shivang.crm.modules.records.service.RecordFieldService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/record-types/{recordTypeId}/fields")
@RequiredArgsConstructor
@Tag(name = "Record Fields", description = "Tenant-defined fields for a record type")
public class RecordFieldController {

    private final RecordFieldService recordFieldService;
    private final TenantContext tenantContext;

    @PostMapping
    @Operation(summary = "Create field for record type")
    public ResponseEntity<ApiResponse<RecordFieldResponse>> create(
            @PathVariable UUID recordTypeId,
            @Valid @RequestBody RecordFieldCreateRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        RecordFieldResponse resp = recordFieldService.create(tenantId, recordTypeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
    }

    @GetMapping
    @Operation(summary = "List fields for record type")
    public ResponseEntity<ApiResponse<List<RecordFieldResponse>>> list(@PathVariable UUID recordTypeId) {
        UUID tenantId = tenantContext.requireTenantId();
        List<RecordFieldResponse> fields = recordFieldService.list(tenantId, recordTypeId);
        return ResponseEntity.ok(ApiResponse.success(fields));
    }

    @PatchMapping("/{fieldId}")
    @Operation(summary = "Update field")
    public ResponseEntity<ApiResponse<RecordFieldResponse>> update(
            @PathVariable UUID recordTypeId,
            @PathVariable UUID fieldId,
            @Valid @RequestBody RecordFieldUpdateRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        RecordFieldResponse resp = recordFieldService.update(tenantId, recordTypeId, fieldId, request);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @DeleteMapping("/{fieldId}")
    @Operation(summary = "Delete (archive) field")
    public ResponseEntity<ApiResponse<String>> delete(
            @PathVariable UUID recordTypeId,
            @PathVariable UUID fieldId) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = tenantContext.getUserId();
        recordFieldService.delete(tenantId, recordTypeId, fieldId, userId);
        return ResponseEntity.ok(ApiResponse.success("Field deleted"));
    }
}
