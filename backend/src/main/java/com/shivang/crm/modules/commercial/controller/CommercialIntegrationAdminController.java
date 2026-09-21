package com.shivang.crm.modules.commercial.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.commercial.repository.CommercialApiKeyRepository;
import com.shivang.crm.modules.commercial.service.CommercialApiKeyService;
import com.shivang.crm.modules.commercial.service.CommercialIntegrationFeatureService;
import com.shivang.crm.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/commercial")
@RequiredArgsConstructor
public class CommercialIntegrationAdminController {

    private final CommercialApiKeyService apiKeyService;
    private final CommercialApiKeyRepository apiKeyRepository;
    private final CommercialIntegrationFeatureService featureService;
    private final TenantContext tenantContext;

    @GetMapping("/api-keys")
    @PreAuthorize("hasAnyRole('ADMIN','PLATFORM_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<StatusResponse>> status() {
        featureService.requireEnabled();
        UUID tenantId = tenantContext.requireTenantId();
        var existing = apiKeyRepository.findByTenantId(tenantId);
        boolean configured = existing.isPresent() && Boolean.TRUE.equals(existing.get().getIsActive());
        String prefix = configured ? existing.get().getKeyPrefix() : null;
        return ResponseEntity.ok(ApiResponse.success(new StatusResponse(configured, prefix)));
    }

    /**
     * Generate or rotate API key for current tenant.
     * Requires tenant authentication (admin). Returns raw key once – caller must store it.
     * Key is stored as hash; raw never persisted.
     */
    @PostMapping("/api-keys/rotate")
    @PreAuthorize("hasAnyRole('ADMIN','PLATFORM_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RotationResponse>> rotate() {
        featureService.requireEnabled();
        UUID tenantId = tenantContext.requireTenantId();
        var generated = apiKeyService.generateOrRotate(tenantId);
        log.info("Commercial API key rotated for tenant {}", tenantId);
        RotationResponse resp = new RotationResponse(generated.rawKey(), generated.prefix(),
                "Store this key securely. It will not be shown again. Use header X-API-Key.");
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    // Tenant-specific rotate for platform admins (query param style kept simple)
    @PostMapping("/tenants/{tenantId}/api-keys/rotate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<RotationResponse>> rotateForTenant(@PathVariable UUID tenantId) {
        featureService.requireEnabled();
        var generated = apiKeyService.generateOrRotate(tenantId);
        log.info("Commercial API key rotated for tenant {} by platform admin", tenantId);
        RotationResponse resp = new RotationResponse(generated.rawKey(), generated.prefix(),
                "Store this key securely.");
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    public record RotationResponse(String apiKey, String prefix, String message) {}
    public record StatusResponse(boolean configured, String keyPrefix) {}
}
