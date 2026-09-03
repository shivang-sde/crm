package com.shivang.crm.modules.form.controller;

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

import com.shivang.crm.modules.form.dto.FormCreateRequest;
import com.shivang.crm.modules.form.dto.FormResponse;
import com.shivang.crm.modules.form.dto.FormUpdateRequest;
import com.shivang.crm.modules.form.service.FormService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/forms")
@RequiredArgsConstructor
@Tag(name = "Forms", description = "CRM Form Builder")
public class FormController {

    private final FormService formService;
    private final TenantContext tenantContext;

    @GetMapping
    @Operation(summary = "List forms for tenant")
    public ResponseEntity<ApiResponse<List<FormResponse>>> list() {
        UUID tenantId = requireTenantId();
        List<FormResponse> forms = formService.listForms(tenantId);
        return ResponseEntity.ok(ApiResponse.success(forms));
    }

    @PostMapping
    @Operation(summary = "Create form (draft)")
    public ResponseEntity<ApiResponse<FormResponse>> create(@Valid @RequestBody FormCreateRequest request) {
        UUID tenantId = requireTenantId();
        FormResponse form = formService.createForm(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(form));
    }

    @GetMapping("/{formId}")
    @Operation(summary = "Get form with fields")
    public ResponseEntity<ApiResponse<FormResponse>> get(@PathVariable UUID formId) {
        UUID tenantId = requireTenantId();
        FormResponse form = formService.getForm(tenantId, formId);
        return ResponseEntity.ok(ApiResponse.success(form));
    }

    @PutMapping("/{formId}")
    @Operation(summary = "Update form (atomic save of metadata + fields)")
    public ResponseEntity<ApiResponse<FormResponse>> update(@PathVariable UUID formId, @Valid @RequestBody FormUpdateRequest request) {
        UUID tenantId = requireTenantId();
        FormResponse form = formService.updateForm(tenantId, formId, request);
        return ResponseEntity.ok(ApiResponse.success(form));
    }

    @DeleteMapping("/{formId}")
    @Operation(summary = "Delete form")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable UUID formId) {
        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();
        formService.deleteForm(tenantId, formId, userId);
        return ResponseEntity.ok(ApiResponse.success("Form deleted"));
    }

    @PostMapping("/{formId}/publish")
    @Operation(summary = "Publish form")
    public ResponseEntity<ApiResponse<FormResponse>> publish(@PathVariable UUID formId) {
        UUID tenantId = requireTenantId();
        FormResponse form = formService.publishForm(tenantId, formId);
        return ResponseEntity.ok(ApiResponse.success(form));
    }

    @PostMapping("/{formId}/unpublish")
    @Operation(summary = "Unpublish form")
    public ResponseEntity<ApiResponse<FormResponse>> unpublish(@PathVariable UUID formId) {
        UUID tenantId = requireTenantId();
        FormResponse form = formService.unpublishForm(tenantId, formId);
        return ResponseEntity.ok(ApiResponse.success(form));
    }

    @PostMapping("/{formId}/duplicate")
    @Operation(summary = "Duplicate form")
    public ResponseEntity<ApiResponse<FormResponse>> duplicate(@PathVariable UUID formId) {
        UUID tenantId = requireTenantId();
        FormResponse form = formService.duplicateForm(tenantId, formId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(form));
    }

    private UUID requireTenantId() {
        return tenantContext.requireTenantId();
    }

    private UUID requireUserId() {
        if (!tenantContext.hasUser()) throw new IllegalStateException("User context not available");
        return tenantContext.getUserId();
    }
}
