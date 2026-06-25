package com.shivang.crm.modules.account.controller;

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

import com.shivang.crm.modules.account.dto.AccountCustomFieldCreateRequest;
import com.shivang.crm.modules.account.dto.AccountCustomFieldResponse;
import com.shivang.crm.modules.account.service.AccountCustomFieldService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/account-custom-fields")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Account Custom Fields", description = "Account Custom Field Definition APIs")
public class AccountCustomFieldController {

    private final AccountCustomFieldService accountCustomFieldService;
    private final TenantContext tenantContext;

    @GetMapping
    @Operation(summary = "List account custom fields", description = "Get active custom fields for current tenant")
    public ResponseEntity<ApiResponse<List<AccountCustomFieldResponse>>> listFields() {
        log.info("GET /api/v1/account-custom-fields - Listing account custom fields");

        UUID tenantId = currentTenantId();
        List<AccountCustomFieldResponse> fields = accountCustomFieldService.getActiveFields(tenantId);
        return ResponseEntity.ok(ApiResponse.success(fields));
    }

    @PostMapping
    @Operation(summary = "Create account custom field", description = "Create a new account custom field")
    public ResponseEntity<ApiResponse<AccountCustomFieldResponse>> createField(
            @Valid @RequestBody AccountCustomFieldCreateRequest request) {

        log.info("POST /api/v1/account-custom-fields - Creating account custom field");

        UUID tenantId = currentTenantId();
        AccountCustomFieldResponse fieldResponse = accountCustomFieldService.createField(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(fieldResponse));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update account custom field", description = "Update an existing account custom field")
    public ResponseEntity<ApiResponse<AccountCustomFieldResponse>> updateField(
            @Parameter(description = "Custom field UUID") @PathVariable UUID id,
            @Valid @RequestBody AccountCustomFieldCreateRequest request) {

        log.info("PUT /api/v1/account-custom-fields/{} - Updating account custom field", id);

        UUID tenantId = currentTenantId();
        AccountCustomFieldResponse fieldResponse = accountCustomFieldService.updateField(id, tenantId, request);
        return ResponseEntity.ok(ApiResponse.success(fieldResponse));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete account custom field", description = "Delete a custom field definition")
    public ResponseEntity<ApiResponse<String>> deleteField(@Parameter(description = "Custom field UUID") @PathVariable UUID id) {
        log.info("DELETE /api/v1/account-custom-fields/{} - Deleting account custom field", id);

        UUID tenantId = currentTenantId();
        accountCustomFieldService.deleteField(id, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Account custom field deleted successfully"));
    }

    private UUID currentTenantId() {
        String tenantId = tenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context is not available");
        }
        return UUID.fromString(tenantId);
    }
}
