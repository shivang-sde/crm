package com.shivang.crm.modules.rbac.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SpEL entry point for method-level authorization.
 *
 * Exposed as bean "rbac" so annotations can express:
 *   @PreAuthorize("@rbac.has(authentication, 'lead', 'read')")
 *
 * This is intentionally a thin adapter: every evaluation is delegated to
 * {@link PermissionEvaluatorService}, keeping a single authoritative RBAC
 * interpreter (fail-closed, SUPERADMIN-aware, cache-backed) shared with
 * RbacFilter and service-layer checks. No independent permission semantics
 * live here.
 */
@Slf4j
@Component("rbac")
@RequiredArgsConstructor
public class RbacAuthorization {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final TenantContext tenantContext;

    /**
     * Checks module:action for the authenticated caller in the current tenant
     * context. Fails closed on any missing or malformed input.
     */
    public boolean has(Authentication authentication, String module, String action) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String principal)
                || module == null || module.isBlank()
                || action == null || action.isBlank()) {
            return false;
        }

        UUID userId;
        try {
            userId = UUID.fromString(principal);
        } catch (IllegalArgumentException e) {
            log.debug("Method authorization denied: invalid principal");
            return false;
        }

        return permissionEvaluatorService.hasPermission(
                userId, tenantContext.getTenantId(), module, action);
    }
}
