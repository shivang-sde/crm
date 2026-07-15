package com.shivang.crm.modules.auth.security;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import lombok.extern.slf4j.Slf4j;

/**
 * Request-scoped holder for current tenant and user context.
 * Set by TenantResolutionFilter on every authenticated request.
 * Cleared after request completes to prevent memory leaks.
 */
@Slf4j
@Component
@RequestScope
public class TenantContext {

    private UUID tenantId;
    private UUID userId;
    private String userLevel; // "PLATFORM" or "TENANT"
    private String role;
    private UUID roleId;
    private String userEmail;

    // ========== Setters with String conversion ==========
    
    public void setTenantId(String tenantId) {
        if (tenantId != null && !tenantId.isEmpty() && !"null".equals(tenantId)) {
            this.tenantId = UUID.fromString(tenantId);
        } else {
            this.tenantId = null;
        }
    }

    public void setUserId(String userId) {
        if (userId != null && !userId.isEmpty()) {
            this.userId = UUID.fromString(userId);
        } else {
            this.userId = null;
        }
    }

    public void setRoleId(String roleId) {
        if (roleId != null && !roleId.isEmpty()) {
            this.roleId = UUID.fromString(roleId);
        } else {
            this.roleId = null;
        }
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
    
    public void setUserLevel(String userLevel) {
        this.userLevel = userLevel;
    }
    
    public void setRole(String role) {
        this.role = role;
    }

    // ========== Getters ==========
    
    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }
    
    public String getUserLevel() {
        return userLevel;
    }
    
    public String getRole() {
        return role;
    }
    
    public UUID getRoleId() {
        return roleId;
    }

    public void clear() {
        this.tenantId = null;
        this.userId = null;
        this.userLevel = null;
        this.role = null;
        this.roleId = null;
        this.userEmail = null;
    }

    // ========== Helper Methods ==========
    
    public boolean hasTenant() {
        return tenantId != null && !tenantId.equals(new UUID(0L, 0L));
    }
    
    public boolean hasUser() {
        return userId != null;
    }
    
    public boolean isPlatformUser() {
        return "PLATFORM".equals(userLevel);
    }

    public boolean isTenantUser() {
        return "TENANT".equals(userLevel);
    }
}