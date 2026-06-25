package com.shivang.crm.shared.service;

import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.shared.model.OwnershipScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionEvaluatorService permissionEvaluatorService;

    public boolean hasPermission(UUID tenantId, UUID userId, String permission) {
        return permissionEvaluatorService.hasPermission(tenantId, userId, permission);
    }

    public OwnershipScope getOwnershipScope(UUID tenantId, UUID userId, String module) {
        return permissionEvaluatorService.getOwnershipScope(tenantId, userId, module);
    }

    public List<OwnershipScope> getUserOwnershipScopes(UUID tenantId) {
        UUID userId = com.shivang.crm.shared.security.UserContext.getCurrentUserId();
        return permissionEvaluatorService.getUserOwnershipScopes(tenantId, userId);
    }

    public boolean isInSameTeam(UUID tenantId, UUID user1Id, UUID user2Id) {
        return permissionEvaluatorService.isInSameTeam(tenantId, user1Id, user2Id);
    }
}
