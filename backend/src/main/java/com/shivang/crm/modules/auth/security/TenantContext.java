package com.shivang.crm.modules.auth.security;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped holder for current tenant and user context.
 * Set by TenantResolutionFilter on every authenticated request.
 * Cleared after request completes to prevent memory leaks.
 */
@Component
@RequestScope
public class TenantContext {

    private String tenantId;
    private String userId;
    private String userEmail;

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

     public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
    
    public String getUserEmail() {
        return userEmail;
    }

    public void clear() {
        this.tenantId = null;
        this.userId = null;
        this.userEmail = null;
    }


     public boolean hasTenant() {
        return tenantId != null && !tenantId.isEmpty() && !tenantId.equals("null");
    }
}