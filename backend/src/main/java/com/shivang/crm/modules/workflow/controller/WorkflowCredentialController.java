package com.shivang.crm.modules.workflow.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.workflow.service.WorkflowCredentialService;
import com.shivang.crm.shared.dto.ApiResponse;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/workflows/http-credentials")
@RequiredArgsConstructor
public class WorkflowCredentialController {

    private final WorkflowCredentialService credentialService;
    private final TenantContext tenantContext;

    public record CredentialStatusResponse(boolean configured, String scope, UUID ownerUserId, List<String> keys) {}

    @GetMapping("/tenant/status")
    @PreAuthorize("@rbac.has(authentication, 'workflow', 'read')")
    public ResponseEntity<ApiResponse<CredentialStatusResponse>> tenantStatus() {
        UUID tenantId = tenantContext.requireTenantId();
        boolean configured = credentialService.hasGenericCredential(tenantId, null, "TENANT");
        List<String> keys = configured ? credentialService.getCredentialKeys(tenantId, null, "TENANT") : List.of();
        return ResponseEntity.ok(ApiResponse.success(new CredentialStatusResponse(configured, "TENANT", null, keys)));
    }

    @PutMapping("/tenant")
    @PreAuthorize("@rbac.has(authentication, 'workflow', 'write')")
    public ResponseEntity<ApiResponse<CredentialStatusResponse>> putTenant(@RequestBody Map<String, Object> body) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID actorId = tenantContext.getUserId();
        Map<String, Object> values = extractValues(body);
        if (values.isEmpty()) throw new BusinessException("VALIDATION_ERROR", "Credential values are required");
        credentialService.storeGenericCredential(tenantId, actorId, null, "TENANT", values, "CREDENTIAL");
        List<String> keys = credentialService.getCredentialKeys(tenantId, null, "TENANT");
        return ResponseEntity.ok(ApiResponse.success(new CredentialStatusResponse(true, "TENANT", null, keys)));
    }

    @GetMapping("/user/{userId}/status")
    @PreAuthorize("@rbac.has(authentication, 'workflow', 'read')")
    public ResponseEntity<ApiResponse<CredentialStatusResponse>> userStatus(@PathVariable UUID userId, @RequestParam(required = false) String scope) {
        UUID tenantId = tenantContext.requireTenantId();
        String effectiveScope = scope == null ? "USER" : scope;
        boolean configured = credentialService.hasGenericCredential(tenantId, userId, effectiveScope);
        List<String> keys = configured ? credentialService.getCredentialKeys(tenantId, userId, effectiveScope) : List.of();
        return ResponseEntity.ok(ApiResponse.success(new CredentialStatusResponse(configured, effectiveScope, userId, keys)));
    }

    @PutMapping("/user/{userId}")
    @PreAuthorize("@rbac.has(authentication, 'workflow', 'write')")
    public ResponseEntity<ApiResponse<CredentialStatusResponse>> putUser(@PathVariable UUID userId, @RequestBody Map<String, Object> body) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID actorId = tenantContext.getUserId();
        Map<String, Object> values = extractValues(body);
        if (values.isEmpty()) throw new BusinessException("VALIDATION_ERROR", "Credential values are required");
        credentialService.storeGenericCredential(tenantId, actorId, userId, "USER", values, "CREDENTIAL");
        List<String> keys = credentialService.getCredentialKeys(tenantId, userId, "USER");
        return ResponseEntity.ok(ApiResponse.success(new CredentialStatusResponse(true, "USER", userId, keys)));
    }

    @DeleteMapping("/tenant")
    @PreAuthorize("@rbac.has(authentication, 'workflow', 'write')")
    public ResponseEntity<ApiResponse<String>> deleteTenant() {
        UUID tenantId = tenantContext.requireTenantId();
        UUID actorId = tenantContext.getUserId();
        if (!credentialService.hasGenericCredential(tenantId, null, "TENANT")) {
            throw new BusinessException("NOT_FOUND", "Tenant credential not found");
        }
        credentialService.deleteGenericCredential(tenantId, actorId, null, "TENANT");
        return ResponseEntity.ok(ApiResponse.success("Tenant credential deleted"));
    }

    @DeleteMapping("/user/{userId}")
    @PreAuthorize("@rbac.has(authentication, 'workflow', 'write')")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable UUID userId) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID actorId = tenantContext.getUserId();
        if (!credentialService.hasGenericCredential(tenantId, userId, "USER")) {
            throw new BusinessException("NOT_FOUND", "User credential not found");
        }
        credentialService.deleteGenericCredential(tenantId, actorId, userId, "USER");
        return ResponseEntity.ok(ApiResponse.success("User credential deleted"));
    }

    @GetMapping("/keys")
    @PreAuthorize("@rbac.has(authentication, 'workflow', 'read')")
    public ResponseEntity<ApiResponse<List<String>>> keys(@RequestParam(required = false) String scope, @RequestParam(required = false) UUID userId) {
        UUID tenantId = tenantContext.requireTenantId();
        String effectiveScope = scope == null ? (userId == null ? "TENANT" : "USER") : scope.toUpperCase();
        UUID owner = "TENANT".equals(effectiveScope) ? null : (userId != null ? userId : tenantContext.getUserId());
        List<String> keys = credentialService.getCredentialKeys(tenantId, owner, effectiveScope);
        return ResponseEntity.ok(ApiResponse.success(keys));
    }

    private Map<String, Object> extractValues(Map<String, Object> body) {
        if (body == null) return Map.of();
        // Accept either {credential:{...}} or direct map
        if (body.containsKey("credential") && body.get("credential") instanceof Map<?,?> m) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            m.forEach((k,v) -> out.put(String.valueOf(k), v));
            return out;
        }
        if (body.containsKey("values") && body.get("values") instanceof Map<?,?> m) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            m.forEach((k,v) -> out.put(String.valueOf(k), v));
            return out;
        }
        // Direct map is the credential
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        body.forEach((k,v) -> {
            if (!k.equals("scope") && !k.equals("ownerUserId") && !k.equals("authType")) out.put(k, v);
        });
        return out;
    }
}
