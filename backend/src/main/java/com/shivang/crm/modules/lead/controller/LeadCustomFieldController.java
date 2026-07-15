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
import com.shivang.crm.modules.lead.dto.LeadCustomFieldCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadCustomFieldResponse;
import com.shivang.crm.modules.lead.service.LeadCustomFieldService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/lead-custom-fields")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lead Custom Fields", description = "Lead Custom Field Definition APIs")
public class LeadCustomFieldController {

    private final LeadCustomFieldService leadCustomFieldService;
    private final TenantContext tenantContext;

    /**
     * Get all active custom fields for tenant
     */
    @GetMapping
    @Operation(summary = "List custom fields", description = "Get all active custom fields for current tenant")
    public ResponseEntity<ApiResponse<java.util.List<LeadCustomFieldResponse>>> listFields() {
        log.info("GET /api/v1/lead-custom-fields - Listing custom fields");

        UUID tenantId = currentTenantId();

        List<LeadCustomFieldResponse> fields = leadCustomFieldService.getActiveFields(tenantId);

        return ResponseEntity.ok(ApiResponse.success(fields));
    }

    /**
     * Create a new custom field
     */
    @PostMapping
    @Operation(summary = "Create custom field", description = "Create a new lead custom field")
    public ResponseEntity<ApiResponse<LeadCustomFieldResponse>> createField(
            @Valid @RequestBody LeadCustomFieldCreateRequest request) {

        log.info("POST /api/v1/lead-custom-fields - Creating custom field");

        UUID tenantId = currentTenantId();

        LeadCustomFieldResponse fieldResponse = leadCustomFieldService.createField(tenantId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(fieldResponse));
    }

    /**
     * Update a custom field
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update custom field", description = "Update an existing custom field")
    public ResponseEntity<ApiResponse<LeadCustomFieldResponse>> updateField(
            @Parameter(description = "Field UUID")
            @PathVariable UUID id,

            @Valid @RequestBody LeadCustomFieldCreateRequest request) {

        log.info("PUT /api/v1/lead-custom-fields/{} - Updating custom field", id);

        UUID tenantId = currentTenantId();

        LeadCustomFieldResponse fieldResponse = leadCustomFieldService.updateField(id, tenantId, request);

        return ResponseEntity.ok(ApiResponse.success(fieldResponse));
    }

    /**
     * Delete a custom field
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete custom field", description = "Delete a custom field")
    public ResponseEntity<ApiResponse<String>> deleteField(
            @Parameter(description = "Field UUID")
            @PathVariable UUID id) {

        log.info("DELETE /api/v1/lead-custom-fields/{} - Deleting custom field", id);

        UUID tenantId = currentTenantId();

        leadCustomFieldService.deleteField(id, tenantId);

        return ResponseEntity.ok(ApiResponse.success("Custom field deleted successfully"));
    }

    private UUID currentTenantId() {
         return tenantContext.getTenantId();
    }
}
