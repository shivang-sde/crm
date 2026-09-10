package com.shivang.crm.modules.rbac.security;

import java.io.Serializable;
import java.util.UUID;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bridges Spring Security's {@link PermissionEvaluator} (used by
 * {@code @PreAuthorize("hasPermission('module','action')")}) to the
 * application's single authoritative {@link PermissionEvaluatorService}.
 *
 * Previously no PermissionEvaluator bean existed, so Spring fell back to
 * {@code DenyAllPermissionEvaluator}, causing all hasPermission checks to
 * deny (logs: DenyAllPermissionEvaluator - Denying ...). This adapter
 * delegates to PermissionEvaluatorService.hasPermission(userId, tenantId, module, action)
 * using the existing TenantContext for tenant isolation and preserving
 * SUPERADMIN bypass, fail-closed semantics, and caching.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacPermissionEvaluator implements PermissionEvaluator {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final TenantContext tenantContext;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String principal)
                || targetDomainObject == null || permission == null) {
            return false;
        }
        String module = String.valueOf(targetDomainObject).trim();
        String action = String.valueOf(permission).trim();
        if (module.isBlank() || action.isBlank()) {
            return false;
        }
        // Handle combined "module:action" form if ever used
        if (module.contains(":") && action.isBlank()) {
            String[] parts = module.split(":", 2);
            module = parts[0].trim();
            action = parts[1].trim();
        }
        UUID userId;
        try {
            userId = UUID.fromString(principal);
        } catch (IllegalArgumentException e) {
            log.debug("PermissionEvaluator denied: invalid principal");
            return false;
        }
        UUID tenantId = tenantContext.getTenantId();
        boolean result = permissionEvaluatorService.hasPermission(userId, tenantId, module, action);
        log.debug("PermissionEvaluator hasPermission userId={} tenantId={} module={} action={} => {}", userId, tenantId, module, action, result);
        return result;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        // For hasPermission('admin','settings') Spring calls the 2-arg overload,
        // but handle 4-arg form as well: treat targetId as module, targetType as action if permission is null,
        // or targetType as module and permission as action.
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String principal)) {
            return false;
        }
        String module = null;
        String action = null;
        if (targetType != null && permission != null) {
            // hasPermission('admin','settings') could be mapped as targetId='admin', targetType='settings' in some SpEL parsings
            // Prefer explicit targetType + permission when both present
            module = String.valueOf(targetId != null ? targetId : targetType).trim();
            action = String.valueOf(permission).trim();
            // If targetType looks like a permission action and permission is null, use targetType as action
            if (targetId instanceof String && targetType != null && permission == null) {
                module = String.valueOf(targetId).trim();
                action = targetType.trim();
            } else if (targetType != null && permission != null) {
                // Standard: hasPermission('call','read') -> targetDomainObject='call', permission='read' goes to 2-arg, not here
                // For 4-arg, use targetType as module, permission as action
                module = String.valueOf(targetType).trim();
                action = String.valueOf(permission).trim();
                if (targetId != null) {
                    // If targetId is also a string module, prefer it
                    String tidStr = String.valueOf(targetId).trim();
                    if (!tidStr.isBlank() && !tidStr.equals(module)) {
                        // hasPermission('admin','settings') via 4-arg would be targetId='admin', targetType='settings'
                        module = tidStr;
                        action = targetType.trim();
                    }
                }
            }
        } else if (targetType != null) {
            module = String.valueOf(targetId != null ? targetId : "").trim();
            action = targetType.trim();
        } else if (targetId != null) {
            String tidStr = String.valueOf(targetId).trim();
            if (tidStr.contains(":")) {
                String[] parts = tidStr.split(":", 2);
                module = parts[0].trim();
                action = parts[1].trim();
            }
        }
        if (module == null || module.isBlank() || action == null || action.isBlank()) {
            return false;
        }
        UUID userId;
        try {
            userId = UUID.fromString(principal);
        } catch (IllegalArgumentException e) {
            log.debug("PermissionEvaluator denied (4-arg): invalid principal");
            return false;
        }
        UUID tenantId = tenantContext.getTenantId();
        boolean result = permissionEvaluatorService.hasPermission(userId, tenantId, module, action);
        log.debug("PermissionEvaluator (4-arg) hasPermission userId={} tenantId={} module={} action={} => {}", userId, tenantId, module, action, result);
        return result;
    }
}
