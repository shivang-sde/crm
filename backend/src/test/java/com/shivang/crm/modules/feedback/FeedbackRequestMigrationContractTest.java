package com.shivang.crm.modules.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * L3.5: the request store must keep tokens hashed, status derived, and the
 * public lookup path unique. SQL-text guard, following the V74 contract style.
 */
class FeedbackRequestMigrationContractTest {

    private static final Path V75 =
            Path.of("src/main/resources/db/migration/V75__create_feedback_requests.sql");

    private static String v75() {
        try {
            return Files.readString(V75, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void theMigrationExists() {
        assertThat(V75).exists();
    }

    @Test
    void theRequestIsTicketScopedWithACustomerSnapshot() {
        assertColumnIs("ticket_id", "uuid not null");
        assertColumnIs("account_id", "uuid not null");
        assertColumnIs("contact_id", "uuid,");
        assertColumnIs("owner_user_id", "uuid,");
        assertColumnIs("created_by", "uuid not null");
    }

    @Test
    void onlyTheTokenHashIsStored() {
        assertColumnIs("token_hash", "varchar\\(64\\) not null");
        assertThat(hasColumnDefinition("token")).isFalse();
        assertThat(v75()).contains("uq_feedback_requests_token_hash")
                .contains("on feedback_requests (token_hash)");
    }

    @Test
    void timestampsAreTheOnlyLifecycleState() {
        assertColumnIs("expires_at", "timestamp not null");
        assertColumnIs("submitted_at", "timestamp,");
        assertColumnIs("revoked_at", "timestamp,");
        assertThat(hasColumnDefinition("status")).isFalse();
    }

    @Test
    void tenantAndTicketReadsAreIndexed() {
        assertThat(v75()).contains("idx_feedback_requests_tenant_ticket_created")
                .contains("on feedback_requests (tenant_id, ticket_id, created_at desc)");
    }

    @Test
    void thePendingCheckIndexExcludesTerminalRows() {
        assertThat(v75()).contains("idx_feedback_requests_pending")
                .contains("where submitted_at is null")
                .contains("and revoked_at is null")
                .contains("and deleted = false");
    }

    @Test
    void foreignKeysCoverTheSnapshotsAndAuditActors() {
        assertThat(v75()).contains("references tickets(id)")
                .contains("references accounts(id)")
                .contains("references contacts(id)")
                .contains("references feedback(id)")
                .contains("references users(id)");
    }

    @Test
    void enumAndScoreRulesStayInTheDomain() {
        assertColumnIs("type", "varchar\\(30\\) not null");
        assertThat(v75()).doesNotContain("check (");
    }

    private void assertColumnIs(String column, String definition) {
        Pattern pattern = Pattern.compile(
                "^\\s*" + column + "\\s+" + definition, Pattern.MULTILINE);
        assertThat(v75()).as(column + " definition").containsPattern(pattern);
    }

    private boolean hasColumnDefinition(String column) {
        return Pattern.compile("^\\s*" + column + "\\s+", Pattern.MULTILINE)
                .matcher(v75()).find();
    }
}
