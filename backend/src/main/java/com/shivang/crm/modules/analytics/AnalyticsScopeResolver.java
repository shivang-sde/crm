package com.shivang.crm.modules.analytics;

import org.springframework.stereotype.Component;

import java.util.UUID;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.PermissionDeniedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Derives the effective {@link AnalyticsContext} from the authenticated
 * request's existing {@link TenantContext} (populated by
 * TenantResolutionFilter from the JWT). No second role hierarchy and no
 * client-supplied identifiers are involved.
 *
 * Actual role mapping found in this application:
 *
 *   SUPERADMIN (level PLATFORM)          -> PLATFORM
 *   RESELLER   (level PLATFORM)          -> RESELLER (resellerId = user id,
 *                                           matching tenants.reseller_id)
 *   ADMIN      (tenant level)            -> TENANT
 *   MANAGER / EMPLOYEE / custom roles    -> USER
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsScopeResolver {

    private final TenantContext tenantContext;

    /**
     * @param requestedScope optional client-requested downgrade; may never
     *                       exceed the caller's derived authority and is only
     *                       honored when it can be resolved without trusting
     *                       client-supplied IDs.
     */
    public AnalyticsContext resolve(String requestedScope) {
        AnalyticsScope effective = deriveScope();
        AnalyticsScope requested = parseRequested(requestedScope);

        if (requested != null && requested != effective) {
            if (requested.getRank() > effective.getRank()) {
                throw new PermissionDeniedException(
                        "Requested analytics scope exceeds the authenticated user's authority");
            }
            // Only USER is derivable without external IDs (needs tenant + user).
            if (requested != AnalyticsScope.USER || !tenantContext.hasTenant()) {
                throw new BusinessException("INVALID_ANALYTICS_SCOPE",
                        "Requested analytics scope cannot be resolved for the authenticated user");
            }
            effective = AnalyticsScope.USER;
        }

        return build(effective);
    }

    private AnalyticsScope deriveScope() {
        String level = tenantContext.getUserLevel();
        String role = tenantContext.getRole();

        if ("PLATFORM".equals(level)) {
            // Platform roles are SUPERADMIN and RESELLER.
            if ("RESELLER".equals(role)) {
                return AnalyticsScope.RESELLER;
            }
            return AnalyticsScope.PLATFORM;
        }
        // Tenant level: ADMIN sees the whole tenant, everyone else own records.
        if ("ADMIN".equals(role)) {
            return AnalyticsScope.TENANT;
        }
        return AnalyticsScope.USER;
    }

    private AnalyticsScope parseRequested(String requestedScope) {
        if (requestedScope == null || requestedScope.isBlank()) {
            return null;
        }
        try {
            return AnalyticsScope.fromString(requestedScope);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_ANALYTICS_SCOPE", e.getMessage());
        }
    }

    private AnalyticsContext build(AnalyticsScope scope) {
        UUID userId = tenantContext.getUserId();
        if (userId == null) {
            throw new BusinessException("INVALID_ANALYTICS_SCOPE", "User identity is not available");
        }
        return switch (scope) {
            case PLATFORM -> new AnalyticsContext(scope, null, null, userId);
            case RESELLER ->
                // tenants.reseller_id references the platform reseller user id.
                new AnalyticsContext(scope, null, userId, userId);
            case TENANT -> new AnalyticsContext(scope, tenantContext.requireTenantId(), null, userId);
            case USER -> new AnalyticsContext(scope, tenantContext.requireTenantId(), null, userId);
        };
    }
}
