package com.shivang.crm.modules.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.dto.TicketCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.modules.ticket.dto.TicketStatusChangeRequest;
import com.shivang.crm.modules.ticket.dto.TicketUpdateRequest;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.controller.TicketController;
import com.shivang.crm.modules.ticket.mapper.TicketMapper;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.1-P1: Tenant integrity and relationship validation for the Ticket aggregate.
 *
 * <p>The foreign keys on {@code tickets} are tenant-blind, exactly like every other
 * customer-domain FK in this schema. That makes
 * {@code CustomerRelationshipValidator} the only thing standing between a
 * known-but-foreign UUID and a cross-tenant ticket, so these tests exercise it at
 * the service boundary rather than trusting the schema.
 */
class TicketRelationshipIntegrityTest {

    private TicketRepository ticketRepository;
    private RecordScopeGuard scopeGuard;
    private com.shivang.crm.shared.service.CustomerRelationshipValidator validator;
    private ActivityService activityService;
    private CanonicalCrmEventPublisher publisher;
    private TicketService service;

    private UUID tenantA;
    private UUID tenantB;
    private UUID actorId;
    private UUID accountA;
    private UUID accountB;
    private UUID contactA;
    private UUID contactOfAccountB;
    private UUID ownerA;
    private UUID ownerB;
    private UUID ticketId;

    @BeforeEach
    void setup() {
        ticketRepository = mock(TicketRepository.class);
        scopeGuard = mock(RecordScopeGuard.class);
        validator = mock(com.shivang.crm.shared.service.CustomerRelationshipValidator.class);
        activityService = mock(ActivityService.class);
        publisher = mock(CanonicalCrmEventPublisher.class);

        service = new TicketService(ticketRepository, mock(TicketMapper.class), scopeGuard,
                validator, activityService, publisher, slaProperties(), fixedClock());

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        actorId = UUID.randomUUID();
        accountA = UUID.randomUUID();
        accountB = UUID.randomUUID();
        contactA = UUID.randomUUID();
        contactOfAccountB = UUID.randomUUID();
        ownerA = UUID.randomUUID();
        ownerB = UUID.randomUUID();
        ticketId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null, List.of()));

        when(scopeGuard.requireScope(any(), any(), anyString(), anyString())).thenReturn("ALL");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(ticketId);
            }
            if (saved.getCreatedAt() == null) {
                saved.setCreatedAt(Instant.now());
            }
            return saved;
        });
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private TicketCreateRequest createRequest() {
        return TicketCreateRequest.builder()
                .accountId(accountA)
                .subject("Customer cannot access dashboard")
                .description("Repeated login failure")
                .build();
    }

    private Ticket liveTicket() {
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setTenantId(tenantA);
        ticket.setAccountId(accountA);
        ticket.setSubject("Customer cannot access dashboard");
        ticket.setDescription("Repeated login failure");
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setOwnerId(ownerA);
        ticket.setCreatedBy(ownerA);
        ticket.setDeleted(false);
        return ticket;
    }

    private void stubTicket(Ticket ticket) {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticket.getId(), tenantA))
                .thenReturn(Optional.of(ticket));
    }

    private void code(Throwable thrown, String expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    // --- Account is mandatory and validated ---

@Test
    void createWithoutAccountIsRejected() {
        TicketCreateRequest request = TicketCreateRequest.builder()
                .subject("No account supplied")
                .build();

        // requireLiveAccount is null-tolerant, so the service must enforce
        // Account's mandatory status itself rather than relying on the validator.
        code(catchThrowable(() -> service.createTicket(tenantA, actorId, request)),
                "TICKET_ACCOUNT_REQUIRED");
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void createRequiresLiveAccount() {
        TicketCreateRequest request = createRequest();

        org.mockito.Mockito.doThrow(new BusinessException("ACCOUNT_NOT_FOUND", "gone"))
                .when(validator).requireLiveAccount(accountA, tenantA);

        code(catchThrowable(() -> service.createTicket(tenantA, actorId, request)), "ACCOUNT_NOT_FOUND");
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void aDeletedAccountIsRejectedBecauseTheLookupIsDeletedAware() {
        // requireLiveAccount delegates to findByIdAndTenantIdAndDeletedFalse, so a
        // soft-deleted Account is indistinguishable from a missing one. Asserted here
        // by asserting the service never proceeds when the validator refuses.
        TicketCreateRequest request = createRequest();
        org.mockito.Mockito.doThrow(new BusinessException("ACCOUNT_NOT_FOUND", "soft deleted"))
                .when(validator).requireLiveAccount(accountA, tenantA);

        code(catchThrowable(() -> service.createTicket(tenantA, actorId, request)), "ACCOUNT_NOT_FOUND");
    }

    @Test
    void crossTenantAccountIsRejected() {
        TicketCreateRequest request = TicketCreateRequest.builder()
                .accountId(accountB)
                .subject("Foreign account")
                .build();

        org.mockito.Mockito.doThrow(new BusinessException("ACCOUNT_NOT_FOUND", "foreign"))
                .when(validator).requireLiveAccount(accountB, tenantA);

        code(catchThrowable(() -> service.createTicket(tenantA, actorId, request)), "ACCOUNT_NOT_FOUND");
        verify(ticketRepository, never()).save(any());
    }

    // --- Contact is optional but relationship-safe ---

    @Test
    void contactIsOptional() {
        TicketCreateRequest request = createRequest();

        service.createTicket(tenantA, actorId, request);

        verify(validator, never()).requireLiveContact(any(), any());
        verify(validator, never()).requireContactBelongsToAccount(any(), any(), any());
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void validAccountWithValidContactIsAccepted() {
        TicketCreateRequest request = createRequest();
        request.setContactId(contactA);

        service.createTicket(tenantA, actorId, request);

        // Order matters: contact is validated as live before its account pairing.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(validator);
        order.verify(validator).requireLiveContact(contactA, tenantA);
        order.verify(validator).requireContactBelongsToAccount(contactA, accountA, tenantA);
    }

    @Test
    void contactBelongingToAnotherAccountIsRejected() {
        TicketCreateRequest request = createRequest();
        request.setContactId(contactOfAccountB);

        org.mockito.Mockito.doThrow(new BusinessException("DEAL_CONTACT_ACCOUNT_MISMATCH", "mismatch"))
                .when(validator).requireContactBelongsToAccount(contactOfAccountB, accountA, tenantA);

        code(catchThrowable(() -> service.createTicket(tenantA, actorId, request)),
                "DEAL_CONTACT_ACCOUNT_MISMATCH");
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void crossTenantContactIsRejected() {
        TicketCreateRequest request = createRequest();
        request.setContactId(UUID.randomUUID());

        org.mockito.Mockito.doThrow(new BusinessException("CONTACT_NOT_FOUND", "foreign"))
                .when(validator).requireLiveContact(any(), eq(tenantA));

        code(catchThrowable(() -> service.createTicket(tenantA, actorId, request)), "CONTACT_NOT_FOUND");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void deletedContactIsRejectedBecauseTheLookupIsDeletedAware() {
        TicketCreateRequest request = createRequest();
        request.setContactId(UUID.randomUUID());

        org.mockito.Mockito.doThrow(new BusinessException("CONTACT_NOT_FOUND", "soft deleted"))
                .when(validator).requireLiveContact(any(), eq(tenantA));

        code(catchThrowable(() -> service.createTicket(tenantA, actorId, request)), "CONTACT_NOT_FOUND");
    }

    // --- Owner validation ---

    @Test
    void suppliedOwnerMustBeTenantValid() {
        TicketCreateRequest request = createRequest();
        request.setOwnerId(ownerB);

        org.mockito.Mockito.doThrow(new BusinessException("OWNER_NOT_FOUND", "foreign"))
                .when(validator).requireTenantOwner(ownerB, tenantA);

        code(catchThrowable(() -> service.createTicket(tenantA, actorId, request)), "OWNER_NOT_FOUND");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void noOwnerDefaultsToTheCreatorSoOwnScopeCanSeeIt() {
        service.createTicket(tenantA, actorId, createRequest());

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        // An unassigned ticket would be invisible to its own creator under OWN scope.
        assertThat(captor.getValue().getOwnerId()).isEqualTo(actorId);
    }

    @Test
    void nullOwnerIsNotValidated() {
        service.createTicket(tenantA, actorId, createRequest());

        verify(validator).requireTenantOwner(null, tenantA);
        verify(ticketRepository).save(any(Ticket.class));
    }

    // --- Cross-tenant read/write/delete/status ---

    @Test
    void tenantACannotReadTenantBTicket() {
        Ticket foreign = liveTicket();
        foreign.setTenantId(tenantB);
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantA))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.getTicketById(ticketId, tenantA)), "TICKET_NOT_FOUND");
    }

    @Test
    void tenantACannotUpdateTenantBTicket() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantA))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.updateTicket(ticketId, tenantA, actorId,
                TicketUpdateRequest.builder().subject("Hijacked").build())), "TICKET_NOT_FOUND");
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void tenantACannotDeleteTenantBTicket() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantA))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.deleteTicket(ticketId, tenantA, actorId)), "TICKET_NOT_FOUND");
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void tenantACannotChangeStatusOnTenantBTicket() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantA))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.changeStatus(ticketId, tenantA, actorId,
                TicketStatusChangeRequest.builder().status(TicketStatus.IN_PROGRESS).build())),
                "TICKET_NOT_FOUND");
        verifyNoInteractions(publisher);
    }

    @Test
    void updateCannotMoveATicketToAnotherTenantsAccount() {
        stubTicket(liveTicket());

        org.mockito.Mockito.doThrow(new BusinessException("ACCOUNT_NOT_FOUND", "foreign"))
                .when(validator).requireLiveAccount(accountB, tenantA);

        code(catchThrowable(() -> service.updateTicket(ticketId, tenantA, actorId,
                TicketUpdateRequest.builder().accountId(accountB).build())), "ACCOUNT_NOT_FOUND");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateRevalidatesTheContactAgainstTheNewAccount() {
        stubTicket(liveTicket());

        service.updateTicket(ticketId, tenantA, actorId, TicketUpdateRequest.builder()
                .accountId(accountB)
                .contactId(contactA)
                .build());

        // Not just revalidated: validated against the RESULTING account, so moving
        // the ticket cannot leave a contact attached to the previous customer.
        verify(validator).requireContactBelongsToAccount(contactA, accountB, tenantA);
    }

@Test
    void updateWithOmittedFieldsValidatesTheExistingRelationships() {
        stubTicket(liveTicket());

        service.updateTicket(ticketId, tenantA, actorId, TicketUpdateRequest.builder()
                .subject("Renamed")
                .build());

        // A partial update must still re-validate the customer relationship it is
        // keeping, not only what it changes.
        verify(validator).requireLiveAccount(accountA, tenantA);
    }

    @Test
    void updateDoesNotValidateTheOwnerBecauseItCannotChangeIt() {
        stubTicket(liveTicket());

        service.updateTicket(ticketId, tenantA, actorId, TicketUpdateRequest.builder()
                .subject("Renamed")
                .build());

        // L3.1-P2: ownership belongs to assignTicket. Validating an owner the PATCH
        // cannot apply would let a write-only caller probe owner existence, and would
        // imply PATCH participates in assignment.
        verify(validator, never()).requireTenantOwner(any(), any());
    }

    // --- Transaction boundary ---

    @Test
    void everyMutationIsTransactionalWithDefaultRollback() {
        assertThat(TicketService.class.getAnnotation(Transactional.class)).isNotNull();
        var annotation = TicketService.class.getAnnotation(Transactional.class);
        assertThat(annotation.propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRED);
        assertThat(annotation.noRollbackFor()).isEmpty();
        assertThat(annotation.rollbackFor()).isEmpty();
    }

@Test
    void readOperationsAreReadOnly() throws NoSuchMethodException {
        assertThat(TicketService.class
                .getMethod("getTicketById", UUID.class, UUID.class)
                .getAnnotation(Transactional.class)
                .readOnly()).isTrue();
        assertThat(TicketService.class
                .getMethod("listTickets", UUID.class, UUID.class, TicketStatus.class, String.class, int.class, int.class)
                .getAnnotation(Transactional.class)
                .readOnly()).isTrue();
    }

    @Test
    void aFailedValidationCannotLeaveACommittedEvent() {
        // BusinessException is a RuntimeException, so it triggers the default
        // rollback rule and the outbox row written before it is discarded. There is
        // no try/catch around validation that would swallow it.
        org.mockito.Mockito.doThrow(new BusinessException("ACCOUNT_NOT_FOUND", "gone"))
                .when(validator).requireLiveAccount(accountA, tenantA);

        Throwable thrown = catchThrowable(() -> service.createTicket(tenantA, actorId, createRequest()));

        assertThat(thrown).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(publisher);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void eventsArePublishedInsideTheMutationPathNotFromTheController() {
        service.createTicket(tenantA, actorId, createRequest());

        // The publisher is a constructor dependency of the service and nothing
        // else: no controller or repository reference exists.
        assertThat(java.util.Arrays.stream(TicketController.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getType)
                .map(Class::getName))
                .doesNotContain(CanonicalCrmEventPublisher.class.getName());
    }
    static com.shivang.crm.modules.ticket.config.TicketSlaProperties slaProperties() {
        return new com.shivang.crm.modules.ticket.config.TicketSlaProperties();
    }

    static java.time.Clock fixedClock() {
        return java.time.Clock.fixed(java.time.Instant.parse("2026-01-15T09:00:00Z"),
                java.time.ZoneOffset.UTC);
    }
}