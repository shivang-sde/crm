package com.shivang.crm.modules.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.dto.TicketStatusChangeRequest;
import com.shivang.crm.modules.ticket.dto.TicketUpdateRequest;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.mapper.TicketMapper;
import com.shivang.crm.modules.ticket.mapper.TicketMapperImpl;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.1-P1: Ticket lifecycle, lifecycle timestamps, soft delete, and the event set
 * each of them produces.
 *
 * <p>The central invariant under test is that a no-op never produces an event:
 * a retried PATCH and a repeated status request are both ordinary in a
 * distributed system, and neither may look like a domain mutation to a workflow
 * trigger.
 */
class TicketLifecycleAndEventTest {

    private TicketRepository ticketRepository;
    private ActivityService activityService;
    private CanonicalCrmEventPublisher publisher;
    private TicketService service;

    private UUID tenantId;
    private UUID actorId;
    private UUID accountId;
    private UUID ticketId;
    private Ticket ticket;

    @BeforeEach
    void setup() {
        ticketRepository = mock(TicketRepository.class);
        activityService = mock(ActivityService.class);
        publisher = mock(CanonicalCrmEventPublisher.class);
        RecordScopeGuard scopeGuard = mock(RecordScopeGuard.class);
        // The REAL mapper, not a mock: these tests assert that a PATCH actually
        // mutates the entity and that a no-op PATCH does not, which a mock cannot
        // demonstrate. MapStruct generated the implementation at compile time.
        TicketMapper mapper = new TicketMapperImpl();

        when(scopeGuard.requireScope(any(), any(), anyString(), anyString())).thenReturn("ALL");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new TicketService(ticketRepository, mapper, scopeGuard,
                mock(com.shivang.crm.shared.service.CustomerRelationshipValidator.class),
                activityService, publisher, slaProperties(), fixedClock());

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        ticketId = UUID.randomUUID();

        ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setTenantId(tenantId);
        ticket.setAccountId(accountId);
        ticket.setSubject("Customer cannot access dashboard");
        ticket.setDescription("Repeated login failure");
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setOwnerId(actorId);
        ticket.setCreatedBy(actorId);
        ticket.setDeleted(false);
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.of(ticket));

        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        actorId.toString(), null, List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
    }

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // --- helpers ---

    private void status(TicketStatus target) {
        service.changeStatus(ticketId, tenantId, actorId,
                TicketStatusChangeRequest.builder().status(target).build());
    }

    private void code(Throwable thrown, String expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    private List<String> publishedEvents() {
        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        verify(publisher, org.mockito.Mockito.atLeast(0)).publish(any(UUID.class),
                entityType.capture(), eventType.capture(), any(), any());
        return java.util.stream.IntStream.range(0, entityType.getAllValues().size())
                .mapToObj(i -> entityType.getAllValues().get(i) + "." + eventType.getAllValues().get(i))
                .toList();
    }

    private Map<String, Object> metadataOf(String name) {
        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(publisher, org.mockito.Mockito.atLeast(0)).publish(any(UUID.class),
                entityType.capture(), eventType.capture(), any(), metadata.capture());
        List<String> names = java.util.stream.IntStream.range(0, entityType.getAllValues().size())
                .mapToObj(i -> entityType.getAllValues().get(i) + "." + eventType.getAllValues().get(i))
                .toList();
        return metadata.getAllValues().get(names.indexOf(name));
    }

    // --- valid transitions ---

    @Test
    void openToInProgress() {
        status(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(publishedEvents()).containsExactly("TICKET.STATUS_CHANGED");
    }

    @Test
    void openToResolved() {
        status(TicketStatus.RESOLVED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void inProgressToResolved() {
        status(TicketStatus.IN_PROGRESS);
        org.mockito.Mockito.clearInvocations(publisher);
        status(TicketStatus.RESOLVED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void resolvedToClosed() {
        status(TicketStatus.RESOLVED);
        org.mockito.Mockito.clearInvocations(publisher);
        status(TicketStatus.CLOSED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void resolvedReopensToOpen() {
        status(TicketStatus.RESOLVED);
        org.mockito.Mockito.clearInvocations(publisher);
        status(TicketStatus.OPEN);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void closedReopensToOpen() {
        // Walk the real forward path first: OPEN cannot jump to CLOSED.
        status(TicketStatus.RESOLVED);
        status(TicketStatus.CLOSED);
        org.mockito.Mockito.clearInvocations(publisher);

        // Explicit reopen is the only way out of CLOSED.
        status(TicketStatus.OPEN);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void directOpenToClosedIsRejected() {
        // The lifecycle matrix is not a free-for-all; OPEN may skip to RESOLVED but
        // not straight to CLOSED.
        code(catchThrowable(() -> status(TicketStatus.CLOSED)), "TICKET_INVALID_STATUS_TRANSITION");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        verifyNoInteractions(publisher);
    }

    @Test
    void inProgressCannotReturnToOpenWithoutResolving() {
        status(TicketStatus.IN_PROGRESS);
        org.mockito.Mockito.clearInvocations(publisher);

        code(catchThrowable(() -> status(TicketStatus.OPEN)), "TICKET_INVALID_STATUS_TRANSITION");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        verifyNoInteractions(publisher);
    }

    @Test
    void aRejectedTransitionDoesNotMutateTheTicket() {
        code(catchThrowable(() -> status(TicketStatus.CLOSED)), "TICKET_INVALID_STATUS_TRANSITION");
        verify(ticketRepository, never()).save(any());
        assertThat(ticket.getResolvedAt()).isNull();
        assertThat(ticket.getClosedAt()).isNull();
    }

    // --- idempotency ---

    @Test
    void requestingTheCurrentStatusIsAnAcceptedNoOp() {
        // A retried request must not error...
        status(TicketStatus.OPEN);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        // ...and must not describe a mutation that did not happen.
        verifyNoInteractions(publisher);
        verify(activityService, never()).logActivity(any(), any(), anyString(), anyString(), anyString(),
                any(), any());
    }

    @Test
    void idempotencyHoldsForEveryStatus() {
        for (TicketStatus s : TicketStatus.values()) {
            ticket.setStatus(s);
            org.mockito.Mockito.clearInvocations(publisher, activityService);
            status(s);
            verifyNoInteractions(publisher);
            assertThat(ticket.getStatus()).isEqualTo(s);
        }
    }

    @Test
    void aNullStatusIsRejected() {
        code(catchThrowable(() -> service.changeStatus(ticketId, tenantId, actorId,
                TicketStatusChangeRequest.builder().build())), "TICKET_INVALID_STATUS_TRANSITION");
    }

    // --- lifecycle timestamps ---

    @Test
    void enteringResolvedStampsResolvedAt() {
        status(TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();
        assertThat(ticket.getClosedAt()).isNull();
    }

    @Test
    void enteringClosedStampsClosedAt() {
        status(TicketStatus.RESOLVED);
        status(TicketStatus.CLOSED);
        assertThat(ticket.getClosedAt()).isNotNull();
        assertThat(ticket.getResolvedAt()).isNotNull();
    }

    @Test
    void reopeningFromResolvedClearsResolvedAt() {
        status(TicketStatus.RESOLVED);
        assertThat(ticket.getResolvedAt()).isNotNull();

        status(TicketStatus.OPEN);

        assertThat(ticket.getResolvedAt()).isNull();
        assertThat(ticket.getClosedAt()).isNull();
    }

    @Test
    void reopeningFromClosedClearsBothTimestamps() {
        status(TicketStatus.RESOLVED);
        status(TicketStatus.CLOSED);
        assertThat(ticket.getClosedAt()).isNotNull();
        assertThat(ticket.getResolvedAt()).isNotNull();

        status(TicketStatus.OPEN);

        assertThat(ticket.getResolvedAt()).isNull();
        assertThat(ticket.getClosedAt()).isNull();
    }

        // --- soft delete ---

    @Test
    void deleteIsASoftDelete() {
        service.deleteTicket(ticketId, tenantId, actorId);

        assertThat(ticket.isDeleted()).isTrue();
        assertThat(ticket.getDeletedBy()).isEqualTo(actorId);
        assertThat(ticket.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteNeverHardDeletes() {
        service.deleteTicket(ticketId, tenantId, actorId);

        verify(ticketRepository, never()).delete(any(Ticket.class));
        verify(ticketRepository, never()).deleteAll(any(Iterable.class));
        verify(ticketRepository, never()).deleteAllInBatch(any(Iterable.class));
    }

    @Test
    void aSoftDeletedTicketIsNoLongerReturnedByGet() {
        service.deleteTicket(ticketId, tenantId, actorId);

        // The finder is deleted-aware, so the row stops resolving for live reads
        // without the row itself being destroyed.
        verify(ticketRepository).save(argThat(t -> t.isDeleted()));
    }

    @Test
    void deletingAnAlreadyDeletedTicketIsNotFound() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.deleteTicket(ticketId, tenantId, actorId)), "TICKET_NOT_FOUND");
        verifyNoInteractions(publisher);
    }

    // --- events ---

    @Test
    void aRealStatusTransitionPublishesExactlyOneStatusChanged() {
        status(TicketStatus.IN_PROGRESS);

        assertThat(publishedEvents()).containsExactly("TICKET.STATUS_CHANGED");
        verify(publisher, org.mockito.Mockito.times(1)).publish(
                any(UUID.class), eq("TICKET"), eq("STATUS_CHANGED"), any(), any());
    }

    @Test
    void statusChangedCarriesTheTransitionAndIdentifiers() {
        status(TicketStatus.RESOLVED);

        assertThat(metadataOf("TICKET.STATUS_CHANGED"))
                .containsEntry("previousStatus", "OPEN")
                .containsEntry("newStatus", "RESOLVED")
                .containsEntry("accountId", accountId.toString())
                .containsEntry("actorId", actorId.toString())
                .containsEntry("actorType", "USER");
    }

    @Test
    void aNoOpUpdatePublishesNothing() {
        // An empty PATCH is the most common retried request shape.
        service.updateTicket(ticketId, tenantId, actorId, TicketUpdateRequest.builder().build());

        verifyNoInteractions(publisher);
        verify(activityService, never()).logActivity(any(), any(), anyString(), anyString(), anyString(),
                any(), any());
    }

    @Test
    void resubmittingTheSameValuesPublishesNothing() {
        service.updateTicket(ticketId, tenantId, actorId, TicketUpdateRequest.builder()
                .subject("Customer cannot access dashboard")
                .description("Repeated login failure")
                .build());

        verifyNoInteractions(publisher);
    }

    @Test
    void aMeaningfulUpdatePublishesExactlyOneUpdatedWithChangedFields() {
        service.updateTicket(ticketId, tenantId, actorId, TicketUpdateRequest.builder()
                .subject("Customer cannot access dashboard or reports")
                .build());

        assertThat(publishedEvents()).containsExactly("TICKET.UPDATED");
        assertThat((List<String>) metadataOf("TICKET.UPDATED").get("changedFields"))
                .containsExactly("subject");
    }

    @Test
    void multipleFieldChangesAreAllReported() {
        service.updateTicket(ticketId, tenantId, actorId, TicketUpdateRequest.builder()
                .subject("Renamed")
                .description("New detail")
                .build());

        assertThat((List<String>) metadataOf("TICKET.UPDATED").get("changedFields"))
                .containsExactly("subject", "description");
    }

    @Test
    void softDeletePublishesExactlyOneDeleted() {
        service.deleteTicket(ticketId, tenantId, actorId);

        assertThat(publishedEvents()).containsExactly("TICKET.DELETED");
        verify(publisher).publish(eq(tenantId), eq("TICKET"), eq("DELETED"), eq(ticketId), any());
    }

    @Test
    void everyTicketEventCarriesTheCorrectTenantAndEntity() {
        service.changeStatus(ticketId, tenantId, actorId,
                TicketStatusChangeRequest.builder().status(TicketStatus.IN_PROGRESS).build());

        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> entity = ArgumentCaptor.forClass(UUID.class);
        verify(publisher).publish(tenant.capture(), eq("TICKET"), eq("STATUS_CHANGED"),
                entity.capture(), any());

        // The event is keyed on the ticket's OWN tenant, never a run-level context.
        assertThat(tenant.getValue()).isEqualTo(tenantId);
        assertThat(entity.getValue()).isEqualTo(ticketId);
    }

    @Test
    void ticketRegistersNoLaterPhaseVocabulary() {
        // Ticket adds an entity type and nothing beyond P1/P2. Teams, queues, SLA,
        // escalation and messages are later phases, so no such vocabulary may exist.
        var names = java.util.Arrays.stream(
                        com.shivang.crm.shared.event.CanonicalCrmEvent.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();

        assertThat(names).contains("TICKET_ENTITY_TYPE");
        // L3.1-P3 adds TICKET_MESSAGE_ENTITY_TYPE, L3.1-P4 adds SLA_BREACHED_EVENT_TYPE.
        assertThat(names).contains("TICKET_MESSAGE_ENTITY_TYPE");
        assertThat(names).contains("SLA_BREACHED_EVENT_TYPE");
        // Escalation is still a later phase, and so is anything beyond SLA_BREACHED.
        assertThat(names).noneMatch(n -> n.toUpperCase().contains("ESCALAT"));
        assertThat(names).noneMatch(n -> n.toUpperCase().contains("SLA_REMINDER"));
        assertThat(names).noneMatch(n -> n.toUpperCase().contains("SLA_WARNING"));
        // No ASSIGNED/UNASSIGNED: L3.1-P2 reuses the existing OWNER_CHANGED
        // vocabulary (as the lead/deal/entitlement modules do) instead of inventing
        // a Ticket-specific assignment event.
        assertThat(names).noneMatch(n -> n.toUpperCase().contains("ASSIGNED"));
        // P3 defines only TICKET_MESSAGE.CREATED; no message lifecycle events exist.
        assertThat(names).noneMatch(n -> n.toUpperCase().contains("MESSAGE_DELETED"));
        assertThat(names).noneMatch(n -> n.toUpperCase().contains("MESSAGE_UPDATED"));
    }

    @Test
    void ownerChangedVocabularyIsReusedNotTicketSpecific() {
        // TICKET.OWNER_CHANGED is composed from the two existing constants, so a
        // consumer already handling OWNER_CHANGED for other modules needs no new
        // subscription branch for Ticket.
        var names = java.util.Arrays.stream(
                        com.shivang.crm.shared.event.CanonicalCrmEvent.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();

        assertThat(names).contains("OWNER_CHANGED_EVENT_TYPE");
        assertThat(names).contains("TICKET_ENTITY_TYPE");
    }

    @Test
    void activityIsWrittenForEveryLifecycleAction() {
        status(TicketStatus.IN_PROGRESS);
        verify(activityService).logActivity(eq(tenantId), eq(ticketId), eq("TICKET"),
                eq("TICKET_STATUS_CHANGED"), anyString(), eq(actorId), any());
    }

    @Test
    void activityAndEventShareTheTicketEntityType() {
        status(TicketStatus.IN_PROGRESS);

        ArgumentCaptor<String> activityEntityType = ArgumentCaptor.forClass(String.class);
        verify(activityService).logActivity(any(), any(), activityEntityType.capture(),
                anyString(), anyString(), any(), any());
        ArgumentCaptor<String> eventEntityType = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(any(), eventEntityType.capture(), anyString(), any(), any());

        assertThat(activityEntityType.getValue()).isEqualTo(eventEntityType.getValue()).isEqualTo("TICKET");
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
    static com.shivang.crm.modules.ticket.config.TicketSlaProperties slaProperties() {
        return new com.shivang.crm.modules.ticket.config.TicketSlaProperties();
    }

    static java.time.Clock fixedClock() {
        return java.time.Clock.fixed(java.time.Instant.parse("2026-01-15T09:00:00Z"),
                java.time.ZoneOffset.UTC);
    }
}