package com.shivang.crm.modules.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.dto.TicketCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.modules.ticket.dto.TicketStatusChangeRequest;
import com.shivang.crm.modules.ticket.dto.TicketUpdateRequest;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.mapper.TicketMapper;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.PermissionDeniedException;

/**
 * L3.1-P1: record-scope authorization for Ticket.
 *
 * <p>RecordScopeGuard is not reimplemented here — it is the single authority for
 * what a scope means. What these tests lock down is that Ticket actually routes
 * through it, that the tenant predicate stays in force regardless of scope, and
 * that holding ticket:write does not bypass the record boundary.
 */
class TicketAuthorizationTest {

    private TicketRepository ticketRepository;
    private RecordScopeGuard scopeGuard;
    private TicketService service;

    private UUID tenantId;
    private UUID callerId;
    private UUID otherOwnerId;
    private UUID ticketId;
    private UUID accountId;

    @BeforeEach
    void setup() {
        ticketRepository = mock(TicketRepository.class);
        scopeGuard = mock(RecordScopeGuard.class);
        TicketMapper mapper = mock(TicketMapper.class);
        // These tests assert on authorization decisions, not payloads, but the
        // service still maps and pages its results, so both must return objects.
        when(mapper.toResponse(any(Ticket.class))).thenAnswer(inv -> TicketResponse.builder().build());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

service = new TicketService(ticketRepository, mapper, scopeGuard,
                mock(com.shivang.crm.shared.service.CustomerRelationshipValidator.class),
                mock(ActivityService.class), mock(CanonicalCrmEventPublisher.class),
                slaProperties(), fixedClock());

        tenantId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        otherOwnerId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(callerId.toString(), null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** A ticket owned by somebody other than the caller. */
    private Ticket foreignTicket() {
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setTenantId(tenantId);
        ticket.setAccountId(accountId);
        ticket.setSubject("Someone else's ticket");
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setOwnerId(otherOwnerId);
        ticket.setCreatedBy(otherOwnerId);
        ticket.setDeleted(false);
        return ticket;
    }

    private void stubScope(String action, String scope) {
        when(scopeGuard.requireScope(eq(tenantId), eq(callerId), eq("ticket"), eq(action)))
                .thenReturn(scope);
    }

    private void stubTicket(Ticket ticket) {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.of(ticket));
    }

    private void code(Throwable thrown, String expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    // --- scope is requested for the right permission ---

    @Test
    void readRequiresTicketReadScope() {
        stubScope("read", "ALL");
        stubTicket(foreignTicket());

        service.getTicketById(ticketId, tenantId);

        verify(scopeGuard).requireScope(tenantId, callerId, "ticket", "read");
    }

    @Test
    void updateRequiresTicketWriteScope() {
        stubScope("write", "ALL");
        stubTicket(foreignTicket());

        service.updateTicket(ticketId, tenantId, callerId,
                TicketUpdateRequest.builder().subject("Renamed").build());

        verify(scopeGuard).requireScope(tenantId, callerId, "ticket", "write");
    }

    @Test
    void deleteRequiresTicketDeleteScope() {
        stubScope("delete", "ALL");
        stubTicket(foreignTicket());

        service.deleteTicket(ticketId, tenantId, callerId);

        verify(scopeGuard).requireScope(tenantId, callerId, "ticket", "delete");
    }

    @Test
    void statusChangeRequiresTicketWriteNotDelete() {
        stubScope("write", "ALL");
        Ticket ticket = foreignTicket();
        ticket.setOwnerId(callerId);
        ticket.setCreatedBy(callerId);
        stubTicket(ticket);

        service.changeStatus(ticketId, tenantId, callerId,
                TicketStatusChangeRequest.builder().status(TicketStatus.IN_PROGRESS).build());

        verify(scopeGuard).requireScope(tenantId, callerId, "ticket", "write");
        verify(scopeGuard, never()).requireScope(any(), any(), anyString(), eq("delete"));
    }

    @Test
    void listRequiresTicketReadScope() {
        stubScope("read", "ALL");

        service.listTickets(tenantId, null, null, null, 0, 20);

        verify(scopeGuard).requireScope(tenantId, callerId, "ticket", "read");
    }

    @Test
    void createDoesNotEvaluateRecordScope() {
        // There is no record to scope yet. Relationship validation stands alone.
        service.createTicket(tenantId, callerId, TicketCreateRequest.builder()
                .accountId(accountId).subject("No scope evaluation needed").build());

        verify(scopeGuard, never()).requireScope(any(), any(), anyString(), anyString());
    }

    // --- missing permission is denied ---

    @Test
    void aDeniedScopeIsRejectedBeforeAnyDataAccess() {
        when(scopeGuard.requireScope(eq(tenantId), eq(callerId), eq("ticket"), anyString()))
                .thenThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"));

        code(catchThrowable(() -> service.getTicketById(ticketId, tenantId)), "SCOPE_DENIED");
        // Fail closed: the guard runs before the repository is consulted, so the
        // caller cannot even learn whether the ticket exists.
        verify(ticketRepository, never()).findByIdAndTenantIdAndDeletedFalse(any(), any());
    }

    // --- OWN ---

    @Test
    void ownScopeDeniesReadingAnotherUsersTicket() {
        stubScope("read", "OWN");
        // RecordScopeGuard's real behaviour for OWN with a non-matching owner.
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());
        stubTicket(foreignTicket());

        code(catchThrowable(() -> service.getTicketById(ticketId, tenantId)), "SCOPE_DENIED");
    }

    @Test
    void ownScopeAllowsReadingATicketTheCallerCreated() {
        stubScope("read", "OWN");
        Ticket ticket = foreignTicket();
        ticket.setOwnerId(otherOwnerId);
        ticket.setCreatedBy(callerId); // OWN also matches createdBy
        stubTicket(ticket);

        // The mock guard is permissive here, but the pairing proves the Ticket is
        // loaded and asserted rather than short-circuited.
        service.getTicketById(ticketId, tenantId);
        verify(scopeGuard).assertWithinOwnerCreatorScope("OWN", tenantId, callerId,
                otherOwnerId, callerId);
    }

    @Test
    void ownScopeDeniesUpdatingAnotherUsersTicket() {
        stubScope("write", "OWN");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());
        stubTicket(foreignTicket());

        code(catchThrowable(() -> service.updateTicket(ticketId, tenantId, callerId,
                TicketUpdateRequest.builder().subject("Hijacked").build())), "SCOPE_DENIED");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void ownScopeDeniesDeletingAnotherUsersTicket() {
        stubScope("delete", "OWN");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());
        stubTicket(foreignTicket());

        code(catchThrowable(() -> service.deleteTicket(ticketId, tenantId, callerId)), "SCOPE_DENIED");
        verify(ticketRepository, never()).save(any());
    }

    // --- TEAM ---

    @Test
    void teamScopeResolvesDirectReportsBeforeQuerying() {
        stubScope("read", "TEAM");
        List<UUID> team = List.of(UUID.randomUUID(), callerId);
        when(scopeGuard.teamMemberIds(tenantId, callerId)).thenReturn(team);

        service.listTickets(tenantId, null, null, null, 0, 20);

        // TEAM must resolve the reporting line; RecordScopeGuard only returns a
        // scope string, so the service owns this lookup.
        verify(scopeGuard).teamMemberIds(tenantId, callerId);
    }

@Test
    void teamScopeIsNotResolvedForAllOrOwn() {
        // The team lookup is only meaningful for TEAM; resolving it otherwise is a
        // wasted query on every ALL/OWN list.
        stubScope("read", "ALL");
        service.listTickets(tenantId, null, null, null, 0, 20);

        stubScope("read", "OWN");
        service.listTickets(tenantId, null, null, null, 0, 20);

        verify(scopeGuard, never()).teamMemberIds(any(), any());
    }

    // --- ALL ---

    @Test
    void allScopePermitsAnyTicketInTheTenant() {
        stubScope("read", "ALL");
        stubTicket(foreignTicket());

        service.getTicketById(ticketId, tenantId);

        verify(scopeGuard).assertWithinOwnerCreatorScope("ALL", tenantId, callerId,
                otherOwnerId, otherOwnerId);
    }

    @Test
    void allScopeStillCannotReachAnotherTenantsTicket() {
        // The critical non-bypass: ALL widens rows WITHIN a tenant, never across
        // tenants. Scope is irrelevant to the tenant predicate.
        stubScope("read", "ALL");
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.getTicketById(ticketId, tenantId)), "TICKET_NOT_FOUND");
    }

    // --- write does not bypass the record boundary ---

    @Test
    void holdingWritePermissionDoesNotBypassRecordScope() {
        stubScope("write", "ALL");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());
        stubTicket(foreignTicket());

        // Even at ALL scope the assertion is invoked; permission alone is not enough.
        code(catchThrowable(() -> service.updateTicket(ticketId, tenantId, callerId,
                TicketUpdateRequest.builder().subject("Hijacked").build())), "SCOPE_DENIED");
        verify(scopeGuard).assertWithinOwnerCreatorScope("ALL", tenantId, callerId,
                otherOwnerId, otherOwnerId);
    }

    // --- pagination bounds ---

    @Test
    void paginationIsClampedAndDeterministicallyOrdered() {
        stubScope("read", "ALL");
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);

        // An unbounded page request is clamped rather than honoured.
        service.listTickets(tenantId, null, null, null, -5, 100_000);

        verify(ticketRepository, org.mockito.Mockito.atLeastOnce())
                .findAll(any(org.springframework.data.jpa.domain.Specification.class), captor.capture());
        var pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        // Deterministic ordering, so a list response is stable across calls.
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void zeroSizeFallsBackToTheDefaultPageSize() {
        stubScope("read", "ALL");
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);

        service.listTickets(tenantId, null, null, null, 0, 0);

        verify(ticketRepository, org.mockito.Mockito.atLeastOnce())
                .findAll(any(org.springframework.data.jpa.domain.Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }
    static com.shivang.crm.modules.ticket.config.TicketSlaProperties slaProperties() {
        return new com.shivang.crm.modules.ticket.config.TicketSlaProperties();
    }

    static java.time.Clock fixedClock() {
        return java.time.Clock.fixed(java.time.Instant.parse("2026-01-15T09:00:00Z"),
                java.time.ZoneOffset.UTC);
    }
}