package com.shivang.crm.modules.auth.service.impl;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.rbac.entity.UserRole;
import com.shivang.crm.modules.tenant.entity.Tenant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TenantProvisionResult {
    private final Tenant tenant;
    private final User adminUser;
    private final UserRole adminUserRole;
}
