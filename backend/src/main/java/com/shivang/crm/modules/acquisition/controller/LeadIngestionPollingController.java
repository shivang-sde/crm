package com.shivang.crm.modules.acquisition.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.acquisition.config.LeadIngestionConfig;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.acquisition.service.LeadIngestionPollingService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.shared.dto.ApiResponse;
import com.shivang.crm.shared.exception.BusinessException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/acquisition/configs/{configId}/polling")
@RequiredArgsConstructor
@Tag(name = "Polling", description = "Polling source test and trigger")
public class LeadIngestionPollingController {

    private final LeadIngestionPollingService pollingService;
    private final LeadIngestionConfigRepository configRepository;
    private final TenantContext tenantContext;

    @PostMapping("/test")
    @Operation(summary = "Test polling connection", description = "Tests the configured endpoint/connection and returns record availability")
    public ResponseEntity<ApiResponse<Map<String, Object>>> test(
            @PathVariable UUID configId) {
        UUID tenantId = requireTenantId();
        Map<String, Object> result = pollingService.testConnection(tenantId, configId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/trigger")
    @Operation(summary = "Trigger polling manually", description = "Manually triggers a poll for the source, respecting locking and incremental sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> trigger(
            @PathVariable UUID configId) {
        UUID tenantId = requireTenantId();
        UUID actorId = tenantContext.getUserId() != null ? tenantContext.getUserId() : tenantId;
        Map<String, Object> result = pollingService.pollNow(tenantId, configId, actorId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/status")
    @Operation(summary = "Get polling status", description = "Returns last poll stats, next poll, and error if any")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(
            @PathVariable UUID configId) {
        UUID tenantId = requireTenantId();
        LeadIngestionConfig config = configRepository.findByIdAndTenantIdAndDeletedFalse(configId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Polling source not found"));
        Map<String, Object> settings = config.getSettings() != null ? config.getSettings() : Map.of();
        Object pollingState = settings.get("pollingState");
        Object pollingCfg = settings.get("polling");
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("polling", pollingCfg);
        out.put("pollingState", pollingState);
        out.put("active", config.getActive());
        out.put("transportType", config.getTransportType().name());
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    private UUID requireTenantId() {
        return tenantContext.requireTenantId();
    }
}
