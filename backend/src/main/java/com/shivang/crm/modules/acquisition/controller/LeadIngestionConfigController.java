package com.shivang.crm.modules.acquisition.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.acquisition.dto.LeadIngestionConfigCreateRequest;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionConfigResponse;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionConfigUpdateRequest;
import com.shivang.crm.modules.acquisition.service.LeadIngestionConfigService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/acquisition/configs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lead Ingestion Configs", description = "Tenant Lead Ingestion Configuration APIs")
public class LeadIngestionConfigController {

    private final LeadIngestionConfigService leadIngestionConfigService;
    private final TenantContext tenantContext;

    @PostMapping
    @Operation(summary = "Create ingestion config", description = "Create a lead ingestion configuration for current tenant")
    public ResponseEntity<ApiResponse<LeadIngestionConfigResponse>> create(
            @Valid @RequestBody LeadIngestionConfigCreateRequest request) {

        UUID tenantId = requireTenantId();
        LeadIngestionConfigResponse response = leadIngestionConfigService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "List ingestion configs", description = "List non-deleted lead ingestion configurations for current tenant")
    public ResponseEntity<ApiResponse<List<LeadIngestionConfigResponse>>> list() {

        UUID tenantId = requireTenantId();
        List<LeadIngestionConfigResponse> response = leadIngestionConfigService.list(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ingestion config", description = "Get a lead ingestion configuration by id for current tenant")
    public ResponseEntity<ApiResponse<LeadIngestionConfigResponse>> getById(
            @Parameter(description = "Configuration UUID") @PathVariable UUID id) {

        UUID tenantId = requireTenantId();
        LeadIngestionConfigResponse response = leadIngestionConfigService.getById(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ingestion config", description = "Update a lead ingestion configuration for current tenant")
    public ResponseEntity<ApiResponse<LeadIngestionConfigResponse>> update(
            @Parameter(description = "Configuration UUID") @PathVariable UUID id,
            @Valid @RequestBody LeadIngestionConfigUpdateRequest request) {

        UUID tenantId = requireTenantId();
        LeadIngestionConfigResponse response = leadIngestionConfigService.update(tenantId, id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete ingestion config", description = "Soft delete a lead ingestion configuration for current tenant")
    public ResponseEntity<ApiResponse<String>> delete(
            @Parameter(description = "Configuration UUID") @PathVariable UUID id) {

        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();
        leadIngestionConfigService.softDelete(tenantId, id, userId);
        return ResponseEntity.ok(ApiResponse.success("Lead ingestion configuration deleted successfully"));
    }

    private UUID requireTenantId() {
        if (!tenantContext.hasTenant()) {
            throw new IllegalStateException("Tenant context is not available");
        }
        return tenantContext.getTenantId();
    }

    private UUID requireUserId() {
        if (!tenantContext.hasUser()) {
            throw new IllegalStateException("User context is not available");
        }
        return tenantContext.getUserId();
    }
}