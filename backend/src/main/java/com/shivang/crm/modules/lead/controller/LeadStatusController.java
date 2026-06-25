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
import com.shivang.crm.modules.lead.dto.LeadStatusCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadStatusResponse;
import com.shivang.crm.modules.lead.service.LeadStatusService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/lead-statuses")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lead Statuses", description = "Lead Status Master Data APIs")
public class LeadStatusController {

    private final LeadStatusService leadStatusService;
    private final TenantContext tenantContext;

    /**
     * Get all lead statuses for tenant
     */
    @GetMapping
    @Operation(summary = "List lead statuses", description = "Get all lead statuses for current tenant")
    public ResponseEntity<ApiResponse<java.util.List<LeadStatusResponse>>> listStatuses() {
        log.info("GET /api/v1/lead-statuses - Listing statuses");

        UUID tenantId = currentTenantId();

        List<LeadStatusResponse> statuses = leadStatusService.getStatusesByTenant(tenantId);

        return ResponseEntity.ok(ApiResponse.success(statuses));
    }

    /**
     * Create a new lead status
     */
    @PostMapping
    @Operation(summary = "Create lead status", description = "Create a new lead status")
    public ResponseEntity<ApiResponse<LeadStatusResponse>> createStatus(
            @Valid @RequestBody LeadStatusCreateRequest request) {

        log.info("POST /api/v1/lead-statuses - Creating status");

        UUID tenantId = currentTenantId();

        LeadStatusResponse statusResponse = leadStatusService.createStatus(tenantId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(statusResponse));
    }

    /**
     * Update a lead status
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update lead status", description = "Update an existing lead status")
    public ResponseEntity<ApiResponse<LeadStatusResponse>> updateStatus(
            @Parameter(description = "Status UUID")
            @PathVariable UUID id,

            @Valid @RequestBody LeadStatusCreateRequest request) {

        log.info("PUT /api/v1/lead-statuses/{} - Updating status", id);

        UUID tenantId = currentTenantId();

        LeadStatusResponse statusResponse = leadStatusService.updateStatus(id, tenantId, request);

        return ResponseEntity.ok(ApiResponse.success(statusResponse));
    }

    /**
     * Delete a lead status
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete lead status", description = "Delete a lead status")
    public ResponseEntity<ApiResponse<String>> deleteStatus(
            @Parameter(description = "Status UUID")
            @PathVariable UUID id) {

        log.info("DELETE /api/v1/lead-statuses/{} - Deleting status", id);

        UUID tenantId = currentTenantId();

        leadStatusService.deleteStatus(id, tenantId);

        return ResponseEntity.ok(ApiResponse.success("Status deleted successfully"));
    }

    private UUID currentTenantId() {
        String tenantId = tenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context is not available");
        }
        return UUID.fromString(tenantId);
    }
}
