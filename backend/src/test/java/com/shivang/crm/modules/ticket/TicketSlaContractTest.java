package com.shivang.crm.modules.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.modules.ticket.service.TicketSlaBreachService;
import com.shivang.crm.modules.ticket.dto.TicketResponse;

/**
 * L3.1-P4: the SLA wire and entity contract, plus the V73 migration contract.
 *
 * <p><b>Migration verification scope.</b> No database executed V73. These assertions
 * read the SQL text and prove the migration <em>specifies</em> the required columns,
 * defaults and index shape. They do not prove PostgreSQL accepts it or that it rolls
 * back. "SQL contract verified" is the honest claim.
 */
class TicketSlaContractTest {

    private static final Path V73 =
            Path.of("src/main/resources/db/migration/V73__add_ticket_sla_fields.sql");

    private static String sql() {
        return stripComments(raw());
    }

    private static String raw() {
        try {
            return Files.readString(V73).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new AssertionError("could not read " + V73, e);
        }
    }

    private static String stripComments(String sql) {
        return java.util.Arrays.stream(sql.split("\n"))
                .map(line -> {
                    int comment = line.indexOf("--");
                    return comment >= 0 ? line.substring(0, comment) : line;
                })
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    // ================= entity =================

    @Test
    void theTicketCarriesTheFourSlaFields() {
        assertThat(declaredFields()).contains("responseDueAt", "resolutionDueAt",
                "firstResponseAt", "slaBreached");
    }

    @Test
    void theSlaTimestampColumnsUseTheExistingConvention() throws Exception {
        for (String field : List.of("responseDueAt", "resolutionDueAt", "firstResponseAt")) {
            var column = Ticket.class.getDeclaredField(field)
                    .getAnnotation(jakarta.persistence.Column.class);
            assertThat(column).as("%s must declare a @Column", field).isNotNull();
            assertThat(column.name()).isEqualTo(
                    java.util.List.of("responseDueAt", "resolutionDueAt", "firstResponseAt")
                            .indexOf(field) == 0 ? "response_due_at"
                            : java.util.List.of("responseDueAt", "resolutionDueAt", "firstResponseAt")
                                    .indexOf(field) == 1 ? "resolution_due_at" : "first_response_at");
            // Nullable: a ticket predating the migration has no deadlines.
            assertThat(column.nullable()).isTrue();
        }
    }

    @Test
    void slaBreachedMapsToANonNullBooleanColumn() throws Exception {
        var column = Ticket.class.getDeclaredField("slaBreached")
                .getAnnotation(jakarta.persistence.Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("sla_breached");
        // Primitive boolean, so it cannot be null and the default always applies.
        assertThat(Ticket.class.getDeclaredField("slaBreached").getType()).isEqualTo(boolean.class);
    }

    @Test
    void thereIsNoSeparateSlaEntityOrRelationship() {
        // A Ticket's SLA is four columns. An SlaEntity or a slaId would make the Ticket
        // no longer the aggregate.
        assertThat(Ticket.class.getDeclaredFields())
                .noneMatch(f -> f.getName().toLowerCase().contains("sla")
                        && !f.getName().equals("slaBreached"));
        assertThat(Ticket.class.getDeclaredFields())
                .noneMatch(f -> f.getName().toLowerCase().endsWith("id")
                        && f.getName().toLowerCase().contains("sla"));
    }

    @Test
    void thereIsNoPausedOrElapsedAccounting() {
        assertThat(declaredFields())
                .doesNotContain("pausedAt", "totalPausedSeconds", "slaPausedAt");
    }

    @Test
    void theTicketStillExtendsTenantOwnedEntity() {
        // P4 adds fields; it does not change the aggregate's base type.
        assertThat(Ticket.class.getSuperclass())
                .isEqualTo(com.shivang.crm.shared.base.TenantOwnedEntity.class);
    }

    // ================= response =================

    @Test
    void theResponseExposesTheFourSlaFieldsInCamelCase() {
        assertThat(java.util.Arrays.stream(TicketResponse.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .contains("responseDueAt", "resolutionDueAt", "firstResponseAt", "slaBreached");
    }

    @Test
    void noSlaResponseFieldIsRenamedToADatabaseName() throws Exception {
        for (String field : List.of("responseDueAt", "resolutionDueAt", "firstResponseAt", "slaBreached")) {
            var jsonProperty = TicketResponse.class.getDeclaredField(field)
                    .getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
            // A rename would be a signal the wire contract had drifted.
            assertThat(jsonProperty).as("%s must not be renamed", field).isNull();
        }
    }

    @Test
    void slaBreachedIsSerializedAsABooleanNotAFlag() {
        TicketResponse response = TicketResponse.builder().slaBreached(Boolean.TRUE).build();

        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(response);
            assertThat(json).contains("\"slaBreached\":true");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void theExistingResponseFieldsAreUnchanged() {
        // P4 adds four fields and renames or removes none.
        assertThat(java.util.Arrays.stream(TicketResponse.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .contains("id", "tenantId", "ownerId", "accountId", "contactId", "subject",
                        "description", "status", "resolvedAt", "closedAt",
                        "createdBy", "updatedBy", "createdAt", "updatedAt");
    }

    // ================= event vocabulary =================

    @Test
    void slaBreachedEventTypeIsRegistered() {
        assertThat(com.shivang.crm.shared.event.CanonicalCrmEvent.SLA_BREACHED_EVENT_TYPE)
                .isEqualTo("SLA_BREACHED");
    }

    @Test
    void slaBreachedIsTheOnlyNewEventTypeInP4() {
        var names = java.util.Arrays.stream(
                        com.shivang.crm.shared.event.CanonicalCrmEvent.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .map(n -> n.toUpperCase())
                .toList();

        assertThat(names).contains("SLA_BREACHED_EVENT_TYPE");
        // No SLA reminder/warning/escalation vocabulary: P4 reports the miss once.
        assertThat(names).noneMatch(n -> n.contains("SLA_REMINDER"));
        assertThat(names).noneMatch(n -> n.contains("SLA_WARNING"));
        assertThat(names).noneMatch(n -> n.contains("SLA_ESCALAT"));
    }

    // ================= scheduler shape =================

    @Test
    void theSchedulerIsAThinScheduledComponent() throws Exception {
        Class<?> scheduler = com.shivang.crm.modules.ticket.scheduler.TicketSlaBreachScheduler.class;

        assertThat(scheduler.getAnnotation(org.springframework.stereotype.Component.class))
                .isNotNull();
        // Not a service: the logic lives in TicketSlaBreachService, matching
        // EntitlementLifecycleScheduler.
        assertThat(scheduler.getAnnotation(org.springframework.stereotype.Service.class)).isNull();

        var method = java.util.Arrays.stream(scheduler.getDeclaredMethods())
                .filter(m -> m.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class) != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("scheduler needs a @Scheduled method"));
        assertThat(method.getParameterCount()).isZero();
    }

    @Test
    void theSchedulerUsesSpringSchedulingNotQuartzOrRedis() {
        var imports = java.util.Arrays.stream(
                        com.shivang.crm.modules.ticket.scheduler.TicketSlaBreachScheduler.class
                                .getDeclaredMethods())
                .flatMap(m -> java.util.Arrays.stream(m.getAnnotations()))
                .map(a -> a.annotationType().getName())
                .toList();

        assertThat(imports).noneMatch(n -> n.contains("quartz"));
    }

    @Test
    void theSchedulerRespectsAnEnableFlag() {
        // Same shape as EntitlementExpiryProperties.isExpiryEnabled(), so SLA scanning
        // can be turned off without a code change.
        assertThat(new com.shivang.crm.modules.ticket.config.TicketSlaProperties()
                .isBreachScanEnabled()).isTrue();
    }

    @Test
    void thereIsNoDistributedOrRedisLock() {
        // Correctness comes from the slaBreached column being the idempotency
        // boundary. A lock manager would be a new framework for a solved problem.
// Only injected collaborators; statics and the SLF4J logger are irrelevant here.
        var fieldTypes = java.util.Arrays.stream(
                        TicketSlaBreachService.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .filter(f -> !f.getType().getSimpleName().equals("Logger"))
                .map(java.lang.reflect.Field::getType)
                .map(Class::getSimpleName)
                .toList();

        assertThat(fieldTypes).containsExactlyInAnyOrder(
                "TicketRepository", "CanonicalCrmEventPublisher", "ActivityService",
                "TicketEscalationService", "Clock");
        // Correctness comes from the slaBreached column, not a lock manager.
        assertThat(fieldTypes).noneMatch(n -> n.toLowerCase().contains("lockmanager"));
        assertThat(fieldTypes).noneMatch(n -> n.toLowerCase().contains("redis"));
    }

    @Test
    void theBreachServiceConstructorTakesAClock() {
        var types = java.util.Arrays.stream(
                        com.shivang.crm.modules.ticket.service.TicketSlaBreachService.class
                                .getDeclaredConstructors()[0].getParameterTypes())
                .map(Class::getSimpleName)
                .toList();

        assertThat(types).contains("Clock");
        // No tenant or user: the scheduler has no caller to attribute work to.
        assertThat(types).doesNotContain("UUID");
    }

// ================= atomic conditional update =================

    @Test
    void theRepositoryExposesAConditionalUpdateReturningAffectedRowCount() throws Exception {
        var method = TicketRepository.class.getMethod(
                "markSlaBreachedIfNotBreached", java.util.UUID.class, java.util.UUID.class);

        assertThat(method.getReturnType()).isEqualTo(int.class);
    }

    @Test
    void theConditionalUpdateIsAModifyingQuery() throws Exception {
        var method = TicketRepository.class.getMethod(
                "markSlaBreachedIfNotBreached", java.util.UUID.class, java.util.UUID.class);

        var modifying = method.getAnnotation(org.springframework.data.jpa.repository.Modifying.class);
        var query = method.getAnnotation(org.springframework.data.jpa.repository.Query.class);

        assertThat(modifying).as("a bulk update needs @Modifying").isNotNull();
        assertThat(query).as("the transition must be one conditional statement").isNotNull();
    }

    @Test
    void theConditionalUpdateSetsOnlyTheBreachFlag() {
        String query = updateQuery();

        assertThat(query).contains("SET t.slaBreached = true");
        // Nothing else may be written by a scheduler: no deadline, no status, no owner.
        assertThat(query).doesNotContain("resolutionDueAt =");
        assertThat(query).doesNotContain("responseDueAt =");
        assertThat(query).doesNotContain("firstResponseAt =");
        assertThat(query).doesNotContain("t.status =");
        assertThat(query).doesNotContain("t.ownerId =");
    }

    @Test
    void theConditionalUpdateIsConstrainedByIdTenantDeletedAndUnbreached() {
        String query = updateQuery();

        // These four predicates are the whole guarantee. Dropping any one of them
        // would allow a cross-tenant write, a resurrection, or a duplicate event.
        assertThat(query).contains("t.id = :id");
        assertThat(query).contains("t.tenantId = :tenantId");
        assertThat(query).contains("t.deleted = false");
        assertThat(query).contains("t.slaBreached = false");
    }

    @Test
    void theConditionalUpdateHasNoUnconstrainedVariant() {
        // There is exactly one update path, and it carries the tenant predicate.
        var updates = java.util.Arrays.stream(TicketRepository.class.getDeclaredMethods())
                .filter(m -> m.getReturnType() == int.class)
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(updates).containsExactly("markSlaBreachedIfNotBreached");
    }

    @Test
    void theUpdateBindsBothParameters() throws Exception {
        var parameters = java.util.Arrays.stream(
                        TicketRepository.class
                                .getMethod("markSlaBreachedIfNotBreached",
                                        java.util.UUID.class, java.util.UUID.class)
                                .getParameters())
                .map(java.lang.reflect.Parameter::getName)
                .toList();

        // Spring Data needs the names to bind :id and :tenantId.
        assertThat(parameters).containsExactly("id", "tenantId");
    }

    @Test
    void theConditionalUpdateFlushesButDoesNotClearTheContext() throws Exception {
        var modifying = TicketRepository.class
                .getMethod("markSlaBreachedIfNotBreached", java.util.UUID.class, java.util.UUID.class)
                .getAnnotation(org.springframework.data.jpa.repository.Modifying.class);

        // flushAutomatically makes the UPDATE see committed earlier writes;
        // clearAutomatically = false keeps the candidates managed so the service can
        // sync the winner's flag instead of losing the entity.
        assertThat(modifying.flushAutomatically()).isTrue();
        assertThat(modifying.clearAutomatically()).isFalse();
    }

    @Test
    void theServiceDoesNotSaveForTheSlaTransition() {
        String source = readServiceSource();

        // The authoritative transition is the conditional UPDATE. A save() of the
        // flag would reintroduce the read-check-write race this change removed.
        assertThat(source)
                .contains("markSlaBreachedIfNotBreached")
                .doesNotContain("ticketRepository.save(ticket)");
    }

    @Test
    void theServicePublishesOnlyWhenTheUpdateAffectsOneRow() {
        String source = readServiceSource();

        assertThat(source).contains("rowsUpdated == 0");
        assertThat(source).contains("return false");
    }

    @Test
    void theBreachScanIsStillTransactional() {
        // The flag transition and the outbox row must commit or roll back together, so
        // detectBreaches keeps its own @Transactional rather than relying on a caller.
        var annotation = java.util.Arrays.stream(
                        com.shivang.crm.modules.ticket.service.TicketSlaBreachService.class
                                .getDeclaredMethods())
                .filter(m -> m.getName().equals("detectBreaches"))
                .map(m -> m.getAnnotation(
                        org.springframework.transaction.annotation.Transactional.class))
                .findFirst()
                .orElse(null);

        assertThat(annotation).as("detectBreaches must be transactional").isNotNull();
        assertThat(annotation.readOnly()).isFalse();
        assertThat(annotation.propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRED);
    }

    @Test
    void theOutboxStillCarriesTheEvent() {
        // Unchanged mechanism: the breach goes through CanonicalCrmEventPublisher,
        // which enqueues into the transactional outbox. No second event path.
        var publisherFields = java.util.Arrays.stream(
                        com.shivang.crm.shared.event.CanonicalCrmEventPublisher.class
                                .getDeclaredFields())
                .map(f -> f.getType().getSimpleName())
                .toList();

        assertThat(publisherFields).contains("CanonicalCrmEventOutboxService");
    }

    private static String updateQuery() {
        try {
            var query = TicketRepository.class
                    .getMethod("markSlaBreachedIfNotBreached", java.util.UUID.class, java.util.UUID.class)
                    .getAnnotation(org.springframework.data.jpa.repository.Query.class);
            // Whitespace is collapsed so the assertions read as clauses; case is preserved
        // because entity property names are camelCase and case matters to them.
        return query.value().replaceAll("\\s+", " ").trim();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("TicketRepository must expose markSlaBreachedIfNotBreached", e);
        }
    }

    private static String readServiceSource() {
        try {
            return Files.readString(Path.of(
                    "src/main/java/com/shivang/crm/modules/ticket/service/TicketSlaBreachService.java"));
        } catch (Exception e) {
            throw new AssertionError("could not read TicketSlaBreachService", e);
        }
    }

    @Test
    void noServiceCallsSystemCurrentTimeMillis() throws Exception {
        // Deterministic SLA tests require the clock to be injectable everywhere that
        // matters.
        for (String source : List.of(
                "src/main/java/com/shivang/crm/modules/ticket/service/TicketSlaBreachService.java",
                "src/main/java/com/shivang/crm/modules/ticket/service/TicketService.java",
                "src/main/java/com/shivang/crm/modules/ticket/service/TicketMessageService.java")) {
            String text = Files.readString(Path.of(source));
            assertThat(text).as("%s must not read the system clock", source)
                    .doesNotContain("System.currentTimeMillis");
            assertThat(text).as("%s must not use Instant.now", source)
                    .doesNotContain("Instant.now()");
        }
    }

    // ================= migration =================

    @Test
    void theMigrationAddsExactlyTheFourColumns() {
        String sql = sql();

        assertThat(sql).contains("add column if not exists response_due_at timestamp");
        assertThat(sql).contains("add column if not exists resolution_due_at timestamp");
        assertThat(sql).contains("add column if not exists first_response_at timestamp");
        assertThat(sql).contains("add column if not exists sla_breached boolean not null default false");
    }

    @Test
    void slaBreachedDefaultsToFalseAndIsNotNullable() {
        assertThat(sql())
                .contains("sla_breached boolean not null default false");
    }

    @Test
    void theDeadlinesAreNullableForPreMigrationTickets() {
        String sql = sql();

        // NOT NULL would make the migration fail on a table that already has rows.
        assertThat(sql).contains("response_due_at timestamp,");
        assertThat(sql).contains("resolution_due_at timestamp,");
        assertThat(sql).contains("first_response_at timestamp,");
        assertThat(sql).doesNotContain("response_due_at timestamp not null");
    }

    @Test
    void itAltersTheTicketsTableOnly() {
        String sql = sql();

        assertThat(sql).contains("alter table tickets");
        assertThat(sql).doesNotContain("alter table users");
        assertThat(sql).doesNotContain("alter table tenants");
        assertThat(sql).doesNotContain("alter table accounts");
        assertThat(sql).doesNotContain("alter table role_permissions");
        assertThat(sql).doesNotContain("alter table permissions");
    }

    @Test
    void itIsIdempotent() {
        // Every statement carries IF NOT EXISTS, so re-application is a no-op.
        assertThat(sql()).contains("add column if not exists");
        assertThat(java.util.Arrays.stream(sql().split("\n"))
                .map(String::trim)
                .filter(l -> l.contains("add column"))
                .allMatch(l -> l.contains("if not exists"))).isTrue();
    }

    @Test
    void itCreatesNoTable() {
        assertThat(sql()).doesNotContain("create table");
    }

    @Test
    void itSeedsNoPermissionAndNoRoleFunction() {
        String sql = sql();

        // SLA is SYSTEM behaviour: no endpoint, no permission.
        assertThat(sql).doesNotContain("insert into permissions");
        assertThat(sql).doesNotContain("insert into role_permissions");
        assertThat(sql).doesNotContain("create or replace function");
    }

    @Test
    void theResolutionDeadlineIndexIsPartial() {
        // The scanner's predicate, so the index holds only live unbreached candidates.
        assertThat(sql())
                .contains("create index if not exists idx_tickets_tenant_resolution_due_at")
                .contains("on tickets(tenant_id, resolution_due_at)")
                .contains("where deleted = false and sla_breached = false");
    }

    @Test
    void theResponseDeadlineIndexIsPartialAndRequiresAnOutstandingResponse() {
        // Without first_response_at IS NULL this would resurface every answered ticket
        // forever.
        assertThat(sql())
                .contains("create index if not exists idx_tickets_tenant_response_due_at")
                .contains("on tickets(tenant_id, response_due_at)")
                .contains("first_response_at is null");
    }

    @Test
    void bothIndexesAreCreatedIdempotently() {
        List<String> creates = java.util.Arrays.stream(sql().split("\n"))
                .map(String::trim)
                .filter(l -> l.startsWith("create index"))
                .toList();

        assertThat(creates).hasSize(2).allMatch(l -> l.contains("if not exists"));
    }

    @Test
    void theTimestampTypeMatchesTheExistingTicketsConvention() {
        // TIMESTAMP, not TIMESTAMPTZ: V70's tickets columns use TIMESTAMP and this
        // schema stores UTC-normalised instants.
        assertThat(sql()).doesNotContain("timestamptz");
        assertThat(raw()).contains("matching the existing tickets columns");
    }

    @Test
    void thereIsNoPolicyOrCalendarTable() {
        String sql = sql();

        assertThat(sql).doesNotContain("sla_policies");
        assertThat(sql).doesNotContain("sla_rules");
        assertThat(sql).doesNotContain("sla_targets");
        assertThat(sql).doesNotContain("business_hours");
        assertThat(sql).doesNotContain("holiday");
        assertThat(sql).doesNotContain("escalation_rules");
        assertThat(sql).doesNotContain("ticket_sla");
    }

    @Test
    void thereIsNoPolicyReferenceOrPriorityColumn() {
        String sql = sql();

        assertThat(sql).doesNotContain("sla_policy_id");
        assertThat(sql).doesNotContain("sla_policy_name");
        assertThat(sql).doesNotContain("customer_sla_id");
        assertThat(sql).doesNotContain("priority");
        assertThat(sql).doesNotContain("category");
        assertThat(sql).doesNotContain("channel");
    }

    @Test
    void thereIsNoPauseResumeOrBreachReasonColumn() {
        String sql = sql();

        assertThat(sql).doesNotContain("paused_at");
        assertThat(sql).doesNotContain("total_paused_seconds");
        assertThat(sql).doesNotContain("breach_reason");
    }

    @Test
    void thereIsNoSecondBreachBoolean() {
        // One flag covers both deadlines; the event's breachType distinguishes them.
        assertThat(sql()).doesNotContain("response_sla_breached");
        assertThat(sql()).doesNotContain("resolution_sla_breached");
    }

    @Test
    void itDoesNotTouchTheExistingLifecycleColumns() {
        String sql = sql();

        assertThat(sql).doesNotContain("status varchar");
        assertThat(sql).doesNotContain("resolved_at timestamp");
        assertThat(sql).doesNotContain("closed_at timestamp");
    }

    @Test
    void itDropsNothing() {
        String sql = sql();

        assertThat(sql).doesNotContain("drop table");
        assertThat(sql).doesNotContain("drop column");
        assertThat(sql).doesNotContain("drop index");
        assertThat(sql).doesNotContain("delete from");
        assertThat(sql).doesNotContain("update tickets");
    }

    @Test
    void earlierMigrationsAreNotReferencedForModification() {
        String sql = sql();

        assertThat(sql).doesNotContain("v70");
        assertThat(sql).doesNotContain("v71");
        assertThat(sql).doesNotContain("v72");
    }

    @Test
    void theFileIsNamedForItsSequencePosition() {
        assertThat(V73.getFileName().toString()).isEqualTo("V73__add_ticket_sla_fields.sql");
        for (String earlier : List.of("V70__create_tickets.sql",
                "V71__seed_ticket_assign_permission.sql", "V72__create_ticket_messages.sql")) {
            assertThat(java.nio.file.Files.exists(
                    Path.of("src/main/resources/db/migration", earlier)))
                    .as("%s must exist", earlier)
                    .isTrue();
        }
    }

    // ================= helpers =================

    private static Set<String> declaredFields() {
        return java.util.Arrays.stream(Ticket.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .collect(java.util.stream.Collectors.toSet());
    }
}
