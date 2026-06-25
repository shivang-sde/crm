package com.shivang.crm.modules.contact.controller;

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
import com.shivang.crm.modules.contact.dto.ContactCustomFieldCreateRequest;
import com.shivang.crm.modules.contact.dto.ContactCustomFieldResponse;
import com.shivang.crm.modules.contact.service.ContactCustomFieldService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/contact-custom-fields")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Contact Custom Fields", description = "Contact Custom Field Definition APIs")
public class ContactCustomFieldController {

    private final ContactCustomFieldService contactCustomFieldService;
    private final TenantContext tenantContext;

    @GetMapping
    @Operation(summary = "List contact custom fields", description = "Get active custom fields for current tenant")
    public ResponseEntity<ApiResponse<List<ContactCustomFieldResponse>>> listFields() {
        log.info("GET /api/v1/contact-custom-fields - Listing contact custom fields");

        UUID tenantId = currentTenantId();
        List<ContactCustomFieldResponse> fields = contactCustomFieldService.getActiveFields(tenantId);
        return ResponseEntity.ok(ApiResponse.success(fields));
    }

    @PostMapping
    @Operation(summary = "Create contact custom field", description = "Create a new contact custom field")
    public ResponseEntity<ApiResponse<ContactCustomFieldResponse>> createField(
            @Valid @RequestBody ContactCustomFieldCreateRequest request) {

        log.info("POST /api/v1/contact-custom-fields - Creating contact custom field");

        UUID tenantId = currentTenantId();
        ContactCustomFieldResponse fieldResponse = contactCustomFieldService.createField(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(fieldResponse));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update contact custom field", description = "Update an existing contact custom field")
    public ResponseEntity<ApiResponse<ContactCustomFieldResponse>> updateField(
            @Parameter(description = "Custom field UUID") @PathVariable UUID id,
            @Valid @RequestBody ContactCustomFieldCreateRequest request) {

        log.info("PUT /api/v1/contact-custom-fields/{} - Updating contact custom field", id);

        UUID tenantId = currentTenantId();
        ContactCustomFieldResponse fieldResponse = contactCustomFieldService.updateField(id, tenantId, request);
        return ResponseEntity.ok(ApiResponse.success(fieldResponse));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete contact custom field", description = "Delete a custom field definition")
    public ResponseEntity<ApiResponse<String>> deleteField(@Parameter(description = "Custom field UUID") @PathVariable UUID id) {
        log.info("DELETE /api/v1/contact-custom-fields/{} - Deleting contact custom field", id);

        UUID tenantId = currentTenantId();
        contactCustomFieldService.deleteField(id, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Contact custom field deleted successfully"));
    }

    private UUID currentTenantId() {
        String tenantId = tenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context is not available");
        }
        return UUID.fromString(tenantId);
    }
}
