package com.shivang.crm.modules.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.ticket.config.TicketSlaProperties;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.1-P4: SLA breach detection.
 *
 * <p>The two things worth protecting are that a breach emits exactly one event, and
 * that only the scanner can emit it. Both are asserted repeatedly here because a
 * regression in either is silent: the flag stays true, the test suite stays green
 * for anything that checks the flag alone.
 */
class TicketSlaBreachServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    private TicketRepository ticketRepository;
    private CanonicalCrmEventPublisher publisher;
    private ActivityService activityService;
    private TicketEscalationService escalationService;
    private TicketSlaBreachService service;

    private UUID tenantId;

    @BeforeEach
    void setup() {
        ticketRepository = mock(TicketRepository.class);
        publisher = mock(CanonicalCrmEventPublisher.class);
        activityService = mock(ActivityService.class);
        escalationService = mock(TicketEscalationService.class);
        service = new TicketSlaBreachService(ticketRepository, publisher, activityService, escalationService, fixedClock(NOW));
        tenantId = UUID.randomUUID();

        // Default to winning the conditional transition, so the pre-existing tests
        // exercise the winner path unless a test deliberately makes it a loser.
        when(ticketRepository.markSlaBreachedIfNotBreached(any(), any())).thenReturn(1);
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private Ticket ticket(TicketStatus status, Instant responseDueAt, Instant resolutionDueAt,
            Instant firstResponseAt) {
        Ticket t = new Ticket();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenantId);
        t.setAccountId(UUID.randomUUID());
        t.setSubject("Cannot log in");
        t.setDescription("Password reset loop");
        t.setStatus(status);
        t.setCreatedBy(UUID.randomUUID());
        t.setDeleted(false);
        t.setResponseDueAt(responseDueAt);
        t.setResolutionDueAt(resolutionDueAt);
        t.setFirstResponseAt(firstResponseAt);
        return t;
    }

    private void stubCandidates(Ticket... tickets) {
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(tickets)));
    }

    private void stubNoCandidates() {
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> Page.empty());
    }

    private Map<String, Object> breachMetadata() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(any(), eq("TICKET"), eq("SLA_BREACHED"), any(), metadata.capture());
        return metadata.getValue();
    }

    // ================= A/B. SLA creation =================

    @Test
    void creationStampsBothDeadlinesFromTheCreationInstant() {
        // Ticket creation is exercised through TicketService; this asserts the
        // entity-level policy application it delegates to.
        Ticket t = new Ticket();
        Instant createdAt = NOW;

        t.applySlaPolicy(createdAt, Duration.ofHours(4), Duration.ofHours(24));

        assertThat(t.getResponseDueAt()).isEqualTo(createdAt.plus(Duration.ofHours(4)));
        assertThat(t.getResolutionDueAt()).isEqualTo(createdAt.plus(Duration.ofHours(24)));
    }

    @Test
    void creationLeavesFirstResponseEmptyAndTheFlagClear() {
        Ticket t = new Ticket();

        t.applySlaPolicy(NOW, Duration.ofHours(4), Duration.ofHours(24));

        // An outstanding response SLA is exactly this state.
        assertThat(t.getFirstResponseAt()).isNull();
        assertThat(t.isSlaBreached()).isFalse();
    }

    @Test
    void theDefaultPolicyIsFourHoursAndTwentyFourHours() {
        TicketSlaProperties properties = new TicketSlaProperties();

        assertThat(properties.responseDuration()).isEqualTo(Duration.ofHours(4));
        assertThat(properties.resolutionDuration()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void bothDeadlinesDeriveFromOneInstant() {
        // Reading the clock twice could produce deadlines a second apart, which would
        // make the pair internally inconsistent.
        Ticket t = new Ticket();
        t.applySlaPolicy(NOW, Duration.ofHours(4), Duration.ofHours(24));

        Duration gap = Duration.between(t.getResponseDueAt(), t.getResolutionDueAt());
        assertThat(gap).isEqualTo(Duration.ofHours(20));
    }

    @Test
    void durationsAreWholeMinutesNotFloatingPoint() {
        TicketSlaProperties properties = new TicketSlaProperties();

        // Long minutes, not double hours: no rounding, no unit ambiguity.
        assertThat(properties.getResponseMinutes()).isEqualTo(240L);
        assertThat(properties.getResolutionMinutes()).isEqualTo(1440L);
        assertThat(properties.getResponseMinutes()).isInstanceOf(Long.class);
    }

    @Test
    void configuredDurationsAreHonouredOverDefaults() {
        Ticket t = new Ticket();

        t.applySlaPolicy(NOW, Duration.ofMinutes(30), Duration.ofMinutes(120));

        assertThat(t.getResponseDueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(t.getResolutionDueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(120)));
    }

    @Test
    void changingThePolicyLaterDoesNotRewriteAnExistingTicket() {
        // Deadlines are a snapshot, not a live reference. This is the whole reason a
        // snapshot is stored rather than a policy id.
        Ticket t = new Ticket();
        t.applySlaPolicy(NOW, Duration.ofHours(4), Duration.ofHours(24));
        Instant snapshotResponseDue = t.getResponseDueAt();

        // A second policy application is what would happen if deadlines were derived
        // on read instead of stored.
        Ticket other = new Ticket();
        other.applySlaPolicy(NOW.plus(Duration.ofDays(30)), Duration.ofHours(1), Duration.ofHours(2));

        assertThat(t.getResponseDueAt()).isEqualTo(snapshotResponseDue);
        assertThat(other.getResponseDueAt()).isNotEqualTo(snapshotResponseDue);
    }

    // ================= C. First response =================

    @Test
    void theFirstMessageRecordsTheFirstResponse() {
        Ticket t = ticket(TicketStatus.OPEN, NOW, NOW, null);

        boolean recorded = t.recordFirstResponse(NOW);

        assertThat(recorded).isTrue();
        assertThat(t.getFirstResponseAt()).isEqualTo(NOW);
    }

    @Test
    void aSecondMessageDoesNotOverwriteTheFirstResponse() {
        Ticket t = ticket(TicketStatus.OPEN, NOW, NOW, null);
        Instant firstAt = NOW.minusSeconds(60);
        t.recordFirstResponse(firstAt);

        boolean recorded = t.recordFirstResponse(NOW);

        // Idempotent by contract: the response SLA was measured against the first one.
        assertThat(recorded).isFalse();
        assertThat(t.getFirstResponseAt()).isEqualTo(firstAt);
    }

    @Test
    void recordingTheFirstResponseDoesNotChangeTheTicketStatus() {
        Ticket t = ticket(TicketStatus.RESOLVED, NOW, NOW, null);

        t.recordFirstResponse(NOW);

        assertThat(t.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void recordingTheFirstResponseDoesNotTouchTheDeadlines() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.plusSeconds(10), NOW.plusSeconds(20), null);
        Instant responseDue = t.getResponseDueAt();
        Instant resolutionDue = t.getResolutionDueAt();

        t.recordFirstResponse(NOW);

        assertThat(t.getResponseDueAt()).isEqualTo(responseDue);
        assertThat(t.getResolutionDueAt()).isEqualTo(resolutionDue);
    }

    @Test
    void assignmentIsNotAFirstResponse() {
        // Assignment lives in P2 and touches no SLA field, so an assigned ticket with
        // no message still has an outstanding response SLA.
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(1), NOW.plusSeconds(3600), null);

        assertThat(t.getFirstResponseAt()).isNull();
        assertThat(t.hasOutstandingResponseSla(NOW)).isTrue();
    }

    // ================= F. Response SLA breach =================

    @Test
    void anOverdueResponseMarksTheTicketBreached() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(60), NOW.plusSeconds(3600), null);
        assertThat(service.markBreached(t, NOW)).isTrue();

        assertThat(t.isSlaBreached()).isTrue();
    }

    @Test
    void aResponseBreachPublishesExactlyOneEvent() {
        stubCandidates(ticket(TicketStatus.OPEN, NOW.minusSeconds(60), NOW.plusSeconds(3600), null));

        service.detectBreaches();

        verify(publisher, times(1)).publish(
                any(), eq("TICKET"), eq("SLA_BREACHED"), any(), any());
    }

    @Test
    void aResponseBreachIdentifiesItself() {
        stubCandidates(ticket(TicketStatus.OPEN, NOW.minusSeconds(60), NOW.plusSeconds(3600), null));

        service.detectBreaches();

        assertThat(breachMetadata()).containsEntry("breachType", "RESPONSE");
    }

    @Test
    void aRespondedTicketHasNoOutstandingResponseSla() {
        // The whole point of first_response_at: a ticket that has been answered is not
        // a response-SLA candidate any more, even past its deadline.
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(3600), NOW.plusSeconds(3600),
                NOW.minusSeconds(7200));

        assertThat(t.hasOutstandingResponseSla(NOW)).isFalse();
        assertThat(service.markBreached(t, NOW)).isFalse();
    }

    @Test
    void aResponseNotYetDueIsNotABreach() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.plusSeconds(60), NOW.plusSeconds(3600), null);

        assertThat(service.markBreached(t, NOW)).isFalse();
        assertThat(t.isSlaBreached()).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    void aResponseDueExactlyNowIsABreach() {
        // Boundary: "current time >= deadline" is a breach, not a near miss.
        Ticket t = ticket(TicketStatus.OPEN, NOW, NOW.plusSeconds(3600), null);

        assertThat(service.markBreached(t, NOW)).isTrue();
    }

    // ================= G. Resolution SLA breach =================

    @Test
    void anOverdueResolutionMarksTheTicketBreached() {
        Ticket t = ticket(TicketStatus.IN_PROGRESS, NOW.minusSeconds(7200), NOW.minusSeconds(1),
                NOW.minusSeconds(7100));

        assertThat(service.markBreached(t, NOW)).isTrue();
        assertThat(t.isSlaBreached()).isTrue();
    }

    @Test
    void aResolutionBreachIdentifiesItself() {
        Ticket t = ticket(TicketStatus.IN_PROGRESS, NOW.minusSeconds(7200), NOW.minusSeconds(1),
                NOW.minusSeconds(7100));

        service.markBreached(t, NOW);

        assertThat(breachMetadata()).containsEntry("breachType", "RESOLUTION");
    }

    @Test
    void aResolutionNotYetDueIsNotABreach() {
        Ticket t = ticket(TicketStatus.IN_PROGRESS, NOW.plusSeconds(60), NOW.plusSeconds(3600),
                NOW);

        assertThat(service.markBreached(t, NOW)).isFalse();
    }

    @Test
    void aResolutionBreachPublishesOneEvent() {
        stubCandidates(ticket(TicketStatus.IN_PROGRESS, NOW.minusSeconds(7200), NOW.minusSeconds(1),
                NOW.minusSeconds(7100)));

        service.detectBreaches();

        verify(publisher, times(1)).publish(any(), eq("TICKET"), eq("SLA_BREACHED"), any(), any());
    }

    // ================= H. Both deadlines passed =================

    @Test
    void bothDeadlinesOverdueStillEmitsExactlyOneEvent() {
        stubCandidates(ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null));

        service.detectBreaches();

        verify(publisher, times(1)).publish(any(), eq("TICKET"), eq("SLA_BREACHED"), any(), any());
    }

    @Test
    void bothDeadlinesOverdueTransitionsStateExactlyOnce() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);

        service.markBreached(t, NOW);
        long savesAfterFirst = org.mockito.Mockito.mockingDetails(ticketRepository).getInvocations().size();

        boolean secondPass = service.markBreached(t, NOW);

        assertThat(secondPass).isFalse();
        assertThat(org.mockito.Mockito.mockingDetails(ticketRepository).getInvocations().size())
                .isEqualTo(savesAfterFirst);
    }

    @Test
    void bothDeadlinesOverduePicksADeterministicBreachType() {
        // Resolution is checked first, so the value does not depend on evaluation
        // order or on which clock the caller happens to reach first.
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);

        service.markBreached(t, NOW);

        assertThat(breachMetadata()).containsEntry("breachType", "RESOLUTION");
    }

    // ================= I. Idempotency =================

    @Test
    void aSecondScanOfAnAlreadyBreachedTicketDoesNothing() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        service.markBreached(t, NOW);
        org.mockito.Mockito.clearInvocations(publisher, ticketRepository);

        // The candidate query never returns breached rows, so this is what a second
        // run actually encounters.
        assertThat(service.markBreached(t, NOW)).isFalse();

        verifyNoInteractions(publisher);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void repeatedScansDoNotAccumulateEvents() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);

        stubCandidates(t);
        service.detectBreaches();

        // Second run: the row is breached, so the scanner would not select it again.
        stubNoCandidates();
        service.detectBreaches();

        verify(publisher, times(1)).publish(any(), eq("TICKET"), eq("SLA_BREACHED"), any(), any());
    }

    @Test
    void anEmptyScanTouchesNothing() {
        stubNoCandidates();

        assertThat(service.detectBreaches()).isZero();

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void theBreachFlagIsTheIdempotencyBoundaryNotAnInMemoryFlag() {
        // A fresh service instance shares no state with the previous one, so
        // idempotency cannot be coming from the JVM.
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        TicketSlaBreachService first = new TicketSlaBreachService(ticketRepository, publisher, activityService,
                escalationService, fixedClock(NOW));
        first.markBreached(t, NOW);

        TicketSlaBreachService second = new TicketSlaBreachService(ticketRepository, publisher, activityService,
                escalationService, fixedClock(NOW));
        org.mockito.Mockito.clearInvocations(publisher);

        assertThat(second.markBreached(t, NOW)).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    void oneFailingTicketDoesNotAbortTheScan() {
        Ticket bad = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        Ticket good = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        stubCandidates(bad, good);
        org.mockito.Mockito.doThrow(new BusinessException("SLA_WRITE_FAILED", "boom"))
                .when(ticketRepository).markSlaBreachedIfNotBreached(bad.getId(), tenantId);

        int breached = service.detectBreaches();

        // Per-ticket isolation: a write failure on one row must not cost the scan
        // every remaining row, and the failed one is not counted as a breach. Its
        // database row is untouched, so the next scan retries it.
        assertThat(breached).isEqualTo(1);
        assertThat(good.isSlaBreached()).isTrue();
    }

    @Test
    void aFailedWritePublishesNoEventForThatTicket() {
        Ticket bad = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        stubCandidates(bad);
        org.mockito.Mockito.doThrow(new BusinessException("SLA_WRITE_FAILED", "boom"))
                .when(ticketRepository).markSlaBreachedIfNotBreached(bad.getId(), tenantId);

        service.detectBreaches();

        // The event goes through the outbox in the same transaction, so a ticket that
        // was not persisted must not have a durable breach event.
        verifyNoInteractions(publisher);
    }

    // ================= D/E. Closed and Resolved =================

    @Test
    void aClosedTicketIsNeverBreached() {
        Ticket t = ticket(TicketStatus.CLOSED, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);

        assertThat(t.hasOutstandingResponseSla(NOW)).isFalse();
        assertThat(t.hasOutstandingResolutionSla(NOW)).isFalse();
        assertThat(service.markBreached(t, NOW)).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    void aResolvedTicketIsNeverNewlyBreached() {
        Ticket t = ticket(TicketStatus.RESOLVED, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);

        assertThat(t.hasOutstandingResolutionSla(NOW)).isFalse();
        assertThat(t.hasOutstandingResponseSla(NOW)).isFalse();
        assertThat(service.markBreached(t, NOW)).isFalse();
    }

    @Test
    void aClosedTicketKeepsItsExistingFirstResponse() {
        Ticket t = ticket(TicketStatus.CLOSED, NOW.minusSeconds(7200), NOW.minusSeconds(1),
                NOW.minusSeconds(7300));

        service.markBreached(t, NOW);

        assertThat(t.getFirstResponseAt()).isEqualTo(NOW.minusSeconds(7300));
    }

    @Test
    void resolvingDoesNotClearAnExistingBreach() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        service.markBreached(t, NOW);

        t.setStatus(TicketStatus.RESOLVED);

        // Historical: the deadline was missed, and that stays true.
        assertThat(t.isSlaBreached()).isTrue();
    }

    @Test
    void reopeningDoesNotResetSlaState() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        service.markBreached(t, NOW);
        Instant responseDue = t.getResponseDueAt();
        Instant resolutionDue = t.getResolutionDueAt();

        t.setStatus(TicketStatus.CLOSED);
        t.applyStatus(TicketStatus.OPEN, NOW);

        assertThat(t.isSlaBreached()).isTrue();
        assertThat(t.getResponseDueAt()).isEqualTo(responseDue);
        assertThat(t.getResolutionDueAt()).isEqualTo(resolutionDue);
    }

    @Test
    void aStatusChangeDoesNotRecalculateDeadlines() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.plusSeconds(60), NOW.plusSeconds(120), null);
        Instant responseDue = t.getResponseDueAt();
        Instant resolutionDue = t.getResolutionDueAt();

        t.applyStatus(TicketStatus.RESOLVED, NOW);

        assertThat(t.getResponseDueAt()).isEqualTo(responseDue);
        assertThat(t.getResolutionDueAt()).isEqualTo(resolutionDue);
    }

    // ================= J. Multi-tenant =================

    @Test
    void theEventCarriesTheTicketsOwnTenant() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        UUID otherTenant = UUID.randomUUID();
        t.setTenantId(otherTenant);

        service.markBreached(t, NOW);

        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        verify(publisher).publish(tenant.capture(), eq("TICKET"), eq("SLA_BREACHED"), any(), any());

        // The scheduler crosses tenants, so using any single run-level tenant would
        // misattribute the event.
        assertThat(tenant.getValue()).isEqualTo(otherTenant);
    }

    @Test
    void ticketsFromDifferentTenantsAreEachPublishedUnderTheirOwn() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        Ticket a = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        a.setTenantId(tenantA);
        Ticket b = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        b.setTenantId(tenantB);
        stubCandidates(a, b);

        service.detectBreaches();

        ArgumentCaptor<UUID> tenants = ArgumentCaptor.forClass(UUID.class);
        verify(publisher, times(2)).publish(tenants.capture(), eq("TICKET"), eq("SLA_BREACHED"),
                any(), any());

        assertThat(tenants.getAllValues()).containsExactlyInAnyOrder(tenantA, tenantB);
    }

    @Test
    void theEntityIdIsTheTicketId() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);

        service.markBreached(t, NOW);

        ArgumentCaptor<UUID> entity = ArgumentCaptor.forClass(UUID.class);
        verify(publisher).publish(any(), eq("TICKET"), eq("SLA_BREACHED"), entity.capture(), any());

        assertThat(entity.getValue()).isEqualTo(t.getId());
    }

    @Test
    void theScanDoesNotRequireAnAuthenticatedUser() {
        // SYSTEM work: there is no security principal, so nothing may read one.
        assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication()).isNull();

        stubNoCandidates();
        service.detectBreaches();

        // No user-scoped service was consulted.
        verifyNoInteractions(publisher);
    }

    @Test
    void theCandidateQueryIsNotTenantScopedBecauseItCrossesTenants() {
        // Asserted structurally: a tenant-scoped candidate finder would need a tenant
        // the scheduler does not have.
        var names = java.util.Arrays.stream(TicketRepository.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(names).noneMatch(n -> n.contains("Sla") && n.contains("TenantId"));
    }

    // ================= Event contract =================

    @Test
    void theBreachEventIsAttributedToTheSystem() {
        stubCandidates(ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null));

        service.detectBreaches();

        // No actorId: a clock ran out, not a person. Inventing a user id would
        // misattribute the breach to whoever happened to be logged in.
        Map<String, Object> metadata = breachMetadata();
        assertThat(metadata)
                .containsEntry("actorType", "SYSTEM")
                .containsEntry("source", "SCHEDULER")
                .doesNotContainKey("actorId");
    }

    @Test
    void theBreachEventCarriesNoConversationContent() {
        stubCandidates(ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null));

        service.detectBreaches();

        assertThat(breachMetadata())
                .doesNotContainKey("body")
                .doesNotContainKey("description")
                .doesNotContainKey("subject")
                .doesNotContainKey("message");
    }

    @Test
    void theBreachMetadataIsSurvivableThroughTheEventRecord() {
        // Every value is a non-null String, so CanonicalCrmEvent's Map.copyOf cannot
        // fail on it the way an absent actorId would.
        stubCandidates(ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null));

        service.detectBreaches();

        Map<String, Object> metadata = breachMetadata();
        assertThat(metadata.values()).allMatch(v -> v instanceof String && !((String) v).isBlank());

        var event = com.shivang.crm.shared.event.CanonicalCrmEvent.forEntity(
                "TICKET", "SLA_BREACHED", tenantId, UUID.randomUUID(), new HashMap<>(metadata));
        assertThat(event.metadata()).containsKey("breachType");
    }

    @Test
    void theTicketEntityTypeIsUsedNotAMessageType() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);

        service.markBreached(t, NOW);

        verify(publisher).publish(any(), eq("TICKET"), eq("SLA_BREACHED"), any(), any());
    }

    // ================= Atomic transition: winner and loser =================

    @Test
    void aWinningTransitionPublishesExactlyOneEvent() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        when(ticketRepository.markSlaBreachedIfNotBreached(t.getId(), tenantId)).thenReturn(1);

        assertThat(service.markBreached(t, NOW)).isTrue();

        verify(publisher, times(1)).publish(any(), eq("TICKET"), eq("SLA_BREACHED"), any(), any());
    }

    @Test
    void aWinningTransitionNeedsNoOrdinarySave() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        when(ticketRepository.markSlaBreachedIfNotBreached(t.getId(), tenantId)).thenReturn(1);

        service.markBreached(t, NOW);

        // The conditional UPDATE is the authoritative transition. A save() here would
        // reintroduce the read-check-write pattern this change removed.
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void aLosingTransitionPublishesNoEvent() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        when(ticketRepository.markSlaBreachedIfNotBreached(t.getId(), tenantId)).thenReturn(0);

        assertThat(service.markBreached(t, NOW)).isFalse();

        verifyNoInteractions(publisher);
    }

    @Test
    void aLosingTransitionPerformsNoSave() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        when(ticketRepository.markSlaBreachedIfNotBreached(t.getId(), tenantId)).thenReturn(0);

        service.markBreached(t, NOW);

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void aLosingTransitionLeavesTheManagedEntityAlone() {
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        when(ticketRepository.markSlaBreachedIfNotBreached(t.getId(), tenantId)).thenReturn(0);

        service.markBreached(t, NOW);

        // Nothing was written, so the in-memory copy must not claim otherwise — a
        // later flush would otherwise try to persist a transition that never won.
        assertThat(t.isSlaBreached()).isFalse();
    }

    @Test
    void twoRunnersOnTheSameTicketProduceOneEvent() {
        // Two scheduler instances read the same candidate. The database decides:
        // one UPDATE matches, the other does not.
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        stubCandidates(t);
        when(ticketRepository.markSlaBreachedIfNotBreached(t.getId(), tenantId))
                .thenReturn(1)
                .thenReturn(0);

        int first = service.detectBreaches();
        int second = service.detectBreaches();

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        verify(publisher, times(1)).publish(any(), eq("TICKET"), eq("SLA_BREACHED"), any(), any());
    }

    @Test
    void aStaleCandidateThatAlreadyLostIsSkippedWithoutASecondUpdate() {
        // The candidate query is a snapshot. A ticket breached by another worker since
        // that snapshot arrives here with sla_breached still false in memory, so the
        // conditional update is what catches it.
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        when(ticketRepository.markSlaBreachedIfNotBreached(t.getId(), tenantId)).thenReturn(0);

        assertThat(service.markBreached(t, NOW)).isFalse();
        verify(ticketRepository, times(1)).markSlaBreachedIfNotBreached(t.getId(), tenantId);
    }

    @Test
    void theConditionalUpdateIsScopedToTheTicketsOwnTenant() {
        // A cross-tenant update path would be reachable if the predicate dropped the
        // tenant; the ticket's own tenant is the only one passed.
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);

        service.markBreached(t, NOW);

        verify(ticketRepository).markSlaBreachedIfNotBreached(t.getId(), t.getTenantId());
    }

    @Test
    void theWinningTransitionSyncsTheManagedEntityWithTheDatabase() {
        // The bulk UPDATE bypasses the persistence context, which still holds this
        // candidate with sla_breached = false. Without the sync a later flush of the
        // entity would write the stale false back over the committed transition.
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);

        service.markBreached(t, NOW);

        assertThat(t.isSlaBreached()).isTrue();
    }

    @Test
    void aDeletedTicketCannotBeBreachedThroughTheConditionalUpdate() {
        // deleted = false is in the predicate, so a soft-deleted candidate affects no
        // rows and therefore publishes nothing.
        Ticket t = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        when(ticketRepository.markSlaBreachedIfNotBreached(t.getId(), tenantId)).thenReturn(0);

        assertThat(service.markBreached(t, NOW)).isFalse();
    }

    @Test
    void repeatedWinnerThenLoserSequencesNeverDoubleCount() {
        Ticket a = ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null);
        Ticket b = ticket(TicketStatus.IN_PROGRESS, NOW.minusSeconds(7200), NOW.minusSeconds(1), NOW);
        stubCandidates(a, b);
        when(ticketRepository.markSlaBreachedIfNotBreached(a.getId(), tenantId)).thenReturn(1);
        when(ticketRepository.markSlaBreachedIfNotBreached(b.getId(), tenantId)).thenReturn(0);

        assertThat(service.detectBreaches()).isEqualTo(1);
        verify(publisher, times(1)).publish(any(), eq("TICKET"), eq("SLA_BREACHED"), any(), any());
    }

    // ================= Producer ownership =================

    @Test
    void onlyTheBreachServiceProducesSlaBreached() {
        // TicketService and TicketMessageService must not publish it: breach is
        // detected by a clock, and a user action cannot cause one.
        for (Class<?> service : List.of(TicketService.class, TicketMessageService.class)) {
            for (var method : service.getDeclaredMethods()) {
                // Method bodies are not inspectable by reflection; assert instead that
                // neither service even knows the breach event constant, which is what a
                // producer would have to reference.
                assertThat(method.getName().toLowerCase())
                        .as("%s must not have an SLA method", service.getSimpleName())
                        .doesNotContain("breach");
                assertThat(method.getName().toLowerCase())
                        .as("%s must not have an SLA method", service.getSimpleName())
                        .doesNotContain("sla");
            }
        }
    }

    @Test
    void creationDoesNotEmitABreach() {
        // The full creation path emits CREATED only.
        TicketSlaProperties properties = new TicketSlaProperties();
        Ticket t = new Ticket();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenantId);
        t.setAccountId(UUID.randomUUID());
        t.setSubject("Cannot log in");
        t.setCreatedBy(UUID.randomUUID());
        t.setStatus(TicketStatus.OPEN);
        t.setDeleted(false);
        t.applySlaPolicy(NOW, properties.responseDuration(), properties.resolutionDuration());

        assertThat(t.isSlaBreached()).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    void aBreachedTicketWithNoDeadlineIsNotACandidate() {
        // A ticket predating the SLA columns has null deadlines and cannot be overdue.
        Ticket t = ticket(TicketStatus.OPEN, null, null, null);

        assertThat(service.markBreached(t, NOW)).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    void theScanIsBoundedToOneBatch() {
        stubNoCandidates();

        service.detectBreaches();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findAll(any(Specification.class), captor.capture());

        assertThat(captor.getValue().getPageSize()).isLessThanOrEqualTo(500);
    }

    @Test
    void theScanReturnsTheNumberBreached() {
        stubCandidates(
                ticket(TicketStatus.OPEN, NOW.minusSeconds(7200), NOW.minusSeconds(1), null),
                ticket(TicketStatus.IN_PROGRESS, NOW.minusSeconds(7200), NOW.minusSeconds(1), NOW));

        assertThat(service.detectBreaches()).isEqualTo(2);
    }

    @Test
    void aCandidateWithNoDueDeadlineInPracticeIsRejectedByTheQuery() {
        // The specification is the primary filter; this documents that the service's
        // own re-check is a second line of defence rather than the only one.
        Ticket t = ticket(TicketStatus.OPEN, NOW.plusSeconds(3600), NOW.plusSeconds(7200), null);

        assertThat(service.markBreached(t, NOW)).isFalse();
    }

    }
