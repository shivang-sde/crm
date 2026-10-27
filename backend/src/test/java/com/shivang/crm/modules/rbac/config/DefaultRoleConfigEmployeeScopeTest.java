package com.shivang.crm.modules.rbac.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.rbac.entity.Permission;

class DefaultRoleConfigEmployeeScopeTest {

    private final DefaultRoleConfig config = new DefaultRoleConfig();

    @Test
    void employeeAccessScopeIsOwn() {
        assertThat(DefaultRoleConfig.EMPLOYEE_ACCESS_SCOPE).isEqualTo("OWN");
    }

    @Test
    void employeeIsGrantedEntitlementReadAtOwnScope() {
        Permission read = permission("entitlement", "read");

        assertThat(config.isEmployeePermission(read)).isTrue();
        assertThat(scopeGrantedToEmployee(read)).isEqualTo("OWN");
    }

    @Test
    void employeeIsGrantedEntitlementWriteAtOwnScope() {
        Permission write = permission("entitlement", "write");

        assertThat(config.isEmployeePermission(write)).isTrue();
        assertThat(scopeGrantedToEmployee(write)).isEqualTo("OWN");
    }

    @Test
    void employeeIsNotGrantedEntitlementDelete() {
        assertThat(config.isEmployeePermission(permission("entitlement", "delete"))).isFalse();
    }

    @Test
    void employeeScopeIsOwnForEveryGrantedModule() {
        for (String module : DefaultRoleConfig.EMPLOYEE_MODULES) {
            for (String action : DefaultRoleConfig.EMPLOYEE_ACTIONS) {
                Permission permission = permission(module, action);
                if (config.isEmployeePermission(permission)) {
                    assertThat(scopeGrantedToEmployee(permission))
                        .as("%s:%s", module, action)
                        .isEqualTo("OWN");
                }
            }
        }
    }

    @Test
    void adminAndManagerScopesAreUnaffected() {
        Permission adminRead = permission("entitlement", "read");
        assertThat(config.isAdminPermission(adminRead)).isTrue();
        // ADMIN/MANAGER scope constants are not part of DefaultRoleConfig; the
        // provisioning service hard-codes 'ALL' and 'TEAM'. This test documents
        // that only the EMPLOYEE scope was introduced by L3.0-P1.
        assertThat(DefaultRoleConfig.EMPLOYEE_MODULES).contains("entitlement");
        assertThat(Set.of(DefaultRoleConfig.EMPLOYEE_ACCESS_SCOPE)).containsExactly("OWN");
    }

    private String scopeGrantedToEmployee(Permission permission) {
        return config.isEmployeePermission(permission)
                ? DefaultRoleConfig.EMPLOYEE_ACCESS_SCOPE
                : "NOT_GRANTED";
    }

    private Permission permission(String module, String action) {
        Permission permission = new Permission();
        permission.setModule(module);
        permission.setAction(action);
        return permission;
    }
}