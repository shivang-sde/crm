// TenantController.java
package com.shivang.crm.modules.tenant.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.tenant.dto.TenantResponseDTO;
import com.shivang.crm.modules.tenant.dto.TenantUpdateRequest;
import com.shivang.crm.modules.tenant.service.TenantService;
import com.shivang.crm.shared.dto.ApiResponse;
import com.shivang.crm.util.UserUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("hasPermission('tenant', 'read')")
    public ResponseEntity<ApiResponse<List<TenantResponseDTO>>> getAllTenants(
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String role = UserUtil.getUserRole(authentication);
        // You'll need to extract this
        log.info("Authorities={}", authentication.getAuthorities());

        List<TenantResponseDTO> tenants = tenantService.getAllTenants(UUID.fromString(userId), role);
        return ResponseEntity.ok(ApiResponse.success(tenants));
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasPermission('tenant', 'read')")
    public ResponseEntity<ApiResponse<TenantResponseDTO>> getTenant(
            @PathVariable UUID tenantId,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String role = UserUtil.getUserRole(authentication);

        TenantResponseDTO tenant = tenantService.getTenant(tenantId, UUID.fromString(userId), role);
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("hasPermission('tenant', 'write')")
    public ResponseEntity<ApiResponse<TenantResponseDTO>> updateTenant(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantUpdateRequest request,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        String role = UserUtil.getUserRole(authentication);

        TenantResponseDTO response = tenantService.updateTenant(
                tenantId,
                request,
                UUID.fromString(userId),
                role);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

}