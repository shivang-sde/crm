package com.shivang.crm.modules.lead.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.lead.dto.LeadSourceCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadSourceResponse;
import com.shivang.crm.modules.lead.service.LeadSourceService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/lead-sources")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lead Sources", description = "Lead Source Master Data APIs")
public class LeadSourceController {

    private final LeadSourceService leadSourceService;
    private final TenantContext tenantContext;

    /**
     * Get all active lead sources for tenant
     */
    @GetMapping
    @Operation(summary = "List lead sources", description = "Get all active lead sources for current tenant")
    public ResponseEntity<ApiResponse<java.util.List<LeadSourceResponse>>> listSources() {
        log.info("GET /api/v1/lead-sources - Listing sources");

        UUID tenantId = currentTenantId();

        List<LeadSourceResponse> sources = leadSourceService.getActiveSources(tenantId);

        return ResponseEntity.ok(ApiResponse.success(sources));
    }

    /**
     * Create a new lead source
     */
    @PostMapping
    @Operation(summary = "Create lead source", description = "Create a new lead source")
    public ResponseEntity<ApiResponse<LeadSourceResponse>> createSource(
            @Valid @RequestBody LeadSourceCreateRequest request) {

        log.info("POST /api/v1/lead-sources - Creating source");

        UUID tenantId = currentTenantId();

        LeadSourceResponse sourceResponse = leadSourceService.createSource(tenantId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(sourceResponse));
    }

    /**
     * Update a lead source
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update lead source", description = "Update an existing lead source")
    public ResponseEntity<ApiResponse<LeadSourceResponse>> updateSource(
            @Parameter(description = "Source UUID")
            @PathVariable UUID id,

            @Valid @RequestBody LeadSourceCreateRequest request) {

        log.info("PUT /api/v1/lead-sources/{} - Updating source", id);

        UUID tenantId = currentTenantId();

        LeadSourceResponse sourceResponse = leadSourceService.updateSource(id, tenantId, request);

        return ResponseEntity.ok(ApiResponse.success(sourceResponse));
    }

    /**
     * Delete a lead source
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete lead source", description = "Delete a lead source")
    public ResponseEntity<ApiResponse<String>> deleteSource(
            @Parameter(description = "Source UUID")
            @PathVariable UUID id) {

        log.info("DELETE /api/v1/lead-sources/{} - Deleting source", id);

        UUID tenantId = currentTenantId();

        leadSourceService.deleteSource(id, tenantId);

        return ResponseEntity.ok(ApiResponse.success("Source deleted successfully"));
    }

    private UUID currentTenantId() {
         return tenantContext.getTenantId();
    }
}
