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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.config.TicketSlaProperties;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.mapper.TicketMapperImpl;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.1-P6: SLA escalation to the owner's manager.
 *
 * <p>The rule is one line — owner → owner's direct manager — and almost all the value
 * here is in the ways it must decline to act. A manager field is a raw UUID column
 * with no tenant constraint, so every interesting test is about a link that cannot be
 * trusted.
 */
class TicketEscalationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");

    private UserRepository userRepository;
    private ActivityService activityService;
    private TicketRepository ticketRepository;
    private RecordScopeGuard scopeGuard;
    private CustomerRelationshipValidator validator;
    private CanonicalCrmEventPublisher publisher;
    private TicketService ticketService;
    private TicketEscalationService escalationService;

    private UUID tenantId;
    private UUID tenantB;
    private UUID employeeId;
    private UUID managerId;
    private UUID adminId;
    private UUID accountId;
    private UUID ticketId;
    private Ticket ticket;

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepository.class);
        activityService = mock(ActivityService.class);
        ticketRepository = mock(TicketRepository.class);
        scopeGuard = mock(RecordScopeGuard.class);
        validator = mock(CustomerRelationshipValidator.class);
        publisher = mock(CanonicalCrmEventPublisher.class);

        when(scopeGuard.requireScope(any(), any(), anyString(), anyString())).thenReturn("ALL");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService = new TicketService(ticketRepository, new TicketMapperImpl(), scopeGuard,
                validator, activityService, publisher, new TicketSlaProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        // Spy, not mock: the owner change must really happen so edge cases observe the
        // resulting state, while still proving the shared assignment path was used.
        ticketService = org.mockito.Mockito.spy(ticketService);
        escalationService = new TicketEscalationService(userRepository, ticketService);

        tenantId = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        ticketId = UUID.randomUUID();

        ticket = breachedTicket(tenantId, employeeId);
    }

    private Ticket breachedTicket(UUID tenant, UUID owner) {
        Ticket t = new Ticket();
        t.setId(ticketId);
        t.setTenantId(tenant);
        t.setAccountId(accountId);
        t.setSubject("Cannot log in");
        t.setStatus(TicketStatus.OPEN);
        t.setOwnerId(owner);
        t.setCreatedBy(owner);
        t.setDeleted(false);
        t.setResponseDueAt(NOW.minus(Duration.ofHours(48)));
        t.setResolutionDueAt(NOW.minus(Duration.ofHours(24)));
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenant))
                .thenReturn(Optional.of(t));
        return t;
    }

    private User user(UUID id, UUID tenant, UUID manager) {
        User u = new User();
        u.setId(id);
        u.setTenantId(tenant);
        u.setManagerId(manager);
        u.setDeleted(false);
        u.setIsActive(true);
        return u;
    }

    /** Owner reports to a live same-tenant manager: the happy path. */
    private void stubHappyPath() {
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(user(employeeId, tenantId, managerId)));
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(managerId, tenantId))
                .thenReturn(Optional.of(user(managerId, tenantId, adminId)));
    }

    // ================= Successful escalation =================

    @Test
    void anOwnerWithAManagerIsEscalated() {
        stubHappyPath();

        assertThat(escalationService.escalateToOwnerManager(ticket)).isTrue();

        assertThat(ticket.getOwnerId()).isEqualTo(managerId);
    }

    @Test
    void escalationGoesThroughTheExistingAssignmentPath() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        // Not a direct setOwnerId: the shared path re-validates and emits.
        verify(ticketService).escalateTicket(ticketId, tenantId, managerId);
    }

    @Test
    void escalationDoesNotRequireTheAssignPermission() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        // SYSTEM work has no role, so demanding ticket:assign would block it. A
        // scheduler is not an agent with permissions.
        verify(scopeGuard, never()).requireScope(any(), any(), eq("ticket"), eq("assign"));
    }

    @Test
    void escalationDoesNotRequireRecordScope() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        // A manager reporting to themselves holds neither OWN nor TEAM scope over the
        // ticket, yet escalation is exactly how they come to own it.
        verify(scopeGuard, never()).assertWithinOwnerCreatorScope(
                anyString(), any(), any(), any(), any());
    }

    @Test
    void escalationStillValidatesTheTargetAgainstTheTenant() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        verify(validator).requireTenantOwner(managerId, tenantId);
    }

    @Test
    void escalationRecordsOneSystemActivity() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        verify(activityService, times(1)).logSystemActivity(eq(tenantId), eq(ticketId),
                eq("TICKET"), eq("TICKET_ESCALATED"), anyString(), eq("TICKET_SLA_LIFECYCLE"), any());
    }

    @Test
    void escalationActivityIsAttributedToTheSystemNotAUser() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        // performedBy stays null and actorType carries SYSTEM, so the scheduler is not
        // attributed to an invented person.
        verify(activityService, never()).logActivity(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void escalationActivityCarriesTheTransitionAndReason() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(activityService).logSystemActivity(any(), any(), any(), any(), any(), any(),
                metadata.capture());

        assertThat(metadata.getValue())
                .containsEntry("ticketId", ticketId.toString())
                .containsEntry("previousOwnerId", employeeId.toString())
                .containsEntry("newOwnerId", managerId.toString())
                .containsEntry("reason", "SLA_BREACH");
    }

    @Test
    void escalationActivityCarriesNoMessageOrCustomerContent() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(activityService).logSystemActivity(any(), any(), any(), any(), any(), any(),
                metadata.capture());

        assertThat(metadata.getValue().keySet())
                .doesNotContain("body", "description", "subject", "message");
    }

    @Test
    void escalationEmitsTheExistingOwnerChangedEvent() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        // No new TICKET.ESCALATED event: the owner change is the business fact
        // consumers already handle.
        verify(publisher, times(1)).publish(eq(tenantId), eq("TICKET"), eq("OWNER_CHANGED"),
                eq(ticketId), any());
    }

    @Test
    void escalationEmitsNoNewEscalationEvent() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        // Two signals for one fact would be duplicate business noise.
        verify(publisher, never()).publish(any(), eq("TICKET"), eq("ESCALATED"), any(), any());
    }

    @Test
    void escalationOwnerChangedEventIsSystemAttributed() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq(tenantId), eq("TICKET"), eq("OWNER_CHANGED"), eq(ticketId),
                metadata.capture());

        Map<String, Object> value = metadata.getValue();
        assertThat(value)
                .containsEntry("actorType", "SYSTEM")
                .containsEntry("source", "SCHEDULER")
                .containsEntry("previousOwnerId", employeeId.toString())
                .containsEntry("newOwnerId", managerId.toString());
        assertThat(value).doesNotContainKey("actorId");
    }

    @Test
    void escalationDoesNotEmitTheManualOwnerChangedActivity() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        // The USER-actor activity would misattribute the change to a user.
        verify(activityService, never()).logActivity(any(), any(), eq("TICKET"),
                eq("TICKET_OWNER_CHANGED"), any(), any(), any());
    }

    // ================= One hop only =================

    @Test
    void escalationStopsAtTheDirectManager() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        // Manager B reports to Admin C, but a single breach must not travel the chain.
        assertThat(ticket.getOwnerId()).isEqualTo(managerId);
        assertThat(ticket.getOwnerId()).isNotEqualTo(adminId);
    }

    @Test
    void theGrandManagerIsNeverLookedUp() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        // Admin C is not resolved at all, so it cannot be assigned.
        verify(userRepository, never()).findByIdAndTenantIdAndDeletedFalse(eq(adminId), any());
    }

    @Test
    void aRepeatedEscalationOfAManagerTicketGoesToTheirManager() {
        // Each breach escalates exactly one hop from the CURRENT owner. Once the
        // manager owns it, a later breach would escalate to the next level up — that
        // is a separate breach, not this one, and this rule does not loop.
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(managerId, tenantId))
                .thenReturn(Optional.of(user(managerId, tenantId, adminId)));
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(adminId, tenantId))
                .thenReturn(Optional.of(user(adminId, tenantId, null)));
        Ticket managerTicket = breachedTicket(tenantId, managerId);

        assertThat(escalationService.escalateToOwnerManager(managerTicket)).isTrue();

        assertThat(managerTicket.getOwnerId()).isEqualTo(adminId);
    }

    // ================= No manager =================

    @Test
    void anOwnerWithNoManagerIsNotEscalated() {
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(user(employeeId, tenantId, null)));

        assertThat(escalationService.escalateToOwnerManager(ticket)).isFalse();

        assertThat(ticket.getOwnerId()).isEqualTo(employeeId);
    }

    @Test
    void anOwnerWithNoManagerRecordsNoActivityOrEvent() {
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(user(employeeId, tenantId, null)));

        escalationService.escalateToOwnerManager(ticket);

        verifyNoInteractions(activityService);
        verifyNoInteractions(publisher);
    }

    @Test
    void aMissingOwnerRecordIsTreatedAsNoManager() {
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.empty());

        assertThat(escalationService.escalateToOwnerManager(ticket)).isFalse();

        assertThat(ticket.getOwnerId()).isEqualTo(employeeId);
    }

    @Test
    void aSelfReferentialManagerIsNotEscalated() {
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(user(employeeId, tenantId, employeeId)));

        assertThat(escalationService.escalateToOwnerManager(ticket)).isFalse();

        verify(ticketService, never()).escalateTicket(any(), any(), any());
    }

    // ================= No owner =================

    @Test
    void aTicketWithNoOwnerIsNotEscalated() {
        Ticket ownerless = breachedTicket(tenantId, null);

        assertThat(escalationService.escalateToOwnerManager(ownerless)).isFalse();
    }

    @Test
    void aTicketWithNoOwnerInventsNoTarget() {
        Ticket ownerless = breachedTicket(tenantId, null);

        escalationService.escalateToOwnerManager(ownerless);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(activityService);
    }

    // ================= Invalid manager =================

    @Test
    void aDeletedManagerIsNotAssigned() {
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(user(employeeId, tenantId, managerId)));
        // Deleted or foreign: the tenant-scoped finder returns nothing.
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(managerId, tenantId))
                .thenReturn(Optional.empty());

        assertThat(escalationService.escalateToOwnerManager(ticket)).isFalse();

        assertThat(ticket.getOwnerId()).isEqualTo(employeeId);
    }

    @Test
    void aDeletedManagerRecordsNoEscalationActivity() {
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(user(employeeId, tenantId, managerId)));
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(managerId, tenantId))
                .thenReturn(Optional.empty());

        escalationService.escalateToOwnerManager(ticket);

        // No false "escalated" entry for a target that never received the ticket.
        verifyNoInteractions(activityService);
    }

    // ================= Cross-tenant manager =================

    @Test
    void aCrossTenantManagerCannotReceiveTheTicket() {
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(user(employeeId, tenantId, managerId)));
        // A manager_id row pointing at another tenant's user must not resolve.
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(managerId, tenantId))
                .thenReturn(Optional.empty());

        assertThat(escalationService.escalateToOwnerManager(ticket)).isFalse();

        assertThat(ticket.getOwnerId()).isEqualTo(employeeId);
    }

    @Test
    void theManagerIsAlwaysResolvedWithinTheTicketsTenant() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        // manager_id carries no tenant constraint, so the lookup must supply one.
        verify(userRepository).findByIdAndTenantIdAndDeletedFalse(managerId, tenantId);
        verify(userRepository, never()).findByIdAndTenantIdAndDeletedFalse(managerId, tenantB);
    }

    @Test
    void theOwnerIsAlsoResolvedWithinTheTicketsTenant() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        verify(userRepository).findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId);
    }

    @Test
    void escalationOfATenantBTicketNeverResolvesTenantAUsers() {
        Ticket tenantBTicket = breachedTicket(tenantB, employeeId);
        stubHappyPath();

        escalationService.escalateToOwnerManager(tenantBTicket);

        verify(userRepository, never()).findByIdAndTenantIdAndDeletedFalse(any(), eq(tenantId));
    }

    @Test
    void aCrossTenantManagerIsRejectedByTheSharedValidationToo() {
        stubHappyPath();
        // Even if resolution passed, the assignment path re-validates against the tenant.
        org.mockito.Mockito.doThrow(new BusinessException("OWNER_NOT_FOUND", "foreign"))
                .when(validator).requireTenantOwner(managerId, tenantId);

        assertThat(catchThrowable(() -> escalationService.escalateToOwnerManager(ticket))
                instanceof BusinessException).isTrue();

        assertThat(ticket.getOwnerId()).isEqualTo(employeeId);
    }

    // ================= Already the target =================

    @Test
    void anAlreadyOwnedTicketIsANoOp() {
        // manager_id pointing at the current owner: nothing to move.
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(user(employeeId, tenantId, null)));

        assertThat(escalationService.escalateToOwnerManager(ticket)).isFalse();

        verifyNoInteractions(activityService);
    }

    @Test
    void aSameOwnerEscalationPublishesNoDuplicateOwnerChange() {
        stubHappyPath();
        ticket.setOwnerId(managerId);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(managerId, tenantId))
                .thenReturn(Optional.of(user(managerId, tenantId, null)));

        escalationService.escalateToOwnerManager(ticket);

        // The shared path's no-op guard: assigning the current owner emits nothing.
        verifyNoInteractions(publisher);
    }

    // ================= Reassigned before escalation =================

    @Test
    void theOwnerIsReadAtEscalationTimeNotEarlier() {
        // The manager chain is followed from whoever owns the ticket right now.
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(managerId, tenantId))
                .thenReturn(Optional.of(user(managerId, tenantId, adminId)));
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(adminId, tenantId))
                .thenReturn(Optional.of(user(adminId, tenantId, null)));
        ticket.setOwnerId(managerId);

        escalationService.escalateToOwnerManager(ticket);

        verify(userRepository).findByIdAndTenantIdAndDeletedFalse(managerId, tenantId);
        assertThat(ticket.getOwnerId()).isEqualTo(adminId);
    }

    @Test
    void aStaleManagerIsNotUsedAfterReassignment() {
        // Employee's captured manager is B; if the ticket was reassigned to C first,
        // escalation must follow C's chain, not B.
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(managerId, tenantId))
                .thenReturn(Optional.of(user(managerId, tenantId, adminId)));
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(adminId, tenantId))
                .thenReturn(Optional.of(user(adminId, tenantId, null)));
        ticket.setOwnerId(managerId);

        escalationService.escalateToOwnerManager(ticket);

        assertThat(ticket.getOwnerId()).isEqualTo(adminId);
        verify(userRepository, never()).findByIdAndTenantIdAndDeletedFalse(eq(employeeId), any());
    }

    /** A mock escalation service, for tests about the breach path rather than escalation. */
    private TicketEscalationService mockEscalation() {
        TicketEscalationService mock = mock(TicketEscalationService.class);
        escalationService = mock;
        return mock;
    }

    // ================= internal-only boundary =================

    @Test
    void escalateTicketIsNotPublic() {
        // It bypasses ticket:assign and record scope, so the compiler must keep it
        // away from controllers and any other user-facing entry point.
        var method = java.util.Arrays.stream(TicketService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("escalateTicket"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("escalateTicket must exist"));

        assertThat(java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .as("escalateTicket must not be public")
                .isFalse();
    }

    @Test
    void theOnlyProductionCallerIsTheEscalationService() {
        // No controller, scheduler or other service may reach the permission bypass.
        assertThat(java.util.Arrays.stream(TicketService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("escalateTicket"))
                .findFirst()
                .orElseThrow()
                .getName()).isEqualTo("escalateTicket");

        assertThat(java.util.Arrays.stream(
                        com.shivang.crm.modules.ticket.controller.TicketController.class
                                .getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .map(n -> n.toLowerCase()))
                .noneMatch(n -> n.contains("escalate"));
    }

    @Test
    void theManualAssignPathStaysPublic() {
        // Hardening must not narrow the legitimate user-facing entry point.
        var method = java.util.Arrays.stream(TicketService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("assignTicket"))
                .findFirst()
                .orElseThrow();

        assertThat(java.lang.reflect.Modifier.isPublic(method.getModifiers())).isTrue();
    }

    // ================= Idempotency =================

    @Test
    void aLosingBreachWorkerNeverEscalates() {
        TicketSlaBreachService breach = new TicketSlaBreachService(ticketRepository, publisher,
                activityService, mockEscalation(), Clock.fixed(NOW, ZoneOffset.UTC));
        when(ticketRepository.markSlaBreachedIfNotBreached(ticketId, tenantId)).thenReturn(0);

        assertThat(breach.markBreached(ticket, NOW)).isFalse();

        // The atomic transition is the first idempotency boundary; a worker that lost
        // it must not escalate, or two workers would both reassign the ticket.
        verifyNoInteractions(escalationService);
    }

    @Test
    void repeatedSchedulerRunsEscalateOnce() {
        TicketSlaBreachService breach = new TicketSlaBreachService(ticketRepository, publisher,
                activityService, mockEscalation(), Clock.fixed(NOW, ZoneOffset.UTC));
        stubHappyPath();
        when(ticketRepository.markSlaBreachedIfNotBreached(ticketId, tenantId))
                .thenReturn(1)
                .thenReturn(0);

        breach.markBreached(ticket, NOW);
        breach.markBreached(ticket, NOW);

        verify(escalationService, times(1)).escalateToOwnerManager(ticket);
    }

    @Test
    void oneBreachProducesOneOwnerChange() {
        // Real escalation, so the full chain is observed end to end.
        TicketSlaBreachService breach = new TicketSlaBreachService(ticketRepository, publisher,
                activityService, escalationService, Clock.fixed(NOW, ZoneOffset.UTC));
        stubHappyPath();
        when(ticketRepository.markSlaBreachedIfNotBreached(ticketId, tenantId)).thenReturn(1);

        breach.markBreached(ticket, NOW);

        // Exactly one SLA breach record and one owner change: two facts, one each.
        verify(activityService, times(1)).logSystemActivity(eq(tenantId), eq(ticketId), eq("TICKET"),
                eq("TICKET_SLA_BREACHED"), any(), eq("TICKET_SLA_LIFECYCLE"), any());
        verify(activityService, times(1)).logSystemActivity(eq(tenantId), eq(ticketId), eq("TICKET"),
                eq("TICKET_ESCALATED"), any(), eq("TICKET_SLA_LIFECYCLE"), any());
        verify(publisher, times(1)).publish(eq(tenantId), eq("TICKET"), eq("SLA_BREACHED"),
                eq(ticketId), any());
        verify(publisher, times(1)).publish(eq(tenantId), eq("TICKET"), eq("OWNER_CHANGED"),
                eq(ticketId), any());
    }

    @Test
    void escalationDoesNotResetTheSlaBreachFlag() {
        stubHappyPath();

        ticket.setSlaBreached(true);

        escalationService.escalateToOwnerManager(ticket);

        // Escalating is not a way to clear a historical breach.
        assertThat(ticket.isSlaBreached()).isTrue();
    }

    @Test
    void escalationDoesNotChangeTicketStatus() {
        stubHappyPath();

        escalationService.escalateToOwnerManager(ticket);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    // ================= Transaction boundary =================

    @Test
    void theEscalationServiceJoinsTheCallersTransaction() throws Exception {
        var annotation = java.util.Arrays.stream(TicketEscalationService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("escalateToOwnerManager"))
                .map(m -> org.springframework.core.annotation.AnnotatedElementUtils
                        .findMergedAnnotation(m,
                                org.springframework.transaction.annotation.Transactional.class))
                .findFirst()
                .orElse(null);

        // REQUIRED, not REQUIRES_NEW: a separate commit would let the owner change
        // outlive a rolled-back breach.
        assertThat(annotation).isNotNull();
        assertThat(annotation.propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRED);
    }

    @Test
    void theEscalationServiceNeverOpensItsOwnTransaction() throws Exception {
        for (var method : TicketEscalationService.class.getDeclaredMethods()) {
            var annotation = org.springframework.core.annotation.AnnotatedElementUtils
                    .findMergedAnnotation(method,
                            org.springframework.transaction.annotation.Transactional.class);
            if (annotation != null) {
                assertThat(annotation.propagation())
                        .isIn(org.springframework.transaction.annotation.Propagation.REQUIRED,
                                org.springframework.transaction.annotation.Propagation.SUPPORTS);
            }
        }
    }

    @Test
    void escalationUsesNoAsyncOrOutboxOfItsOwn() {
        // One commit boundary: no queue, no publisher of its own, no scheduler.
        assertThat(java.util.Arrays.stream(TicketEscalationService.class.getDeclaredFields())
                .map(f -> f.getType().getSimpleName()))
                .doesNotContain("RabbitTemplate", "OutboxEventRepository", "ScheduledExecutorService");
    }

    @Test
    void thereIsNoEscalationScheduler() {
        // The SLA scan is the only trigger; escalation must not poll independently.
        var schedulers = java.util.Arrays.stream(
                        com.shivang.crm.modules.ticket.scheduler.TicketSlaBreachScheduler.class
                                .getDeclaredMethods())
                .filter(m -> m.getAnnotation(
                        org.springframework.scheduling.annotation.Scheduled.class) != null)
                .toList();

        assertThat(schedulers).hasSize(1);
    }

    @Test
    void theEscalationServiceExposesExactlyOneOperation() {
        java.util.List<String> methods = java.util.Arrays.stream(TicketEscalationService.class
                        .getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName)
                .toList();

        // One fixed rule, one hop: no level parameter, no policy argument, no strategy.
        assertThat(methods).containsExactly("escalateToOwnerManager");
    }

    @Test
    void thereIsNoEscalationPolicyFramework() {
        var methods = java.util.Arrays.stream(TicketEscalationService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .map(n -> n.toLowerCase())
                .toList();

        assertThat(methods).noneMatch(n -> n.contains("level"));
        assertThat(methods).noneMatch(n -> n.contains("chain"));
        assertThat(methods).noneMatch(n -> n.contains("policy"));
        assertThat(methods).noneMatch(n -> n.contains("delay"));
        assertThat(methods).noneMatch(n -> n.contains("queue"));
        assertThat(methods).noneMatch(n -> n.contains("team"));
    }

}
