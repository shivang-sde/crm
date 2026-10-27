package com.shivang.crm.modules.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * L3.3-P1: V74 must stay consistent with the two facts the tenant-provisioning test
 * and the RBAC wiring depend on: exactly the two feedback permissions are seeded, and
 * the tenant-default-roles function is re-declared with feedback included.
 *
 * <p>Follows the same SQL-text guard style as TicketMigrationContractTest and
 * EntitlementAssignSeedTest rather than booting a context.
 */
class FeedbackMigrationContractTest {

    private static final Path V74 = Path.of("src/main/resources/db/migration/V74__create_feedback.sql");

    private static String v74() {
        try {
            return Files.readString(V74, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void theMigrationExists() {
        assertThat(V74).exists();
    }

    @Test
    void exactlyTheTwoFeedbackPermissionsAreSeeded() {
        Set<String> actions = Pattern.compile("'feedback', '(\\w+)'")
                .matcher(v74())
                .results()
                .map(m -> m.group(1))
                .collect(Collectors.toSet());

        assertThat(actions).containsExactlyInAnyOrder("read", "write");
        // No delete/assign/review/export for feedback in P1.
        assertThat(v74()).doesNotContain("'feedback', 'delete'");
        assertThat(v74()).doesNotContain("'feedback', 'assign'");
        assertThat(v74()).doesNotContain("'feedback', 'review'");
        assertThat(v74()).doesNotContain("'feedback', 'export'");
    }

    @Test
    void everyPermissionRowIsConflictGuarded() {
        assertThat(v74()).contains("on conflict (module, action) do nothing");
    }

    @Test
    void allFourBackfillGrantsArePresentAndConflictGuarded() {
        String sql = v74();
        // SUPERADMIN, ADMIN, MANAGER, EMPLOYEE backfills.
        assertThat(sql).contains("r.name = 'superadmin'");
        assertThat(sql).contains("r.name = 'admin'");
        assertThat(sql).contains("r.name = 'manager'");
        assertThat(sql).contains("r.name = 'employee'");
        assertThat(sql).contains("p.module = 'feedback'");
        assertThat(sql).contains("on conflict (role_id, permission_id) do nothing");
    }

    @Test
    void theTriggerFunctionIsRedeclared() {
        String sql = v74();
        assertThat(sql).contains("create or replace function create_tenant_default_roles");
        assertThat(sql).contains("'feedback'");
    }

    @Test
    void theAdminTriggerGrantsFeedbackToAllRoles() {
        String sql = v74();
        // feedback appears in the admin module list and is not restricted by action.
        assertThat(sql)
                .as("admin trigger module list must include feedback")
                .contains("'ticket', 'feedback'");
    }

    @Test
    void employeeTenantScopeIsOwnForFeedback() {
        String sql = v74();
        // Employee gets feedback at OWN scope (trigger EXECUTES with access_scope 'own').
        assertThat(sql).contains("'employee', 'tenant'", "new.id", "'own'");
    }

    @Test
    void accountIdIsNotNullAndNoOptionalFksAreIntroduced() {
        // Feedback always belongs to an existing customer.
        assertColumnIsNotNull("account_id");
        // No deal_id / entitlement_id columns: a feedback row does not presume a deal
        // or entitlement. (Comments mention those words, so detect the columns.)
        assertThat(hasColumnDefinition("deal_id")).isFalse();
        assertThat(hasColumnDefinition("entitlement_id")).isFalse();
    }

    private void assertColumnIsNotNull(String column) {
        Pattern line = Pattern.compile("^\\s*" + column + "\\s+uuid\\s+not null",
                Pattern.MULTILINE);
        assertThat(v74()).as(column + " must be UUID NOT NULL").containsPattern(line);
    }

    private boolean hasColumnDefinition(String column) {
        return Pattern.compile("^\\s*" + column + "\\s+", Pattern.MULTILINE)
                .matcher(v74()).find();
    }

    @Test
    void emailIsNotAColumnOnFeedback() {
        // The submitted email is resolved to an Account/Contact and is not persisted,
        // so feedback must have no email column. (Search text, not comment prose.)
        assertThat(hasColumnDefinition("email")).isFalse();
    }

    @Test
    void externalReferenceUsesAPartialUniqueIndex() {
        String sql = v74();
        assertThat(sql).contains("uq_feedback_external_reference");
        assertThat(sql).contains("on feedback (tenant_id, source, external_reference)");
        assertThat(sql).contains("where deleted = false");
        assertThat(sql).contains("source is not null");
        assertThat(sql).contains("btrim(external_reference) <> ''");
    }

    @Test
    void ticketCorrelationIsNullableAndThereIsNoTicketLifecycle() {
        assertThat(v74()).containsPattern(Pattern.compile("^\\s*ticket_id\\s+uuid,", Pattern.MULTILINE));
        // No SLA columns, no status column, no complaint workflow.
        assertThat(hasColumnDefinition("status")).isFalse();
        assertThat(hasColumnDefinition("sla_breached")).isFalse();
        assertThat(hasColumnDefinition("resolution_due_at")).isFalse();
        assertThat(hasColumnDefinition("nps_segment")).isFalse();
        assertThat(hasColumnDefinition("classification")).isFalse();
    }

    @Test
    void enumSurfaceIsStringOnlyAndNoDbCheck() {
        assertThat(v74()).containsPattern(Pattern.compile("type\\s+varchar\\(30\\) not null"));
        assertThat(v74()).containsPattern(Pattern.compile("source\\s+varchar\\(30\\)"));
        // No CHECK enforcing the enum values or a score range; the domain owns those.
        assertThat(v74()).doesNotContain("check (");
    }
}
