package com.shivang.crm.shared.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.shared.enums.OwnershipScope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionEvaluatorService permissionEvaluatorService;

    /**
     * Check if user has permission for a specific module:action
     */
    public boolean hasPermission(UUID tenantId, UUID userId, String permission) {
        return permissionEvaluatorService.hasPermission(tenantId, userId, permission);
    }

    /**
     * Get ownership scope for a specific module
     */
    public OwnershipScope getOwnershipScope(UUID tenantId, UUID userId, String module) {
        return permissionEvaluatorService.getOwnershipScope(tenantId, userId, module);
    }

    /**
     * Get all ownership scopes for user's permissions
     * FIXED: Added missing userId parameter
     */
    public List<OwnershipScope> getUserOwnershipScopes(UUID tenantId, UUID userId) {
        return permissionEvaluatorService.getUserOwnershipScopes(tenantId, userId);
    }

    /**
     * Check if two users are in the same team
     */
    public boolean isInSameTeam(UUID tenantId, UUID user1Id, UUID user2Id) {
        return permissionEvaluatorService.isInSameTeam(tenantId, user1Id, user2Id);
    }
}