package com.shivang.crm.modules.analytics;

import java.util.UUID;

/**
 * Resolved analytics execution context derived from the authenticated
 * request's {@code TenantContext}. All identifiers come from the security
 * context only - never from client-supplied parameters.
 *
 * @param scope      effective analytics scope (never exceeds caller authority)
 * @param tenantId   applicable for TENANT/USER scopes, null otherwise
 * @param resellerId applicable for RESELLER scope (the platform user id that
 *                   owns tenants via {@code tenants.reseller_id}), null otherwise
 * @param userId     authenticated user id, always present
 */
public record AnalyticsContext(
        AnalyticsScope scope,
        UUID tenantId,
        UUID resellerId,
        UUID userId) {
}
