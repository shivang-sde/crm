package com.shivang.crm.modules.analytics;

import org.springframework.stereotype.Component;

import java.util.UUID;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
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
 * The analytics perspective is derived from the authenticated user's stored
 * {@code report:read} access scope (via
 * {@link PermissionEvaluatorService#getAccessScope}), NOT from role names.
 * Role/level is consulted only to distinguish the platform shell:
 *
 *   PLATFORM level, role SUPERADMIN       -> PLATFORM
 *   PLATFORM level, role RESELLER         -> RESELLER (resellerId = user id,
 *                                          matching tenants.reseller_id)
 *   TENANT level, report:read = ALL       -> TENANT
 *   TENANT level, report:read = TEAM      -> TEAM
 *   TENANT level, report:read = OWN       -> USER
 *   report:read missing / NONE            -> denied (fail-closed)
 *
 * A tenant-created custom role with report:read = ALL/TEAM/OWN therefore gets
 * TENANT/TEAM/USER respectively, independent of its role name.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsScopeResolver {

    private final TenantContext tenantContext;
    private final PermissionEvaluatorService permissionEvaluatorService;

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
        UUID userId = tenantContext.getUserId();
        if (userId == null) {
            throw new BusinessException("INVALID_ANALYTICS_SCOPE", "User identity is not available");
        }

        if ("PLATFORM".equals(tenantContext.getUserLevel())) {
            requireReportRead(userId, null);
            // Platform shell: only SUPERADMIN and RESELLER exist here.
            return "RESELLER".equals(tenantContext.getRole())
                    ? AnalyticsScope.RESELLER
                    : AnalyticsScope.PLATFORM;
        }

        // Tenant level: the stored report:read access scope is authoritative.
        UUID tenantId = tenantContext.requireTenantId();
        String scope = requireReportRead(userId, tenantId);
        return switch (scope) {
            case "ALL" -> AnalyticsScope.TENANT;
            case "TEAM" -> AnalyticsScope.TEAM;
            case "OWN" -> AnalyticsScope.USER;
            default -> throw new PermissionDeniedException("ACCESS_DENIED",
                    "You do not have permission to view analytics");
        };
    }

    private String requireReportRead(UUID userId, UUID tenantId) {
        String scope = permissionEvaluatorService.getAccessScope(userId, tenantId, "report", "read");
        if (!"ALL".equals(scope) && !"TEAM".equals(scope) && !"OWN".equals(scope)) {
            throw new PermissionDeniedException("ACCESS_DENIED",
                    "You do not have permission to view analytics");
        }
        return scope;
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
            case TEAM -> new AnalyticsContext(scope, tenantContext.requireTenantId(), null, userId);
            case USER -> new AnalyticsContext(scope, tenantContext.requireTenantId(), null, userId);
        };
    }
}
