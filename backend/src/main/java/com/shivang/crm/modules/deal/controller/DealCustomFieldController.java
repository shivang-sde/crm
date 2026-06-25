package com.shivang.crm.modules.deal.controller;

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

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.deal.dto.DealCustomFieldCreateRequest;
import com.shivang.crm.modules.deal.dto.DealCustomFieldResponse;
import com.shivang.crm.modules.deal.service.DealCustomFieldService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/deal-custom-fields")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Deal Custom Fields", description = "Deal Custom Field Definition APIs")
public class DealCustomFieldController {

    private final DealCustomFieldService dealCustomFieldService;
    private final TenantContext tenantContext;

    @GetMapping
    @Operation(summary = "List deal custom fields", description = "Get active custom fields for current tenant")
    public ResponseEntity<ApiResponse<List<DealCustomFieldResponse>>> listFields() {
        log.info("GET /api/v1/deal-custom-fields - Listing deal custom fields");

        UUID tenantId = currentTenantId();
        List<DealCustomFieldResponse> fields = dealCustomFieldService.getActiveFields(tenantId);
        return ResponseEntity.ok(ApiResponse.success(fields));
    }

    @PostMapping
    @Operation(summary = "Create deal custom field", description = "Create a new deal custom field")
    public ResponseEntity<ApiResponse<DealCustomFieldResponse>> createField(
            @Valid @RequestBody DealCustomFieldCreateRequest request) {

        log.info("POST /api/v1/deal-custom-fields - Creating deal custom field");

        UUID tenantId = currentTenantId();
        DealCustomFieldResponse fieldResponse = dealCustomFieldService.createField(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(fieldResponse));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update deal custom field", description = "Update an existing deal custom field")
    public ResponseEntity<ApiResponse<DealCustomFieldResponse>> updateField(
            @Parameter(description = "Custom field UUID") @PathVariable UUID id,
            @Valid @RequestBody DealCustomFieldCreateRequest request) {

        log.info("PUT /api/v1/deal-custom-fields/{} - Updating deal custom field", id);

        UUID tenantId = currentTenantId();
        DealCustomFieldResponse fieldResponse = dealCustomFieldService.updateField(id, tenantId, request);
        return ResponseEntity.ok(ApiResponse.success(fieldResponse));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete deal custom field", description = "Delete a custom field definition")
    public ResponseEntity<ApiResponse<String>> deleteField(
            @Parameter(description = "Custom field UUID") @PathVariable UUID id) {

        log.info("DELETE /api/v1/deal-custom-fields/{} - Deleting deal custom field", id);

        UUID tenantId = currentTenantId();
        dealCustomFieldService.deleteField(id, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Deal custom field deleted successfully"));
    }

    private UUID currentTenantId() {
        String tenantId = tenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context is not available");
        }
        return UUID.fromString(tenantId);
    }
}
