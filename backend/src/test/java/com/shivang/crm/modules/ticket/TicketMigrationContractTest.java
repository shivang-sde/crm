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
 * L3.1-P0/P1: the migration and role defaults must match the frozen P0 RBAC
 * contract.
 *
 * <p>There is no database harness in this repository, so these assertions read the
 * migration itself. That is weaker than executing it, and it is worth being honest
 * about that: these tests prove the SQL text says the right thing, not that
 * PostgreSQL accepts it. What they do catch reliably is the failure mode this
 * codebase has already hit — a permission added in Java but not in the trigger, so
 * existing tenants get access and newly provisioned ones silently do not.
 */
class TicketMigrationContractTest {

    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V70__create_tickets.sql");

    private static String migration() {
        try {
            return Files.readString(MIGRATION).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new AssertionError("could not read " + MIGRATION, e);
        }
    }

    // --- table shape ---

    @Test
    void theTicketsTableExistsWithIdempotentCreation() {
        assertThat(migration()).contains("create table if not exists tickets");
    }

    @Test
    void accountIsMandatoryAndContactIsOptional() {
        String sql = migration();
        var table = sql.substring(sql.indexOf("create table if not exists tickets"));

        // The whole point of the NOT NULL is that a Ticket cannot exist without a
        // customer identity; a nullable account_id would defeat it.
        assertThat(table).contains("account_id uuid not null");
        assertThat(table).contains("contact_id uuid,");
        assertThat(table).doesNotContain("contact_id uuid not null");
    }

    @Test
    void theLifecycleColumnsAreNullableTimestamps() {
        String sql = migration();

        assertThat(sql).contains("status varchar(30) not null");
        assertThat(sql).contains("resolved_at timestamp,");
        assertThat(sql).contains("closed_at timestamp,");
    }

    @Test
    void theSoftDeleteColumnsArePresentWithTheRepositoryWideShape() {
        String sql = migration();

        assertThat(sql).contains("deleted boolean not null default false");
        assertThat(sql).contains("deleted_at timestamp,");
        assertThat(sql).contains("deleted_by uuid,");
    }

    @Test
    void theStatusColumnHasNoCheckConstraintPinningTheEnum() {
        // Deliberately absent: a CHECK constraint on status. @Enumerated(STRING) plus
        // the Java enum is the single source of truth, and a CHECK here would need
        // updating in lockstep or a later phase's status would be rejected.
        String sql = migration();

        assertThat(sql).doesNotContain("check (status");
    }

    @Test
    void theTimestampsAndTenancyAreNotNullable() {
        String sql = migration();

        assertThat(sql).contains("tenant_id uuid not null");
        assertThat(sql).contains("subject varchar(255) not null");
        assertThat(sql).contains("created_by uuid not null");
    }

    // --- indexes ---

    @Test
    void indexesCoverEveryQueryPathP1ActuallyHas() {
        String sql = migration();

        // list by status, OWN/TEAM record scope, account filter, and default ordering
        assertThat(sql).contains("idx_tickets_tenant_status");
        assertThat(sql).contains("idx_tickets_tenant_owner");
        assertThat(sql).contains("idx_tickets_tenant_account");
        assertThat(sql).contains("idx_tickets_tenant_created_at");
    }

    @Test
    void everyIndexIsCreatedIdempotently() {
        List<String> creates = java.util.Arrays.stream(migration().split("\n"))
                .filter(l -> l.trim().startsWith("create index"))
                .toList();

        assertThat(creates).isNotEmpty()
                .allMatch(l -> l.contains("if not exists"));
    }

    // --- permissions ---

    @Test
    void exactlyTheThreeP1PermissionsAreSeeded() {
        Set<String> seeded = java.util.regex.Pattern
                .compile("'ticket', '(\\w+)'")
                .matcher(migration())
                .results()
                .map(m -> m.group(1))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(seeded).containsExactlyInAnyOrder("read", "write", "delete");
    }

    @Test
    void assignIsNotSeeded() {
        // ticket:assign is P2. Seeding it now would create a permission with no
        // endpoint, which is dead RBAC surface.
        assertThat(migration()).doesNotContain("'ticket', 'assign'");
    }

    @Test
    void everyPermissionInsertIsConflictGuarded() {
        List<String> inserts = java.util.Arrays.stream(migration().split(";"))
                .filter(s -> s.contains("insert into permissions") || s.contains("insert into role_permissions"))
                .toList();

        assertThat(inserts).isNotEmpty()
                .allMatch(s -> s.contains("on conflict"));
    }

    @Test
    void noEventOrMessageTableIsCreatedYet() {
        // P2 messaging and P3 SLA/escalation are deferred; their tables must not
        // appear speculatively.
        String sql = migration();

        assertThat(sql).doesNotContain("create table if not exists ticket_messages");
        assertThat(sql).doesNotContain("create table if not exists ticket_sla");
        assertThat(sql).doesNotContain("create table if not exists ticket_escalations");
        assertThat(sql).doesNotContain("create table if not exists ticket_status_history");
    }

    // --- role defaults: Java and SQL must agree ---

    @Test
    void ticketIsACustomerDomainModuleForEveryTenantRole() {
        assertThat(DefaultRoleConfig.ADMIN_MODULES).contains("ticket");
        assertThat(DefaultRoleConfig.MANAGER_MODULES).contains("ticket");
        assertThat(DefaultRoleConfig.EMPLOYEE_MODULES).contains("ticket");
    }

    @Test
    void employeeNeverAcquiresTicketDelete() {
        assertThat(DefaultRoleConfig.EMPLOYEE_ACTIONS)
                .containsExactlyInAnyOrder("read", "write");
    }

    @Test
    void managerDoesNotAcquireTicketDeleteEither() {
        // MANAGER_ACTIONS excludes delete, so a manager can work the queue but cannot
        // destroy correspondence.
        assertThat(DefaultRoleConfig.MANAGER_ACTIONS).doesNotContain("delete");
    }

    @Test
    void theJavaAndSqlRolePoliciesAreTheSame() {
        String sql = migration();

        // ADMIN -> ALL, MANAGER -> TEAM, EMPLOYEE -> OWN.
        assertThat(sql).containsPattern("select\\s+admin_role_id,\\s*p\\.id,\\s*'all'");
        assertThat(sql).containsPattern("select\\s+manager_role_id,\\s*p\\.id,\\s*'team'");
        assertThat(sql).containsPattern("select\\s+employee_role_id,\\s*p\\.id,\\s*'own'");

        // The EMPLOYEE action set must match EMPLOYEE_ACTIONS exactly, otherwise the
        // two seeding paths grant different permissions. Anchored on the role id
        // because MANAGER's block matches the same shape.
        Pattern employeeGrant = Pattern.compile(
                "select employee_role_id, p\\.id, '\\w+'\\s*from permissions p\\s*"
                        + "where p\\.module in \\(([^)]*)\\)\\s*and p\\.action in \\(([^)]*)\\)",
                Pattern.DOTALL);
        var matcher = employeeGrant.matcher(sql);
        assertThat(matcher.find()).as("employee grant block must exist in V70").isTrue();
        // Order-insensitive: EMPLOYEE_ACTIONS is a Set, so its iteration order is not
        // guaranteed to match the SQL's literal order.
        assertThat(splitter(matcher.group(2)))
                .containsExactlyInAnyOrderElementsOf(DefaultRoleConfig.EMPLOYEE_ACTIONS);
        // splitter() strips the quotes, so compare the bare value.
        assertThat(splitter(matcher.group(1))).contains("ticket");
    }

    @Test
    void theTriggerFunctionIsRecreatedForFutureTenants() {
        // The failure mode: existing tenants have ticket permissions (backfill above)
        // but a tenant provisioned after this migration never does.
        String sql = migration();

        assertThat(sql).contains("create or replace function create_tenant_default_roles()");
        assertThat(sql).contains("drop trigger if exists tenant_creation_trigger on tenants");
        assertThat(sql).contains("execute function create_tenant_default_roles()");
    }

    @Test
    void theTriggerGrantsTicketToAllThreeNewRoles() {
        String sql = migration();
        var function = sql.substring(sql.indexOf("create or replace function create_tenant_default_roles()"));

        for (String role : List.of("admin_role_id", "manager_role_id", "employee_role_id")) {
            var grant = Pattern.compile("select " + role + ", p\\.id, '\\w+'\\s*from permissions p\\s*"
                    + "where p\\.module in \\((.*?)\\)(;|\\s*and)", Pattern.DOTALL)
                    .matcher(function);
            assertThat(grant.find()).as("grant for %s must exist", role).isTrue();
            assertThat(grant.group(1)).contains("'ticket'");
        }
    }

    private static List<String> splitter(String values) {
        return java.util.Arrays.stream(values.split(","))
                .map(String::trim)
                .map(v -> v.replace("'", ""))
                .filter(v -> !v.isEmpty())
                .toList();
    }
}
