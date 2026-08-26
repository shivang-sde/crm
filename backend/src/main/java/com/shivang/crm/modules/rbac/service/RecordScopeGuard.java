package com.shivang.crm.modules.rbac.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.shared.exception.PermissionDeniedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RBAC-7: record-level access-scope enforcement helper.
 *
 * Single authority for resolving a caller's effective record scope for a
 * catalog permission. Resolution is delegated to
 * {@link PermissionEvaluatorService#getAccessScope} (fail-closed since
 * RBAC-1: undefined permission/NONE/corrupt scope -> "NONE").
 *
 * Semantics:
 *   ALL  -> every record within the caller's TENANT (never cross-tenant)
 *   TEAM -> records owned by team members (existing manager-hierarchy model)
 *   OWN  -> only records owned/created by the caller
 *   none -> DENY (thrown)
 *
 * Tenant isolation is always applied by the surrounding repository queries
 * (findByIdAndTenantId / Specification.hasTenant); scope NEVER widens the
 * tenant boundary. SUPERADMIN resolves to ALL through the centralized
 * evaluator bypass.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordScopeGuard {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final UserRepository userRepository;

    /**
     * Resolves the caller's effective record scope for module:action.
     * Returns OWN, TEAM or ALL; throws for NONE/missing/malformed scopes.
     */
    public String requireScope(UUID tenantId, UUID userId, String module, String action) {
        if (tenantId == null || userId == null) {
            throw new PermissionDeniedException("SCOPE_DENIED",
                    "Tenant and user context are required");
        }

        String scope = permissionEvaluatorService.getAccessScope(userId, tenantId, module, action);
        String normalized = scope == null ? "" : scope.trim().toUpperCase();

        return switch (normalized) {
            case "OWN", "TEAM", "ALL" -> normalized;
            default -> {
                log.debug("Record scope denied: {}:{} resolves to '{}' for user {}", module, action, scope, userId);
                throw new PermissionDeniedException("SCOPE_DENIED",
                        "You do not have record-level access for this action");
            }
        };
    }

    /**
     * Team membership per the existing manager-hierarchy model.
     */
    public List<UUID> teamMemberIds(UUID tenantId, UUID userId) {
        return permissionEvaluatorService.getTeamUserIds(userId, tenantId);
    }

    /**
     * Whether a record falls within the given scope, mirroring the CRM list
     * specifications for modules whose ownership model is owner+creator:
     *   OWN  -> owner == caller OR creator == caller
     *   TEAM -> owner in caller's team OR owner == caller OR creator == caller
     *   ALL  -> true (tenant boundary enforced by the query itself)
     */
    public boolean withinOwnerCreatorScope(
            String scope,
            UUID tenantId,
            UUID userId,
            UUID ownerId,
            UUID createdBy) {

        if (userId == null) {
            return false;
        }

        return switch (scope) {
            case "ALL" -> true;
            case "OWN" -> userId.equals(ownerId) || userId.equals(createdBy);
            case "TEAM" -> {
                if (userId.equals(ownerId) || userId.equals(createdBy)) {
                    yield true;
                }
                if (ownerId == null) {
                    yield false;
                }
                yield teamMemberIds(tenantId, userId).contains(ownerId);
            }
            default -> false;
        };
    }

    /**
     * Assert variant of {@link #withinOwnerCreatorScope}; throws a generic
     * authorization error that does not leak ownership or existence details.
     */
    public void assertWithinOwnerCreatorScope(
            String scope,
            UUID tenantId,
            UUID userId,
            UUID ownerId,
            UUID createdBy) {
        if (!withinOwnerCreatorScope(scope, tenantId, userId, ownerId, createdBy)) {
            throw new PermissionDeniedException("SCOPE_DENIED",
                    "Record is outside your access scope");
        }
    }
}
