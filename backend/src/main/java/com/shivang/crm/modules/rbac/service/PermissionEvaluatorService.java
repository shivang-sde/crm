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
    private final PermissionRepository permissionRepository; // Add this

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

        // Build permission map
        Map<String, String> permissionScopeMap = new HashMap<>();
        for (RolePermission rp : rolePermissions) {
            Permission permission = rp.getPermission();
            String key = permission.getModule() + ":" + permission.getAction();
            permissionScopeMap.put(key, rp.getAccessScope());
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
        // Allow all read operations on admin module (role_read, etc.)
        // This allows users to read their own role info without explicit permission
        if ("admin".equals(module) && action.contains("read")) {
            log.debug("Allowing read access on admin module for action: {}", action);
            return true;
        }

        // Check if this permission exists in the system
        if (!isPermissionDefined(module, action)) {
            // Permission not defined in DB - allow access (no RBAC required)
            log.debug("Permission {}/{} not defined in system, allowing access", module, action);
            return true;
        }

        // Superadmin bypass (only if permission exists)
        if (isSuperadmin(userId)) {
            return true;
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

    @Transactional(readOnly = true)
    public String getAccessScope(UUID userId, UUID tenantId, String module, String action) {
        // If permission doesn't exist, return ALL (full access)
        if (!isPermissionDefined(module, action)) {
            return "ALL";
        }

        // Superadmin gets ALL scope
        if (isSuperadmin(userId)) {
            return "ALL";
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
     * Check if a permission is defined in the system
     */
    private boolean isPermissionDefined(String module, String action) {
        return permissionRepository.existsByModuleAndAction(module, action);
    }

    private boolean isSuperadmin(UUID userId) {
        // Check if user has SUPERADMIN role
        return userRoleRepository.findPlatformRole(userId).stream()
                .anyMatch(ur -> "SUPERADMIN".equals(ur.getRole().getName()));
    }
}