package com.shivang.crm.modules.records.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.records.dto.RecordWebhookCreateRequest;
import com.shivang.crm.modules.records.dto.RecordWebhookResponse;
import com.shivang.crm.modules.records.dto.RecordWebhookUpdateRequest;
import com.shivang.crm.modules.records.service.RecordWebhookService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/record-webhooks")
@RequiredArgsConstructor
@Tag(name = "Record Webhooks", description = "Tenant webhook control plane for Records")
public class RecordWebhookController {

    private final RecordWebhookService webhookService;
    private final TenantContext tenantContext;

    @PostMapping
    @Operation(summary = "Create webhook")
    public ResponseEntity<ApiResponse<RecordWebhookResponse>> create(@Valid @RequestBody RecordWebhookCreateRequest req) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = tenantContext.getUserId();
        RecordWebhookResponse resp = webhookService.create(tenantId, userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
    }

    @GetMapping
    @Operation(summary = "List webhooks")
    public ResponseEntity<ApiResponse<java.util.List<RecordWebhookResponse>>> list(
            @RequestParam(required = false) UUID recordTypeId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = tenantContext.requireTenantId();
        Page<RecordWebhookResponse> p = webhookService.list(tenantId, recordTypeId, isActive, page, size);
        Map<String, Object> meta = Map.of("page", p.getNumber(), "size", p.getSize(), "total", p.getTotalElements(), "totalPages", p.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(p.getContent(), meta));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get webhook")
    public ResponseEntity<ApiResponse<RecordWebhookResponse>> get(@PathVariable UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        RecordWebhookResponse resp = webhookService.get(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update webhook")
    public ResponseEntity<ApiResponse<RecordWebhookResponse>> update(@PathVariable UUID id, @Valid @RequestBody RecordWebhookUpdateRequest req) {
        UUID tenantId = tenantContext.requireTenantId();
        RecordWebhookResponse resp = webhookService.update(tenantId, id, req);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete webhook")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = tenantContext.getUserId();
        webhookService.delete(tenantId, id, userId);
        return ResponseEntity.ok(ApiResponse.success("Webhook deleted"));
    }

    @PostMapping("/{id}/rotate-secret")
    @Operation(summary = "Rotate webhook secret (one-time plain secret returned)")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> rotateSecret(@PathVariable UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        var result = webhookService.rotateSecret(tenantId, id);
        java.util.Map<String, Object> data = Map.of("secret", result.plainSecret(), "webhook", result.webhook());
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
