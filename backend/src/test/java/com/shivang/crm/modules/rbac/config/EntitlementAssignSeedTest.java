package com.shivang.crm.modules.rbac.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.rbac.entity.Permission;

/**
 * L3.0-P3: entitlement:assign seed parity.
 *
 * The assignment permission must be granted through the existing role-seed
 * vocabulary, and EMPLOYEE must never acquire it. P1's EMPLOYEE entitlement
 * read/write = OWN correction must remain intact.
 */
class EntitlementAssignSeedTest {

    /** Mirrors create_tenant_default_roles() in V50 and the V68 grant block. */
    private static boolean triggerGrants(String roleName, String module, String action) {
        return switch (roleName) {
            // ADMIN: every action for its modules
            case "ADMIN" -> DefaultRoleConfig.ADMIN_MODULES.contains(module);
            // MANAGER: its modules restricted to MANAGER_ACTIONS
            case "MANAGER" -> DefaultRoleConfig.MANAGER_MODULES.contains(module)
                    && DefaultRoleConfig.MANAGER_ACTIONS.contains(action);
            // EMPLOYEE: its modules restricted to EMPLOYEE_ACTIONS (read, write)
            case "EMPLOYEE" -> DefaultRoleConfig.EMPLOYEE_MODULES.contains(module)
                    && DefaultRoleConfig.EMPLOYEE_ACTIONS.contains(action);
            default -> false;
        };
    }

    private Permission permission(String module, String action) {
        Permission permission = new Permission();
        permission.setModule(module);
        permission.setAction(action);
        return permission;
    }

    @Test
    void adminReceivesEntitlementAssign() {
        assertThat(triggerGrants("ADMIN", "entitlement", "assign")).isTrue();
        assertThat(new DefaultRoleConfig().isAdminPermission(permission("entitlement", "assign"))).isTrue();
    }

    @Test
    void managerReceivesEntitlementAssign() {
        assertThat(triggerGrants("MANAGER", "entitlement", "assign")).isTrue();
        assertThat(new DefaultRoleConfig().isManagerPermission(permission("entitlement", "assign"))).isTrue();
    }

    @Test
    void employeeNeverReceivesEntitlementAssign() {
        assertThat(triggerGrants("EMPLOYEE", "entitlement", "assign")).isFalse();
        assertThat(new DefaultRoleConfig().isEmployeePermission(permission("entitlement", "assign"))).isFalse();
    }

    @Test
    void employeeStillReceivesEntitlementReadAndWrite() {
        assertThat(triggerGrants("EMPLOYEE", "entitlement", "read")).isTrue();
        assertThat(triggerGrants("EMPLOYEE", "entitlement", "write")).isTrue();
    }

    /**
     * P1 regression: EMPLOYEE entitlement read/write must remain OWN.
     * The assign action is granted at OWN for employee-holding roles, which is
     * why the action is excluded from EMPLOYEE_MODULES' action set rather than
     * from the module set.
     */
    @Test
    void p1EmployeeEntitlementOwnScopeCorrectionRemainsIntact() {
        assertThat(DefaultRoleConfig.EMPLOYEE_ACCESS_SCOPE).isEqualTo("OWN");
        assertThat(DefaultRoleConfig.EMPLOYEE_ACTIONS).containsExactlyInAnyOrder("read", "write");
        assertThat(DefaultRoleConfig.EMPLOYEE_MODULES).contains("entitlement");
    }

    @Test
    void assignIsTheEstablishedActionVocabularyNotANewConcept() {
        // Every owned CRM module already separates assignment via '<module>:assign'.
        for (String module : List.of("lead", "contact", "account", "deal", "activity",
                "task", "call", "meeting", "entitlement")) {
            assertThat(triggerGrants("ADMIN", module, "assign"))
                    .as("%s:assign", module)
                    .isTrue();
        }
        assertThat(DefaultRoleConfig.MANAGER_ACTIONS).contains("assign");
    }

    /**
     * The V68 migration must grant assign to SUPERADMIN/ADMIN/MANAGER and must
     * not mention EMPLOYEE, so the P1 OWN correction cannot be widened by it.
     */
    @Test
    void v68MigrationExcludesEmployeeAndIsIdempotent() throws Exception {
        Path migration = Path.of("src", "main", "resources", "db", "migration",
                "V68__seed_entitlement_assign_permission.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertThat(sql).contains("'entitlement', 'assign'");
        assertThat(sql).contains("r.name = 'SUPERADMIN'");
        assertThat(sql).contains("r.name = 'ADMIN'");
        assertThat(sql).contains("r.name = 'MANAGER'");
        assertThat(sql).doesNotContain("r.name = 'EMPLOYEE'");
        assertThat(sql).contains("ON CONFLICT (module, action) DO NOTHING");
    }

    /**
     * P1's V67 migration must remain present and must still correct EMPLOYEE
     * entitlement read/write to OWN.
     */
    @Test
    void p1MigrationRemainsIntact() throws Exception {
        Path migration = Path.of("src", "main", "resources", "db", "migration",
                "V67__fix_employee_entitlement_access_scope.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql).contains("r.name = 'EMPLOYEE'");
        assertThat(sql).contains("SET access_scope = 'OWN'");
        assertThat(sql).contains("p.action IN ('read', 'write')");
    }
}