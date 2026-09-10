package com.shivang.crm.modules.rbac.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

/**
 * Wires the custom {@link RbacPermissionEvaluator} into Spring Method Security
 * so that {@code @PreAuthorize("hasPermission('module','action')")} delegates to
 * {@link com.shivang.crm.modules.rbac.service.PermissionEvaluatorService}
 * (tenant-aware, SUPERADMIN-aware, cache-backed) instead of the default
 * {@code DenyAllPermissionEvaluator}.
 *
 * No duplicate handler exists in the codebase (verified via grep for
 * MethodSecurityExpressionHandler); this is the sole handler.
 */
@Configuration
public class RbacMethodSecurityConfig {

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RbacPermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}
