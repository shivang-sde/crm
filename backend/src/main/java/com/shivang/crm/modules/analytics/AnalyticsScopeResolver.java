package com.shivang.crm.modules.analytics;

import org.springframework.stereotype.Component;

import java.util.UUID;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.tenant.repository.TenantRepository;
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
 *
 * Optional tenantId parameter allows SUPERADMIN/RESELLER to drill into a
 * specific tenant. When authorized, the effective scope becomes TENANT
 * for the selected tenant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsScopeResolver {

    private final TenantContext tenantContext;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final TenantRepository tenantRepository;

    /**
     * @param requestedScope optional client-requested scope downgrade; may never
     *                       exceed the caller's derived authority
     * @param requestedTenantId optional tenant UUID to drill into; validated
     *                          against caller's authority before use
     */
    public AnalyticsContext resolve(String requestedScope, UUID requestedTenantId) {
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

        // Handle tenantId drill-down
        if (requestedTenantId != null) {
            validateTenantAccess(requestedTenantId);
            // When a specific tenant is selected, analytics run as TENANT scope
            // for that tenant, regardless of the caller's original scope
            return buildTenantContext(requestedTenantId);
        }

        return build(effective);
    }

    private void validateTenantAccess(UUID tenantId) {
        UUID userId = tenantContext.getUserId();
        String userLevel = tenantContext.getUserLevel();
        String role = tenantContext.getRole();

        if (userId == null) {
            throw new BusinessException("INVALID_ANALYTICS_SCOPE", "User identity is not available");
        }

        // SUPERADMIN can access any existing tenant
        if ("PLATFORM".equals(userLevel) && "SUPERADMIN".equals(role)) {
            tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Tenant not found"));
            return;
        }

        // RESELLER can only access tenants owned by them. Non-existent tenants
        // return the same denial, so tenant existence is not leaked.
        if ("PLATFORM".equals(userLevel) && "RESELLER".equals(role)) {
            boolean owned = tenantRepository.findById(tenantId)
                    .map(t -> userId.equals(t.getResellerId()))
                    .orElse(false);
            if (!owned) {
                throw new PermissionDeniedException("ACCESS_DENIED",
                        "You do not have permission to access this tenant");
            }
            return;
        }

        // TENANT users can only access their own tenant, and only when they can
        // already view the whole tenant (report:read = ALL). Users with TEAM/OWN
        // scope must not widen their analytics via tenantId drill-down.
        if ("TENANT".equals(userLevel)) {
            UUID currentTenantId = tenantContext.getTenantId();
            if (currentTenantId == null || !currentTenantId.equals(tenantId)) {
                throw new PermissionDeniedException("ACCESS_DENIED",
                        "You do not have permission to access this tenant");
            }
            if (!"ALL".equals(requireReportRead(userId, currentTenantId))) {
                throw new PermissionDeniedException("ACCESS_DENIED",
                        "You do not have permission to view analytics for this scope");
            }
            return;
        }

        throw new PermissionDeniedException("ACCESS_DENIED",
                "You do not have permission to access this tenant");
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

    private AnalyticsContext buildTenantContext(UUID tenantId) {
        UUID userId = tenantContext.getUserId();
        if (userId == null) {
            throw new BusinessException("INVALID_ANALYTICS_SCOPE", "User identity is not available");
        }
        // When drilling into a specific tenant, always use TENANT scope
        return new AnalyticsContext(AnalyticsScope.TENANT, tenantId, null, userId);
    }
}
