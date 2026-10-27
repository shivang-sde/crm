package com.shivang.crm.modules.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.rbac.config.DefaultRoleConfig;

/**
 * L3.1-P2: the V71 migration and the role defaults must satisfy the assignment
 * authorization contract.
 *
 * <p>Same caveat as P1's {@code TicketMigrationContractTest}: there is no database
 * harness here, so this reads the SQL. It proves the text says the right thing,
 * not that PostgreSQL accepts it. What it reliably catches is the drift this
 * codebase has already been bitten by — a permission granted on one seeding path
 * but not the other.
 */
class TicketAssignPermissionMigrationTest {

    private static final Path V71 = Path.of("src/main/resources/db/migration/V71__seed_ticket_assign_permission.sql");

    private static String v71() {
        try {
            return Files.readString(V71).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new AssertionError("could not read " + V71, e);
        }
    }

    /** Lowercased SQL starting at the first mention of the given role id. */
    private static String grantBlock(String roleId) {
        String sql = v71();
        int index = sql.indexOf("select " + roleId);
        if (index < 0) {
            throw new AssertionError("no backfill grant for " + roleId);
        }
        return sql.substring(index, sql.indexOf("on conflict", index));
    }

    // --- permission row ---

    @Test
    void ticketAssignIsSeededIdempotently() {
        String sql = v71();

        assertThat(sql).contains("'ticket', 'assign'");
        // ON CONFLICT DO NOTHING, matching V64/V68/V70.
        assertThat(sql).contains("on conflict (module, action) do nothing");
    }

    @Test
    void thePermissionUuidDoesNotCollideWithV70sThreeRows() throws Exception {
        Set<String> uuids = java.util.regex.Pattern
                .compile("'([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})', 'ticket'")
                .matcher(v71())
                .results()
                .map(m -> m.group(1))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(uuids).containsExactly("aaaa1111-aaaa-aaaa-aaaa-000000000004");

        // ...and that id must not be one of V70's, or V70 would fail to apply.
        String v70 = Files.readString(Path.of(
                "src/main/resources/db/migration/V70__create_tickets.sql")).toLowerCase(Locale.ROOT);
        assertThat(v70).doesNotContain("aaaa1111-aaaa-aaaa-aaaa-000000000004");
    }

    @Test
    void noOtherPermissionIsAdded() {
        // V71 seeds exactly one permission. Anything else would be unrelated scope.
        Set<String> modules = java.util.regex.Pattern
                .compile("\\('[0-9a-f-]{36}', '(\\w+)', '(\\w+)'")
                .matcher(v71())
                .results()
                .map(m -> m.group(1) + ":" + m.group(2))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(modules).containsExactly("ticket:assign");
    }

    // --- backfill grants ---

    @Test
    void superadminAndAdminReceiveAssignAtAll() {
        assertThat(grantBlock("r.id, p.id, 'all'")).isNotNull();
        assertThat(v71()).containsPattern("select\\s+r\\.id,\\s*p\\.id,\\s*'all'");
        assertThat(v71()).contains("r.name = 'superadmin'");
        assertThat(v71()).contains("r.name = 'admin'");
    }

    @Test
    void managerReceivesAssignAtTeam() {
        // Matches V68's entitlement:assign decision exactly.
        assertThat(v71()).containsPattern("select\\s+r\\.id,\\s*p\\.id,\\s*'team'");
        assertThat(v71()).contains("r.name = 'manager'");
    }

    @Test
    void everyBackfillGrantIsConflictGuarded() {
        List<String> grants = java.util.Arrays.stream(v71().split(";"))
                .filter(s -> s.contains("insert into role_permissions"))
                .toList();

        // Six blocks: three backfills (SUPERADMIN, ADMIN, MANAGER) plus the three inside
        // create_tenant_default_roles().
        assertThat(grants).hasSize(6);
        assertThat(grants).allMatch(s -> s.contains("on conflict (role_id, permission_id)"));
    }

    @Test
    void theBackfillNeverTouchesEmployees() {
        // The strongest statement available without a database: EMPLOYEE is not a
        // backfill target at all.
        for (String grant : java.util.Arrays.stream(v71().split(";"))
                .filter(s -> s.contains("insert into role_permissions"))
                .filter(s -> s.contains("from roles")) // backfills only
                .toList()) {
            assertThat(grant)
                    .as("EMPLOYEE must not appear in a backfill grant")
                    .doesNotContain("'employee'");
        }
    }

    @Test
    void theBackfillIsTenantQualified() {
        // A tenant-blind grant would hand assignment to platform-level roles that
        // have no tenant context.
        for (String grant : java.util.Arrays.stream(v71().split(";"))
                .filter(s -> s.contains("insert into role_permissions"))
                .filter(s -> s.contains("from roles")) // backfills only, not the trigger
                .toList()) {
            assertThat(grant).contains("r.level");
            assertThat(grant).contains("r.tenant_id");
        }
    }

    // --- future provisioning trigger ---

    @Test
    void theTriggerFunctionIsReDeclared() {
        // V70's function already granted MANAGER ticket with 'assign' in its action
        // list, but the whole function is restated here so the EMPLOYEE exclusion is
        // explicit and the policy lives in one current place.
        assertThat(v71()).contains("create or replace function create_tenant_default_roles()");
        assertThat(v71()).contains("language plpgsql");
    }

    @Test
    void theTriggerDoesNotRecreateTheTenantInsertTrigger() {
        // The trigger from V70 still fires create_tenant_default_roles(); re-creating
        // it here would duplicate the trigger error handling.
        assertThat(v71()).doesNotContain("create trigger");
        assertThat(v71()).doesNotContain("drop trigger");
    }

    @Test
    void newTenantsGrantAssignToAdminAndManagerButNotEmployee() {
        String function = v71().substring(v71().indexOf("create or replace function"));

        var adminActions = actionsFor(function, "admin_role_id");
        var managerActions = actionsFor(function, "manager_role_id");
        var employeeActions = actionsFor(function, "employee_role_id");

        // ADMIN selects by module only, so every ticket action including assign.
        assertThat(adminActions).isNull();
        assertThat(moduleListFor(function, "admin_role_id")).contains("ticket");

        assertThat(managerActions).contains("assign");
        assertThat(moduleListFor(function, "manager_role_id")).contains("ticket");

        assertThat(employeeActions).doesNotContain("assign");
        assertThat(employeeActions).containsExactly("read", "write");
    }

    /**
     * The latest migration that (re)declares create_tenant_default_roles, lowercased.
     *
     * <p>V74 re-declares it to include the feedback module and is now the live
     * tenant-provisioning function; V71's declaration is superseded. These two
     * tests pin the SQL trigger to DefaultRoleConfig, and that invariant only holds
     * against the function the database actually runs, so they follow the newest
     * copy rather than a superseded one.
     */
    private static String latestTriggerFunction() {
        Path dir = Path.of("src/main/resources/db/migration");
        try {
            return Files.list(dir)
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted((a, b) -> b.getFileName().toString()
                            .compareTo(a.getFileName().toString()))
                    .map(p -> {
                        try {
                            return Files.readString(p).toLowerCase(Locale.ROOT);
                        } catch (java.io.IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .filter(s -> s.contains("create or replace function create_tenant_default_roles"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no migration declares create_tenant_default_roles"));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void theTriggerActionSetsMatchTheJavaConfig() {
        String function = latestTriggerFunction().substring(
                latestTriggerFunction().indexOf("create or replace function"));

        // This is the assertion that prevents the trigger from drifting from Java: if
        // either side changes alone, tenants provisioned in the future disagree with
        // the Java fallback.
        assertThat(actionsFor(function, "manager_role_id"))
                .containsExactlyInAnyOrderElementsOf(DefaultRoleConfig.MANAGER_ACTIONS);
        assertThat(actionsFor(function, "employee_role_id"))
                .containsExactlyInAnyOrderElementsOf(DefaultRoleConfig.EMPLOYEE_ACTIONS);
    }

    @Test
    void theTriggerModuleSetsMatchTheJavaConfig() {
        String function = latestTriggerFunction().substring(
                latestTriggerFunction().indexOf("create or replace function"));

        assertThat(moduleListFor(function, "admin_role_id"))
                .containsExactlyInAnyOrderElementsOf(DefaultRoleConfig.ADMIN_MODULES);
        assertThat(moduleListFor(function, "manager_role_id"))
                .containsExactlyInAnyOrderElementsOf(DefaultRoleConfig.MANAGER_MODULES);
        assertThat(moduleListFor(function, "employee_role_id"))
                .containsExactlyInAnyOrderElementsOf(DefaultRoleConfig.EMPLOYEE_MODULES);
    }

    // --- P1 preserved ---

    @Test
    void theP1PermissionRowsAreUntouched() {
        // V71 adds one permission; it must not remove or rewrite P1's three.
        String sql = v71();

        assertThat(sql).doesNotContain("update role_permissions");
        assertThat(sql).doesNotContain("delete from");
        assertThat(sql).doesNotContain("drop table");
        assertThat(sql).doesNotContain("alter table");
    }

    @Test
    void v70IsNotModifiedByP2() {
        // Structural guarantee: V71 references V70 but never edits it, and does not
        // redefine the table.
        assertThat(v71()).doesNotContain("create table");
        assertThat(v71()).doesNotContain("tickets (");
    }

    @Test
    void noOutOfScopeTablesAppear() {
        String sql = v71();

        assertThat(sql).doesNotContain("assignment_history");
        assertThat(sql).doesNotContain("ticket_sla");
        assertThat(sql).doesNotContain("ticket_queue");
        assertThat(sql).doesNotContain("ticket_team");
        assertThat(sql).doesNotContain("ticket_message");
    }

    // --- helpers ---

    /** The action list guarding a role's grant, or null when it is filtered by module only. */
    private static List<String> actionsFor(String function, String roleId) {
        String block = grantBlockIn(function, roleId);
        var matcher = Pattern.compile("and p\\.action in \\(([^)]*)\\)", Pattern.DOTALL).matcher(block);
        return matcher.find() ? splitter(matcher.group(1)) : null;
    }

    private static List<String> moduleListFor(String function, String roleId) {
        String block = grantBlockIn(function, roleId);
        var matcher = Pattern.compile("where p\\.module in \\(([^)]*)\\)", Pattern.DOTALL).matcher(block);
        if (!matcher.find()) {
            throw new AssertionError("no module list for " + roleId);
        }
        return splitter(matcher.group(1));
    }

    private static String grantBlockIn(String function, String roleId) {
        int index = function.indexOf("select " + roleId + ", p.id");
        if (index < 0) {
            throw new AssertionError("no trigger grant for " + roleId);
        }
        return function.substring(index, function.indexOf("on conflict", index));
    }

    private static List<String> splitter(String values) {
        return java.util.Arrays.stream(values.split(","))
                .map(String::trim)
                .map(v -> v.replace("'", ""))
                .filter(v -> !v.isEmpty())
                .toList();
    }
}
