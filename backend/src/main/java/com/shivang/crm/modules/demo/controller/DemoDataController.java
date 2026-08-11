package com.shivang.crm.modules.demo.controller;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.demo.dto.DemoDataStatusResponse;
import com.shivang.crm.modules.demo.dto.DemoInstallationResponse;
import com.shivang.crm.modules.demo.service.DemoDataService;
import com.shivang.crm.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demo-data")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Demo Data", description = "Demo Data Installation APIs")
public class DemoDataController {

    private final DemoDataService demoDataService;
    private final TenantContext tenantContext;

    @GetMapping("/status")
    @Operation(summary = "Get demo data status", description = "Returns the current installation status of the demo data for the tenant")
    @PreAuthorize("hasPermission('tenant', 'write')")
    public ResponseEntity<ApiResponse<DemoDataStatusResponse>> getDemoStatus() {
        log.info("GET /api/v1/demo-data/status - Checking demo data status");
        UUID tenantId = currentTenantId();
        DemoDataStatusResponse response = demoDataService.getDemoStatus(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/install")
    @Operation(summary = "Install generic sales demo data", description = "Populates realistic demo data for the tenant")
    @PreAuthorize("hasPermission('tenant', 'write')")
    public ResponseEntity<ApiResponse<DemoInstallationResponse>> installGenericSalesDemo() {
        log.info("POST /api/v1/demo-data/install - Triggering demo data installation");

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        DemoInstallationResponse response = demoDataService.installGenericSalesDemo(tenantId, userId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    private UUID currentTenantId() {
        return tenantContext.getTenantId();
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("User authentication is not available");
        }
        return UUID.fromString(authentication.getPrincipal().toString());
    }
}
