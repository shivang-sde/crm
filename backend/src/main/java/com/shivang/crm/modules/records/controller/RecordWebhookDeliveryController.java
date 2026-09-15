package com.shivang.crm.modules.records.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.records.dto.RecordWebhookDeliveryDetailResponse;
import com.shivang.crm.modules.records.dto.RecordWebhookDeliveryResponse;
import com.shivang.crm.modules.records.service.RecordWebhookDeliveryQueryService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/record-webhooks")
@RequiredArgsConstructor
@Tag(name = "Record Webhook Deliveries", description = "Operational debugging for webhook deliveries")
public class RecordWebhookDeliveryController {

    private final RecordWebhookDeliveryQueryService queryService;
    private final TenantContext tenantContext;

    @GetMapping("/{webhookId}/deliveries")
    @Operation(summary = "List deliveries for webhook")
    public ResponseEntity<ApiResponse<java.util.List<RecordWebhookDeliveryResponse>>> list(
            @PathVariable UUID webhookId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = tenantContext.requireTenantId();
        // Clamp size 1..100
        int clampedSize = Math.max(1, Math.min(size, 100));
        int clampedPage = Math.max(0, page);
        Page<RecordWebhookDeliveryResponse> p = queryService.list(tenantId, webhookId, status, clampedPage, clampedSize);
        Map<String, Object> meta = Map.of("page", p.getNumber(), "size", p.getSize(), "total", p.getTotalElements(), "totalPages", p.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(p.getContent(), meta));
    }

    @GetMapping("/{webhookId}/deliveries/{deliveryId}")
    @Operation(summary = "Get delivery detail with operational chain")
    public ResponseEntity<ApiResponse<RecordWebhookDeliveryDetailResponse>> detail(
            @PathVariable UUID webhookId,
            @PathVariable UUID deliveryId) {
        UUID tenantId = tenantContext.requireTenantId();
        RecordWebhookDeliveryDetailResponse detail = queryService.detail(tenantId, webhookId, deliveryId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }
}
