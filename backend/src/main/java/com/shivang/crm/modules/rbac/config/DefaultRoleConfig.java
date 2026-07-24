package com.shivang.crm.modules.rbac.config;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.rbac.entity.Permission;

@Component
public class DefaultRoleConfig {

    public static final Set<String> ADMIN_MODULES = Set.of(
            "lead",
            "contact",
            "account",
            "deal",
            "activity",
            "task",
            "call",
            "meeting",
            "report",
            "workflow",
            "admin"
    );

    public static final Set<String> MANAGER_MODULES = Set.of(
            "lead",
            "contact",
            "account",
            "deal",
            "activity",
            "task",
            "call",
            "meeting",
            "report"
    );

    public static final Set<String> MANAGER_ACTIONS = Set.of(
            "read",
            "write",
            "assign",
            "export"
    );

    public static final Set<String> EMPLOYEE_MODULES = Set.of(
            "lead",
            "contact",
            "account",
            "deal",
            "activity",
            "task",
            "call",
            "meeting"
    );

    public static final Set<String> EMPLOYEE_ACTIONS = Set.of(
            "read",
            "write"
    );

    public boolean isAdminPermission(Permission permission) {
        return ADMIN_MODULES.contains(permission.getModule());
    }

    public boolean isManagerPermission(Permission permission) {
        return MANAGER_MODULES.contains(permission.getModule())
                && MANAGER_ACTIONS.contains(permission.getAction());
    }

    public boolean isEmployeePermission(Permission permission) {
        return EMPLOYEE_MODULES.contains(permission.getModule())
                && EMPLOYEE_ACTIONS.contains(permission.getAction());
    }
}