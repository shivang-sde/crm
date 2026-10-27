package com.shivang.crm.modules.operations.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.auth.security.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * Resolves who the caller is for the operator recovery tooling.
 *
 * <p>Follows the existing platform/tenant split used by {@code AnalyticsScopeResolver}
 * and {@code TenantService}: a caller with no tenant is a platform operator and
 * sees everything; a caller with a tenant sees only that tenant's rows.
 *
 * <p>Note this uses the resolved {@link TenantContext}, not the JWT role string,
 * so it inherits the single DB-backed tenant source of truth rather than adding a
 * third copy of the "is this a platform user" decision.
 */
@Component
@RequiredArgsConstructor
public class OperatorScopeResolver {

    private final TenantContext tenantContext;

    /** True when the caller has no tenant, i.e. is a platform operator. */
    public boolean isPlatformScope() {
        return !tenantContext.hasTenant();
    }

    /** The caller's tenant, or null at platform scope. */
    public UUID tenantId() {
        return tenantContext.getTenantId();
    }

    public UUID userId() {
        return tenantContext.getUserId();
    }

    public String role() {
        return tenantContext.getRole();
    }

    /**
     * True when {@code rowTenantId} is visible to the caller.
     *
     * <p>A row with a null tenant is platform-owned and never visible to a tenant
     * caller. A platform caller sees every row.
     */
    public boolean canAccess(UUID rowTenantId) {
        if (isPlatformScope()) {
            return true;
        }
        UUID callerTenant = tenantContext.getTenantId();
        return rowTenantId != null && callerTenant != null && rowTenantId.equals(callerTenant);
    }

    /** Throws unless the caller may see {@code rowTenantId}. */
    public void requireAccess(UUID rowTenantId) {
        if (!canAccess(rowTenantId)) {
            throw new com.shivang.crm.shared.exception.PermissionDeniedException(
                    "ACCESS_DENIED", "This resource does not belong to your organization");
        }
    }
}
