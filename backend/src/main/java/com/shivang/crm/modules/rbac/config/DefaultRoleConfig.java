package com.shivang.crm.modules.rbac.config;

import java.util.Set;
import org.springframework.stereotype.Component;

import com.shivang.crm.modules.rbac.entity.Permission;

@Component
public class DefaultRoleConfig {

        // Module definitions
        public static final Set<String> ADMIN_MODULES = Set.of(
                        "lead", "contact", "account", "deal", "activity",
                        "report", "workflow", "admin");

        public static final Set<String> MANAGER_MODULES = Set.of(
                        "lead", "contact", "account", "deal", "activity", "report");

        public static final Set<String> MANAGER_ACTIONS = Set.of(
                        "read", "write", "assign", "export");

        public static final Set<String> EMPLOYEE_MODULES = Set.of(
                        "lead", "contact", "account", "deal", "activity");

        public static final Set<String> EMPLOYEE_ACTIONS = Set.of(
                        "read", "write");

        // New modules: task, call, meeting - included in default roles
        public static final Set<String> ACTIVITY_MODULES = Set.of(
                        "task", "call", "meeting");

        // Helper methods for permission checking
        public boolean isAdminPermission(Permission p) {
                return ADMIN_MODULES.contains(p.getModule());
        }

        public boolean isManagerPermission(Permission p) {
                return MANAGER_MODULES.contains(p.getModule())
                                && MANAGER_ACTIONS.contains(p.getAction());
        }

        public boolean isEmployeePermission(Permission p) {
                return EMPLOYEE_MODULES.contains(p.getModule())
                                && EMPLOYEE_ACTIONS.contains(p.getAction());
        }

        /**
         * Check if permission belongs to activity modules (task, call, meeting)
         */
        public boolean isActivityModulePermission(Permission p) {
                return ACTIVITY_MODULES.contains(p.getModule());
        }
}
