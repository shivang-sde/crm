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
import com.shivang.crm.modules.ticket.dto.TicketAssignRequest;
import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.modules.ticket.dto.TicketUpdateRequest;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.mapper.TicketMapper;
import com.shivang.crm.modules.ticket.mapper.TicketMapperImpl;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.PermissionDeniedException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.1-P2: Ticket assignment.
 *
 * <p>The invariant this file exists to protect is that ownership has exactly one
 * door. Everything else follows from it: {@code ticket:assign} is distinct from
 * {@code ticket:write}, PATCH is closed to owner changes, a same-owner request is a
 * silent success rather than an error or a phantom event, and a cross-tenant
 * target owner is rejected before anything is written.
 */
class TicketAssignmentTest {

    private TicketRepository ticketRepository;
    private RecordScopeGuard scopeGuard;
    private CustomerRelationshipValidator validator;
    private ActivityService activityService;
    private CanonicalCrmEventPublisher publisher;
    private TicketService service;

    private UUID tenantId;
    private UUID actorId;
    private UUID accountId;
    private UUID ticketId;
    private UUID ownerA;
    private UUID ownerB;
    private Ticket ticket;

    @BeforeEach
    void setup() {
        ticketRepository = mock(TicketRepository.class);
        scopeGuard = mock(RecordScopeGuard.class);
        validator = mock(CustomerRelationshipValidator.class);
        activityService = mock(ActivityService.class);
        publisher = mock(CanonicalCrmEventPublisher.class);
        // Real mapper: the PATCH-closes-ownership test needs to observe that the
        // mapper genuinely cannot write ownerId, which a mock cannot demonstrate.
        TicketMapper mapper = new TicketMapperImpl();

        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        // Default: the caller may do everything. Individual tests narrow this.
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), anyString())).thenReturn("ALL");

        service = new TicketService(ticketRepository, mapper, scopeGuard, validator,
                activityService, publisher, slaProperties(), fixedClock());

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        ownerA = UUID.randomUUID();
        ownerB = UUID.randomUUID();

        ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setTenantId(tenantId);
        ticket.setAccountId(accountId);
        ticket.setSubject("Cannot log in");
        ticket.setDescription("Password reset loop");
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setOwnerId(ownerA);
        ticket.setCreatedBy(ownerA);
        ticket.setDeleted(false);
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.of(ticket));
    }

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // --- helpers ---

    private TicketResponse assignTo(UUID newOwnerId) {
        return service.assignTicket(ticketId, tenantId, actorId,
                TicketAssignRequest.builder().ownerId(newOwnerId).build());
    }

    private void code(Throwable thrown, String expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    private Map<String, Object> ownerChangedMetadata() {
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq(tenantId), eq("TICKET"), eventType.capture(),
                eq(ticketId), metadata.capture());
        assertThat(eventType.getValue()).isEqualTo("OWNER_CHANGED");
        return metadata.getValue();
    }

    // --- permission is distinct from write ---

    @Test
    void assignmentRequiresTicketAssignNotWrite() {
        assignTo(ownerB);

        // The whole point of a separate permission: this must be 'assign'.
        verify(scopeGuard).requireScope(tenantId, actorId, "ticket", "assign");
        verify(scopeGuard, never()).requireScope(any(), any(), eq("ticket"), eq("write"));
    }

    @Test
    void aCallerWithOnlyWriteCannotAssign() {
        // Mirrors the real evaluator: ticket:write held, ticket:assign absent.
        when(scopeGuard.requireScope(eq(tenantId), eq(actorId), eq("ticket"), eq("assign")))
                .thenThrow(new PermissionDeniedException("PERMISSION_DENIED", "no ticket:assign"));

        code(catchThrowable(() -> assignTo(ownerB)), "PERMISSION_DENIED");

        // Fail closed: rejected before the ticket is even loaded.
        verify(ticketRepository, never()).findByIdAndTenantIdAndDeletedFalse(any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void aDeniedAssignPermissionPreventsAnyOwnerValidation() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("assign")))
                .thenThrow(new PermissionDeniedException("PERMISSION_DENIED", "no"));

        catchThrowable(() -> assignTo(ownerB));

        // Authorization first: do not let an unauthorized caller probe whether a
        // user UUID exists in this tenant.
        verifyNoInteractions(validator);
    }

    // --- real reassignment ---

    @Test
    void reassignmentChangesTheOwner() {
        assignTo(ownerB);

        assertThat(ticket.getOwnerId()).isEqualTo(ownerB);
    }

    @Test
    void reassignmentSetsUpdatedByButNotCreatedBy() {
        assignTo(ownerB);

        assertThat(ticket.getUpdatedBy()).isEqualTo(actorId);
        // createdBy is provenance of the ticket, not of its current owner.
        assertThat(ticket.getCreatedBy()).isEqualTo(ownerA);
    }

    @Test
    void aRealReassignmentEmitsExactlyOneOwnerChanged() {
        assignTo(ownerB);

        verify(publisher, times(1)).publish(
                any(UUID.class), eq("TICKET"), eq("OWNER_CHANGED"), any(), any());
    }

    @Test
    void ownerChangedCarriesTheTransition() {
        assignTo(ownerB);

        Map<String, Object> metadata = ownerChangedMetadata();
        assertThat(metadata)
                .containsEntry("previousOwnerId", ownerA.toString())
                .containsEntry("newOwnerId", ownerB.toString());
    }

    @Test
    void ownerChangedIsKeyedOnTheTicketTenantAndEntity() {
        assignTo(ownerB);

        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> entity = ArgumentCaptor.forClass(UUID.class);
        verify(publisher).publish(tenant.capture(), eq("TICKET"), eq("OWNER_CHANGED"),
                entity.capture(), any());

        // The ticket's own tenant, never a caller-supplied or ambient value.
        assertThat(tenant.getValue()).isEqualTo(tenantId);
        assertThat(entity.getValue()).isEqualTo(ticketId);
    }

    @Test
    void ownerChangedCarriesActorAndCustomerContext() {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication
                        .UsernamePasswordAuthenticationToken(actorId.toString(), null, List.of()));

        assignTo(ownerB);

        Map<String, Object> metadata = ownerChangedMetadata();
        assertThat(metadata)
                .containsEntry("actorId", actorId.toString())
                .containsEntry("actorType", "USER")
                .containsEntry("accountId", accountId.toString());
    }

    @Test
    void ownerChangedIsWrittenToTheActivityLog() {
        assignTo(ownerB);

        verify(activityService).logActivity(eq(tenantId), eq(ticketId), eq("TICKET"),
                eq("TICKET_OWNER_CHANGED"), anyString(), eq(actorId), any());
    }

    // --- same-owner assignment is an idempotent no-op ---

    @Test
    void assigningTheCurrentOwnerSucceeds() {
        // Not an error: a retried request is normal.
        assertThat(assignTo(ownerA)).isNotNull();
    }

    @Test
    void assigningTheCurrentOwnerLeavesTheOwnerUnchanged() {
        assignTo(ownerA);
        assertThat(ticket.getOwnerId()).isEqualTo(ownerA);
    }

    @Test
    void assigningTheCurrentOwnerEmitsNothing() {
        assignTo(ownerA);
        verifyNoInteractions(publisher);
    }

    @Test
    void aNoOpAssignmentDoesNotTouchTheDatabase() {
        assignTo(ownerA);

        // Not even a save: nothing changed, so nothing needs writing.
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void aNoOpAssignmentDoesNotRevalidateTheExistingOwner() {
        // The current owner is already known good; re-checking it could only fail
        // for unrelated reasons (e.g. the owner was deactivated after assignment).
        assignTo(ownerA);

        verifyNoInteractions(validator);
    }

    @Test
    void aNoOpAssignmentWritesNoActivity() {
        assignTo(ownerA);
        verifyNoInteractions(activityService);
    }

    // --- owner validation ---

    @Test
    void theTargetOwnerIsValidatedAgainstTheTenant() {
        assignTo(ownerB);

        // Reuses the shared mechanism; the FK on owner_user_id is tenant-blind, so
        // this is the only real cross-tenant defence.
        verify(validator).requireTenantOwner(ownerB, tenantId);
    }

    @Test
    void anUnknownOwnerIsRejected() {
        org.mockito.Mockito.doThrow(new BusinessException("OWNER_NOT_FOUND", "unknown"))
                .when(validator).requireTenantOwner(ownerB, tenantId);

        code(catchThrowable(() -> assignTo(ownerB)), "OWNER_NOT_FOUND");

        assertThat(ticket.getOwnerId()).isEqualTo(ownerA);
        verifyNoInteractions(publisher);
    }

    @Test
    void aCrossTenantOwnerIsRejected() {
        // requireTenantOwner is what makes this fail; the tenant-blind FK would not.
        org.mockito.Mockito.doThrow(new BusinessException("OWNER_NOT_FOUND", "foreign user"))
                .when(validator).requireTenantOwner(ownerB, tenantId);

        code(catchThrowable(() -> assignTo(ownerB)), "OWNER_NOT_FOUND");

        assertThat(ticket.getOwnerId()).isEqualTo(ownerA);
    }

    @Test
    void aFailedValidationLeavesNoPersistedChange() {
        org.mockito.Mockito.doThrow(new BusinessException("OWNER_NOT_FOUND", "unknown"))
                .when(validator).requireTenantOwner(ownerB, tenantId);

        catchThrowable(() -> assignTo(ownerB));

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void aFailedValidationLeavesNoOutboxEntry() {
        org.mockito.Mockito.doThrow(new BusinessException("OWNER_NOT_FOUND", "unknown"))
                .when(validator).requireTenantOwner(ownerB, tenantId);

        catchThrowable(() -> assignTo(ownerB));

        verifyNoInteractions(publisher);
    }

    // --- record scope ---

    @Test
    void ownScopeCanAssignATicketItOwns() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("assign"))).thenReturn("OWN");
        ticket.setOwnerId(actorId);
        ticket.setCreatedBy(actorId);

        assignTo(ownerB);

        verify(scopeGuard).assertWithinOwnerCreatorScope("OWN", tenantId, actorId, actorId, actorId);
        assertThat(ticket.getOwnerId()).isEqualTo(ownerB);
    }

    @Test
    void ownScopeCannotAssignSomebodyElsesTicket() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("assign"))).thenReturn("OWN");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());

        code(catchThrowable(() -> assignTo(ownerB)), "SCOPE_DENIED");

        assertThat(ticket.getOwnerId()).isEqualTo(ownerA);
        verifyNoInteractions(publisher);
    }

    @Test
    void scopeIsEvaluatedAgainstTheOwnerBeforeTheChange() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("assign"))).thenReturn("OWN");

        assignTo(ownerB);

        // ownerA (the PRE-change owner) must be the argument. Passing ownerB would
        // let a caller assign their way into a record they cannot otherwise reach.
        verify(scopeGuard).assertWithinOwnerCreatorScope("OWN", tenantId, actorId, ownerA, ownerA);
    }

    @Test
    void teamScopeCanAssignWithinDirectReports() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("assign"))).thenReturn("TEAM");

        assignTo(ownerB);

        verify(scopeGuard).assertWithinOwnerCreatorScope("TEAM", tenantId, actorId, ownerA, ownerA);
        assertThat(ticket.getOwnerId()).isEqualTo(ownerB);
    }

    @Test
    void teamScopeCannotAssignOutsideItsScope() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("assign"))).thenReturn("TEAM");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());

        code(catchThrowable(() -> assignTo(ownerB)), "SCOPE_DENIED");
    }

    @Test
    void allScopeCanAssignAnyTicketInTheTenant() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("assign"))).thenReturn("ALL");

        assignTo(ownerB);

        assertThat(ticket.getOwnerId()).isEqualTo(ownerB);
    }

    @Test
    void allScopeCannotReachAnotherTenantsTicket() {
        // The critical non-bypass: ALL widens rows WITHIN a tenant.
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("assign"))).thenReturn("ALL");
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> assignTo(ownerB)), "TICKET_NOT_FOUND");
        verifyNoInteractions(publisher);
    }

    @Test
    void assignPermissionAloneDoesNotBypassRecordScope() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("assign"))).thenReturn("ALL");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());

        // Holding ticket:assign is necessary but not sufficient.
        code(catchThrowable(() -> assignTo(ownerB)), "SCOPE_DENIED");
    }

    // --- deleted tickets ---

    @Test
    void aDeletedTicketCannotBeAssigned() {
        // The finder is deleted-aware, so a deleted ticket simply does not resolve.
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> assignTo(ownerB)), "TICKET_NOT_FOUND");
    }

    @Test
    void aDeletedTicketIsNotResurrectedByAssignment() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        catchThrowable(() -> assignTo(ownerB));

        verifyNoInteractions(publisher);
        verify(ticketRepository, never()).save(any());
    }

    // --- PATCH cannot change ownership ---

    @Test
    void patchCannotChangeTheOwner() {
        service.updateTicket(ticketId, tenantId, actorId, TicketUpdateRequest.builder()
                .ownerId(ownerB)
                .subject("Renamed by an agent with only write")
                .build());

        // The whole point of P2: ticket:write must not reach ownership.
        assertThat(ticket.getOwnerId()).isEqualTo(ownerA);
    }

    @Test
    void patchNeverRequiresTheAssignPermission() {
        service.updateTicket(ticketId, tenantId, actorId, TicketUpdateRequest.builder()
                .ownerId(ownerB)
                .build());

        verify(scopeGuard, never()).requireScope(any(), any(), eq("ticket"), eq("assign"));
    }

    @Test
    void patchDoesNotRevalidateAnIgnoredOwner() {
        service.updateTicket(ticketId, tenantId, actorId, TicketUpdateRequest.builder()
                .ownerId(ownerB)
                .build());

        // Validation must not run for a value that is discarded — otherwise a PATCH
        // could still be used to probe owner existence.
        verify(validator, never()).requireTenantOwner(eq(ownerB), any());
    }

    @Test
    void patchDoesNotEmitOwnerChanged() {
        service.updateTicket(ticketId, tenantId, actorId, TicketUpdateRequest.builder()
                .ownerId(ownerB)
                .subject("Renamed")
                .build());

        // It is a real field change, so TICKET.UPDATED is correct here...
        verify(publisher, never()).publish(any(), any(), eq("OWNER_CHANGED"), any(), any());
    }

    @Test
    void patchStillUpdatesTheFieldsItIsAllowedToChange() {
        // Closing ownership must not break the rest of PATCH.
        service.updateTicket(ticketId, tenantId, actorId, TicketUpdateRequest.builder()
                .ownerId(ownerB)
                .subject("Renamed")
                .description("New detail")
                .build());

        assertThat(ticket.getSubject()).isEqualTo("Renamed");
        assertThat(ticket.getDescription()).isEqualTo("New detail");
    }

    // --- initial owner on create is unaffected ---

    @Test
    void creationStillAcceptsAnInitialOwner() {
        service.createTicket(tenantId, actorId,
                com.shivang.crm.modules.ticket.dto.TicketCreateRequest.builder()
                        .accountId(accountId)
                        .subject("New ticket")
                        .ownerId(ownerB)
                        .build());

        verify(validator).requireTenantOwner(ownerB, tenantId);
    }

    @Test
    void creationDoesNotEmitOwnerChanged() {
        // Setting an initial owner is part of creation, not a reassignment. Do not
        // retrofit an owner-change event into POST /tickets.
        service.createTicket(tenantId, actorId,
                com.shivang.crm.modules.ticket.dto.TicketCreateRequest.builder()
                        .accountId(accountId)
                        .subject("New ticket")
                        .ownerId(ownerB)
                        .build());

        verify(publisher, never()).publish(any(), any(), eq("OWNER_CHANGED"), any(), any());
    }

    @Test
    void creationDoesNotRequireTheAssignPermission() {
        service.createTicket(tenantId, actorId,
                com.shivang.crm.modules.ticket.dto.TicketCreateRequest.builder()
                        .accountId(accountId)
                        .subject("New ticket")
                        .ownerId(ownerB)
                        .build());

        verify(scopeGuard, never()).requireScope(any(), any(), eq("ticket"), eq("assign"));
    }

    @Test
    void creationStillEmitsCreated() {
        service.createTicket(tenantId, actorId,
                com.shivang.crm.modules.ticket.dto.TicketCreateRequest.builder()
                        .accountId(accountId)
                        .subject("New ticket")
                        .build());

        verify(publisher).publish(eq(tenantId), eq("TICKET"), eq("CREATED"), any(), any());
    }

    // --- transaction boundary ---

    @Test
    void assignmentIsTransactionalSoTheOutboxSharesItsCommit() {
var annotation = java.util.Arrays.stream(TicketService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("assignTicket"))
                .map(m -> org.springframework.core.annotation.AnnotatedElementUtils
                        .findMergedAnnotation(m, org.springframework.transaction.annotation.Transactional.class))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        // Either declared here or inherited from the class-level @Transactional.
        // Either way it must not be readOnly.
        if (annotation != null) {
            assertThat(annotation.readOnly()).isFalse();
        } else {
            var classLevel = org.springframework.core.annotation.AnnotatedElementUtils
                    .findMergedAnnotation(TicketService.class,
                            org.springframework.transaction.annotation.Transactional.class);
            assertThat(classLevel).isNotNull();
            assertThat(classLevel.readOnly()).isFalse();
        }
    }

    @Test
    void assignmentDoesNotOpenItsOwnTransaction() {
        // No REQUIRES_NEW / NESTED: the owner change and the outbox row must commit
        // or roll back together.
        var annotation = java.util.Arrays.stream(TicketService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("assignTicket"))
                .map(m -> org.springframework.core.annotation.AnnotatedElementUtils
                        .findMergedAnnotation(m, org.springframework.transaction.annotation.Transactional.class))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (annotation != null) {
            assertThat(annotation.propagation())
                    .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRED);
        }
    }
    static com.shivang.crm.modules.ticket.config.TicketSlaProperties slaProperties() {
        return new com.shivang.crm.modules.ticket.config.TicketSlaProperties();
    }

    static java.time.Clock fixedClock() {
        return java.time.Clock.fixed(java.time.Instant.parse("2026-01-15T09:00:00Z"),
                java.time.ZoneOffset.UTC);
    }
}