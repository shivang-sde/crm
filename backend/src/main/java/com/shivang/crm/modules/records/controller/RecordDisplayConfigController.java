package com.shivang.crm.modules.records.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.records.dto.display.DisplayConfigRequest;
import com.shivang.crm.modules.records.dto.display.DisplayConfigResponse;
import com.shivang.crm.modules.records.service.RecordDisplayConfigService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/record-types/{recordTypeId}/display-config")
@RequiredArgsConstructor
@Tag(name = "Record Display Config", description = "Tenant display layout for record types")
public class RecordDisplayConfigController {

    private final RecordDisplayConfigService displayService;
    private final TenantContext tenantContext;

    @GetMapping
    @Operation(summary = "Get display config (derived default if not customized)")
    public ResponseEntity<ApiResponse<DisplayConfigResponse>> get(@PathVariable UUID recordTypeId) {
        UUID tenantId = tenantContext.requireTenantId();
        DisplayConfigResponse resp = displayService.get(tenantId, recordTypeId);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @PutMapping
    @Operation(summary = "Create or update display config")
    public ResponseEntity<ApiResponse<DisplayConfigResponse>> put(
            @PathVariable UUID recordTypeId,
            @Valid @RequestBody DisplayConfigRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        DisplayConfigResponse resp = displayService.put(tenantId, recordTypeId, request);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @DeleteMapping
    @Operation(summary = "Reset display config to default")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable UUID recordTypeId) {
        UUID tenantId = tenantContext.requireTenantId();
        displayService.delete(tenantId, recordTypeId);
        return ResponseEntity.ok(ApiResponse.success("Display config reset to default"));
    }
}
