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
            "admin",
            "offering",
            "entitlement",
            "acquisition",
            "records",
            "ticket",
            "feedback"
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
            "report",
            "offering",
            "entitlement",
            "acquisition",
            "ticket",
            "feedback"
    );

    /**
     * L3.1-P2: {@code assign} was already part of this action vocabulary for
     * lead/contact/account/deal/activity and task/call/meeting, and L3.0-P3 added
     * entitlement (V68). Ticket now joins them, so a MANAGER may assign tickets to
     * their team. Note this set excludes {@code delete}: a manager can route work
     * but cannot destroy correspondence.
     */
    public static final Set<String> MANAGER_ACTIONS = Set.of(
            "read",
            "write",
            "assign",
            "export"
    );

    /**
     * L3.1-P1: 'ticket' is a customer-domain module, so EMPLOYEE gets it at OWN
     * scope with the same {read, write} action set as every other module listed
     * here. EMPLOYEE therefore never acquires ticket:delete through any path.
     * Keep in sync with create_tenant_default_roles() in V70.
     */
    public static final Set<String> EMPLOYEE_MODULES = Set.of(
            "lead",
            "contact",
            "account",
            "deal",
            "activity",
            "task",
            "call",
            "meeting",
            "entitlement",
            "ticket",
            "feedback"
    );

    /**
     * L3.1-P2: EMPLOYEE deliberately never receives {@code assign} for any module.
     * An employee can raise and work a ticket (they may set an initial owner when
     * creating one) but cannot route one to somebody else. This mirrors the
     * entitlement decision in V68 and is enforced on both seeding paths.
     */
    public static final Set<String> EMPLOYEE_ACTIONS = Set.of(
            "read",
            "write"
    );

    /**
     * Scope assigned to every EMPLOYEE permission. Mirrors the 'OWN' scope used by
     * the tenant-default-role trigger in the migration layer (see V50) so the Java
     * fallback and the PostgreSQL seed stay behaviourally aligned.
     */
    public static final String EMPLOYEE_ACCESS_SCOPE = "OWN";

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