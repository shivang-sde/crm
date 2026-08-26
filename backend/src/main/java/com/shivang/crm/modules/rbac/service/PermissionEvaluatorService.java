package com.shivang.crm.modules.rbac.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.rbac.entity.Permission;
import com.shivang.crm.modules.rbac.entity.RolePermission;
import com.shivang.crm.modules.rbac.entity.UserRole;
import com.shivang.crm.modules.rbac.repository.PermissionRepository;
import com.shivang.crm.modules.rbac.repository.RolePermissionRepository;
import com.shivang.crm.modules.rbac.repository.UserRoleRepository;
import com.shivang.crm.shared.enums.OwnershipScope;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionEvaluatorService {

    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    @Transactional
    @Cacheable(value = "userPermissions", key = "#userId + ':' + #tenantId")
    public UserPermissionContext getUserPermissions(UUID userId, UUID tenantId) {
        // Get user's role for this tenant or platform
        UserRole userRole;
        if (tenantId == null) {
            userRole = userRoleRepository.findPlatformRole(userId)
                    .orElseThrow(() -> new BusinessException("USER_NO_ROLE", "User has no role assigned"));
        } else {
            userRole = userRoleRepository.findByUserIdAndTenantId(userId, tenantId)
                    .orElseThrow(() -> new BusinessException("USER_NO_ROLE", "User has no role assigned"));
        }

        // Get all permissions for this role
        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleId(userRole.getRoleId());

        // Build permission map. Only recognized scopes are treated as grants:
        // a missing/corrupt scope or an explicit NONE must never appear as a
        // granted key (fail-closed).
        Map<String, String> permissionScopeMap = new HashMap<>();
        for (RolePermission rp : rolePermissions) {
            String scope = normalizeGrantedScope(rp.getAccessScope());
            if (scope == null) {
                log.warn("Ignoring role_permission {} with invalid accessScope '{}' for role {}",
                        rp.getId(), rp.getAccessScope(), userRole.getRoleId());
                continue;
            }
            Permission permission = rp.getPermission();
            String key = permission.getModule() + ":" + permission.getAction();
            permissionScopeMap.put(key, scope);
        }

        return UserPermissionContext.builder()
                .userId(userId)
                .tenantId(tenantId)
                .roleId(userRole.getRoleId())
                .roleName(userRole.getRole().getName())
                .permissions(permissionScopeMap)
                .build();
    }

    public List<UUID> getTeamUserIds(UUID managerId, UUID tenantId) {
        return userRepository.findTeamUserIdsByManagerAndTenant(tenantId, managerId);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, UUID tenantId, String module, String action) {
        // Platform bypass: SUPERADMIN retains full access (including modules
        // whose permissions are not yet seeded).
        if (isSuperadmin(userId)) {
            return true;
        }

        // FAIL-CLOSED: a permission that is not defined in the catalog is denied.
        if (!isPermissionDefined(module, action)) {
            log.debug("Permission {}/{} not defined in system, denying access", module, action);
            return false;
        }

        try {
            UserPermissionContext ctx = getUserPermissions(userId, tenantId);
            String key = module + ":" + action;
            boolean hasPermission = ctx.getPermissions().containsKey(key);

            if (!hasPermission) {
                log.debug("User {} lacks permission {}/{}", userId, module, action);
            }

            return hasPermission;
        } catch (BusinessException e) {
            // User has no role assigned
            log.warn("User {} has no role assigned for tenant {}", userId, tenantId);
            return false;
        }
    }

    /**
     * Convenience method for checking permission with "module:action" format
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID tenantId, UUID userId, String permission) {
        if (permission == null || !permission.contains(":")) {
            log.warn("Invalid permission format: {}", permission);
            return false;
        }
        String[] parts = permission.split(":", 2);
        return hasPermission(userId, tenantId, parts[0], parts[1]);
    }

    @Transactional(readOnly = true)
    public String getAccessScope(UUID userId, UUID tenantId, String module, String action) {
        // Superadmin gets ALL scope
        if (isSuperadmin(userId)) {
            return "ALL";
        }

        // FAIL-CLOSED: undefined permission resolves to no access
        if (!isPermissionDefined(module, action)) {
            return "NONE";
        }

        try {
            UserPermissionContext ctx = getUserPermissions(userId, tenantId);
            String key = module + ":" + action;
            return ctx.getPermissions().getOrDefault(key, "NONE");
        } catch (BusinessException e) {
            return "NONE";
        }
    }

    /**
     * Get ownership scope for a specific module
     */
    @Transactional(readOnly = true)
    public OwnershipScope getOwnershipScope(UUID tenantId, UUID userId, String module) {
        try {
            UserPermissionContext ctx = getUserPermissions(userId, tenantId);

            // Check for write permission scope
            String writeKey = module + ":write";
            if (ctx.getPermissions().containsKey(writeKey)) {
                return OwnershipScope.fromString(ctx.getPermissions().get(writeKey));
            }

            // Check for read permission scope as fallback
            String readKey = module + ":read";
            if (ctx.getPermissions().containsKey(readKey)) {
                return OwnershipScope.fromString(ctx.getPermissions().get(readKey));
            }

            // No grant at all: OWN is the most restrictive scope and never elevates access.
            return OwnershipScope.OWN;
        } catch (BusinessException e) {
            return OwnershipScope.OWN;
        }
    }

    /**
     * Get all ownership scopes for user's permissions
     */
    @Transactional(readOnly = true)
    public List<OwnershipScope> getUserOwnershipScopes(UUID tenantId, UUID userId) {
        try {
            UserPermissionContext ctx = getUserPermissions(userId, tenantId);
            return ctx.getPermissions().values().stream()
                    .map(OwnershipScope::fromString)
                    .distinct()
                    .toList();
        } catch (BusinessException e) {
            return List.of(OwnershipScope.OWN);
        }
    }

    /**
     * Check if two users are in the same team
     */
    public boolean isInSameTeam(
        UUID tenantId,
        UUID user1Id,
        UUID user2Id
) {
    List<UUID> teamMembers =
            getTeamUserIds(user1Id, tenantId);

    return teamMembers.contains(user2Id);
}

    /**
     * Check if a permission is defined in the system
     */
    private boolean isPermissionDefined(String module, String action) {
        return permissionRepository.existsByModuleAndAction(module, action);
    }

    /**
     * Returns the granted scope in canonical form (ALL/TEAM/OWN) or null when the
     * stored scope must NOT be treated as a grant (null, NONE or unrecognized).
     */
    private String normalizeGrantedScope(String accessScope) {
        if (accessScope == null) {
            return null;
        }
        String normalized = accessScope.trim().toUpperCase();
        if ("ALL".equals(normalized) || "TEAM".equals(normalized) || "OWN".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    /**
     * Centralized SUPERADMIN platform-role check (single authorization path).
     * Public so role-management delegation logic can reuse it without
     * introducing a second superadmin mechanism.
     */
    public boolean isSuperadmin(UUID userId) {
        // Check if user has SUPERADMIN role
        return userRoleRepository.findPlatformRole(userId).stream()
                .anyMatch(ur -> "SUPERADMIN".equals(ur.getRole().getName()));
    }
}