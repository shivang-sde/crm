package com.shivang.crm.modules.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.dto.request.TenantProvisionRequest;
import com.shivang.crm.modules.auth.dto.response.TenantProvisionResponse;
import com.shivang.crm.modules.auth.mapper.TenantMapper;
import com.shivang.crm.modules.auth.service.TenantProvisioningService;
import com.shivang.crm.modules.auth.service.impl.TenantProvisionResult;
import com.shivang.crm.modules.user.mapper.UserManagementMapper;
import com.shivang.crm.shared.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantProvisioningController {

    private final TenantProvisioningService tenantProvisioningService;
    private final TenantMapper tenantMapper;
    private final UserManagementMapper userManagementMapper;

    @PostMapping("/provision")
    @PreAuthorize("@rbac.has(authentication, 'tenant', 'write')")
    public ResponseEntity<ApiResponse<TenantProvisionResponse>> provisionTenant(
            @Valid @RequestBody TenantProvisionRequest request,
            Authentication authentication) {

        String authenticatedUserId = null;
        if (authentication != null && authentication.getPrincipal() != null) {
            authenticatedUserId = (String) authentication.getPrincipal();
        }

        TenantProvisionResult result = tenantProvisioningService.provisionTenant(request, authenticatedUserId);

        TenantProvisionResponse response = TenantProvisionResponse.builder()
                .tenant(tenantMapper.toTenantInfo(result.getTenant()))
                .admin(userManagementMapper.toUserResponse(result.getAdminUser(), result.getAdminUserRole()))
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
