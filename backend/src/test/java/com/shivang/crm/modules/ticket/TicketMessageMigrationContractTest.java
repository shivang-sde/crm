package com.shivang.crm.modules.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * L3.1-P3: the V72 migration contract.
 *
 * <p><b>Scope of this guarantee.</b> There is no H2 or Testcontainers harness in this
 * repository, so nothing here executes SQL. What these assertions prove is that the
 * migration <em>text</em> specifies the required table, columns, foreign keys and
 * indexes. They do not prove PostgreSQL accepts it, that the migration applies
 * cleanly, or that it rolls back. Treat that distinction as real: "SQL contract
 * verified" is the honest claim here, not "migration execution verified".
 *
 * <p>What structural testing does catch reliably is the drift this codebase has
 * already been bitten by — a column or index added in one place and forgotten in
 * another, or a P4-phase concept leaking into a phase that excluded it.
 */
class TicketMessageMigrationContractTest {

    private static final Path V72 =
            Path.of("src/main/resources/db/migration/V72__create_ticket_messages.sql");

    private static String sql() {
        return stripComments(raw());
    }

    private static String raw() {
        try {
            return Files.readString(V72).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new AssertionError("could not read " + V72, e);
        }
    }

    /**
     * Removes {@code --} comments.
     *
     * <p>Two jobs. It stops a column assertion from matching prose, and it stops the
     * reversibility note ("DROP TABLE ticket_messages") from satisfying the "this
     * migration drops nothing" check. A migration that documents its own rollback
     * would otherwise look like one that performs it.
     */
    private static String stripComments(String sql) {
        return java.util.Arrays.stream(sql.split("\n"))
                .map(line -> {
                    int comment = line.indexOf("--");
                    return comment >= 0 ? line.substring(0, comment) : line;
                })
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /** The CREATE TABLE body, so a column assertion cannot match a comment. */
    private static String tableBody() {
        String sql = sql();
        int start = sql.indexOf("create table if not exists ticket_messages");
        assertThat(start).as("V72 must create ticket_messages").isNotNegative();
        return sql.substring(start, sql.indexOf(");", start));
    }

    // ================= table =================

    @Test
    void theTicketMessagesTableExistsAndIsIdempotent() {
        assertThat(sql()).contains("create table if not exists ticket_messages");
    }

    @Test
    void itCarriesEveryRequiredColumn() {
        assertThat(tableBody())
                .contains("id uuid primary key")
                .contains("tenant_id uuid not null")
                .contains("ticket_id uuid not null")
                .contains("author_user_id uuid not null")
                .contains("body text not null")
                .contains("visibility varchar(30) not null")
                .contains("created_at timestamp not null")
                .contains("updated_at timestamp not null")
                .contains("deleted boolean not null default false")
                .contains("deleted_at timestamp")
                .contains("deleted_by uuid");
    }

    @Test
    void thePrimaryKeyUsesTheRepositoryUuidConvention() {
        // gen_random_uuid(), as V70's tickets and every other table in this schema.
        assertThat(tableBody()).contains("id uuid primary key default gen_random_uuid()");
    }

    @Test
    void timestampsUseNowAsTheDatabaseDefault() {
        // Matches V70, so BaseEntity's @PrePersist and a direct SQL insert agree.
        assertThat(tableBody()).contains("created_at timestamp not null default now()");
        assertThat(tableBody()).contains("updated_at timestamp not null default now()");
    }

    @Test
    void idIsThePrimaryKeyAndNothingElseIs() {
        // Exactly one PK declaration. A stray second NOT NULL on id would be a
        // duplicate column.
        assertThat(java.util.Arrays.stream(tableBody().split("\n"))
                .map(String::trim)
                .filter(l -> l.startsWith("id "))
                .toList())
                .hasSize(1);
    }

    // ================= no duplicated identity =================

    @Test
    void theTableDoesNotDuplicateCustomerOrOwnerColumns() {
        // The parent Ticket is the single source of customer identity, ownership and
        // tenant relationship. A copy here could contradict it.
        assertThat(tableBody())
                .doesNotContain("account_id")
                .doesNotContain("contact_id")
                .doesNotContain("owner_id")
                .doesNotContain("owner_user_id uuid not null");
    }

    @Test
    void thereIsNoCreatedByColumn() {
        // author_user_id is the domain fact; created_by would be a second, redundant
        // spelling of the same idea.
        assertThat(tableBody()).doesNotContain("created_by");
    }

    // ================= foreign keys =================

    @Test
    void ticketIdReferencesTickets() {
        assertThat(tableBody())
                .contains("fk_ticket_messages_ticket_id")
                .contains("foreign key (ticket_id) references tickets(id)");
    }

    @Test
    void authorUserIdReferencesUsers() {
        assertThat(tableBody())
                .contains("fk_ticket_messages_author_user_id")
                .contains("foreign key (author_user_id) references users(id)");
    }

    @Test
    void tenantAndDeletedByReferenceTheExistingTables() {
        assertThat(tableBody())
                .contains("foreign key (tenant_id) references tenants(id)")
                .contains("foreign key (deleted_by) references users(id)");
    }

    @Test
    void thereIsNoOnDeleteCascadeFromTickets() {
        // Messages survive a closed ticket, and TicketService never deletes one. A
        // cascade would silently discard a conversation if a ticket were ever removed.
        assertThat(tableBody()).doesNotContain("on delete cascade");
    }

    @Test
    void theForeignKeysAreTenantBlindLikeEveryOtherCustomerDomainFk() {
        // Documented deliberately, matching deals.account_id and tickets.account_id:
        // the FK cannot enforce tenancy, which is why the service resolves the parent
        // through a tenant-scoped finder.
        String table = tableBody();
        assertThat(table).doesNotContain("references tickets(tenant_id");
        // Read from the unstripped text: the rationale lives in a comment on purpose,
        // because it is the part a future reader needs.
        assertThat(raw()).contains("tenant-blind");
    }

    // ================= indexes =================

    @Test
    void theConversationIndexMatchesTheQueryP3Issues() {
        // The one list query is a ticket's live conversation, newest first.
        assertThat(sql())
                .contains("create index if not exists idx_ticket_messages_tenant_ticket")
                .contains("on ticket_messages(tenant_id, ticket_id, created_at)");
    }

    @Test
    void theAuthorIndexExistsForAuthorScopedLookups() {
        assertThat(sql())
                .contains("create index if not exists idx_ticket_messages_tenant_author")
                .contains("on ticket_messages(tenant_id, author_user_id)");
    }

    @Test
    void everyIndexIsCreatedIdempotently() {
        List<String> creates = java.util.Arrays.stream(sql().split("\n"))
                .map(String::trim)
                .filter(l -> l.startsWith("create index"))
                .toList();

        assertThat(creates).isNotEmpty().allMatch(l -> l.contains("if not exists"));
    }

    @Test
    void thereAreExactlyTwoIndexes() {
        // Deliberate restraint: deleted is a near-universal false and adding it to a
        // four-column index buys almost nothing.
        long count = java.util.Arrays.stream(sql().split("\n"))
                .map(String::trim)
                .filter(l -> l.startsWith("create index"))
                .count();

        assertThat(count).isEqualTo(2);
    }

    @Test
    void theMigrationDoesNotSeedPermissions() {
        // P3 adds no RBAC surface: messages inherit the Ticket permissions, and a
        // ticket_message module would have to be kept in step with the Ticket ones.
        String sql = sql();
        assertThat(sql).doesNotContain("insert into permissions");
        assertThat(sql).doesNotContain("insert into role_permissions");
        assertThat(sql).doesNotContain("create or replace function");
    }

    // ================= P4+ concepts absent =================

    @Test
    void thereAreNoSlaOrEscalationColumns() {
        // response_due_at, resolution_due_at and first_response_at are explicitly out
        // of scope; they would belong on the Ticket anyway, not on a message.
        assertThat(tableBody())
                .doesNotContain("response_due_at")
                .doesNotContain("resolution_due_at")
                .doesNotContain("first_response_at")
                .doesNotContain("sla")
                .doesNotContain("escalat")
                .doesNotContain("breach");
    }

    @Test
    void thereIsNoPriorityCategoryOrChannelColumn() {
        assertThat(tableBody())
                .doesNotContain("priority")
                .doesNotContain("category")
                .doesNotContain("channel")
                .doesNotContain("source");
    }

    @Test
    void thereIsNoDeliveryOrNotificationColumn() {
        // CUSTOMER_VISIBLE is intent only. A delivered_at column would imply a
        // delivery pipeline P3 does not have.
        assertThat(tableBody())
                .doesNotContain("delivered")
                .doesNotContain("sent")
                .doesNotContain("notified")
                .doesNotContain("email")
                .doesNotContain("channel_id")
                .doesNotContain("external_id");
    }

    @Test
    void thereIsNoAttachmentColumn() {
        assertThat(tableBody())
                .doesNotContain("attachment")
                .doesNotContain("file")
                .doesNotContain("media");
    }

    @Test
    void noHistoryOrEventTableIsCreated() {
        // Message audit lives in the existing activities table and canonical outbox.
        // A second audit store would be scope creep.
        String sql = sql();

        assertThat(sql).doesNotContain("create table if not exists ticket_message_history");
        assertThat(sql).doesNotContain("create table if not exists ticket_message_events");
        assertThat(sql).doesNotContain("create table if not exists ticket_message_attachments");
        assertThat(sql).doesNotContain("create table if not exists ticket_sla");
        assertThat(sql).doesNotContain("create table if not exists ticket_escalations");
        assertThat(sql).doesNotContain("create table if not exists ticket_message_outbox");
    }

    @Test
    void exactlyOneTableIsCreated() {
        long count = java.util.Arrays.stream(sql().split("\n"))
                .map(String::trim)
                .filter(l -> l.startsWith("create table"))
                .count();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void thereIsNoCheckConstraintOnVisibility() {
        // @Enumerated(STRING) plus the Java enum is the single source of truth; a CHECK
        // would need updating in lockstep. Same reasoning as V70's status column.
        assertThat(tableBody()).doesNotContain("check (visibility");
        assertThat(tableBody()).doesNotContain("check (visibility in");
    }

    @Test
    void thereIsNoCheckConstraintOnSoftDeleteState() {
        // The repository does not constrain deleted either; the application owns it.
        assertThat(tableBody()).doesNotContain("check (deleted");
    }

    // ================= P1/P2 migrations untouched =================

    @Test
    void itDoesNotAlterTicketsOrUsers() {
        String sql = sql();

        assertThat(sql).doesNotContain("alter table tickets");
        assertThat(sql).doesNotContain("alter table users");
        assertThat(sql).doesNotContain("alter table role_permissions");
    }

    @Test
    void v70AndV71AreNotReferencedForModification() {
        // V72 only creates a new table. Rewriting an applied migration is never safe.
        String sql = sql();

        assertThat(sql).doesNotContain("drop table");
        assertThat(sql).doesNotContain("drop column");
        assertThat(sql).doesNotContain("drop index");
        assertThat(sql).doesNotContain("update role_permissions");
    }

    @Test
    void theFileIsNamedForItsPositionInTheSequence() {
        // V70 tickets, V71 ticket:assign, V72 messages. A gap or a collision here
        // would make Flyway's ordering ambiguous.
        assertThat(V72.getFileName().toString()).isEqualTo("V72__create_ticket_messages.sql");
        assertThat(java.nio.file.Files.exists(
                Path.of("src/main/resources/db/migration/V70__create_tickets.sql"))).isTrue();
        assertThat(java.nio.file.Files.exists(
                Path.of("src/main/resources/db/migration/V71__seed_ticket_assign_permission.sql"))).isTrue();
    }
}
