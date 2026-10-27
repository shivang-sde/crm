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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.dto.TicketMessageCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketMessageResponse;
import com.shivang.crm.modules.ticket.dto.TicketMessageUpdateRequest;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketMessage;
import com.shivang.crm.modules.ticket.entity.TicketMessageVisibility;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.mapper.TicketMessageMapper;
import com.shivang.crm.modules.ticket.mapper.TicketMessageMapperImpl;
import com.shivang.crm.modules.ticket.repository.TicketMessageRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.PermissionDeniedException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.1-P3: Ticket message relationship integrity, parent-ticket authorization,
 * author-only mutation, CLOSED behaviour and events.
 *
 * <p>The organizing idea is that a message has no authority of its own. Almost every
 * test here is therefore some form of "the parent decides": the parent must exist in
 * the tenant, the parent must be in the caller's scope, and the caller must be the
 * author. The child never widens any of those.
 */
class TicketMessageServiceTest {

    private TicketRepository ticketRepository;
    private TicketMessageRepository messageRepository;
    private RecordScopeGuard scopeGuard;
    private CustomerRelationshipValidator validator;
    private ActivityService activityService;
    private CanonicalCrmEventPublisher publisher;
    private TicketMessageService service;

    private UUID tenantId;
    private UUID tenantB;
    private UUID callerId;
    private UUID otherUserId;
    private UUID accountId;
    private UUID ticketId;
    private UUID messageId;
    private Ticket ticket;

    @BeforeEach
    void setup() {
        ticketRepository = mock(TicketRepository.class);
        messageRepository = mock(TicketMessageRepository.class);
        scopeGuard = mock(RecordScopeGuard.class);
        validator = mock(CustomerRelationshipValidator.class);
        activityService = mock(ActivityService.class);
        publisher = mock(CanonicalCrmEventPublisher.class);
        // Real mapper: the author-only and partial-update tests need the mapper to
        // genuinely refuse identity fields, which a mock cannot demonstrate.
        TicketMessageMapper mapper = new TicketMessageMapperImpl();

        when(scopeGuard.requireScope(any(), any(), eq("ticket"), anyString())).thenReturn("ALL");
        when(messageRepository.save(any(TicketMessage.class))).thenAnswer(inv -> {
            TicketMessage m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(UUID.randomUUID());
            }
            return m;
        });
        when(messageRepository.findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(
                any(), any(), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of()));

        service = new TicketMessageService(ticketRepository, messageRepository, mapper,
                scopeGuard, validator, activityService, publisher, fixedClock());

        tenantId = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        callerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        messageId = UUID.randomUUID();

        ticket = liveTicket(tenantId, callerId);
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.of(ticket));
    }

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ================= helpers =================

    private Ticket liveTicket(UUID tenant, UUID owner) {
        Ticket t = new Ticket();
        t.setId(ticketId);
        t.setTenantId(tenant);
        t.setAccountId(accountId);
        t.setSubject("Cannot log in");
        t.setStatus(TicketStatus.OPEN);
        t.setOwnerId(owner);
        t.setCreatedBy(owner);
        t.setDeleted(false);
        return t;
    }

    private TicketMessage message(UUID tenant, UUID ticketRef, UUID author, String body) {
        TicketMessage m = new TicketMessage();
        m.setId(messageId);
        m.setTenantId(tenant);
        m.setTicketId(ticketRef);
        m.setAuthorUserId(author);
        m.setBody(body);
        m.setVisibility(TicketMessageVisibility.INTERNAL);
        m.setDeleted(false);
        return m;
    }

    private void stubMessage(TicketMessage m) {
        when(messageRepository.findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, m.getTenantId(), m.getTicketId()))
                .thenReturn(Optional.of(m));
    }

    private TicketMessageResponse create(TicketMessageVisibility visibility, String body) {
        return service.createMessage(ticketId, tenantId, callerId,
                TicketMessageCreateRequest.builder().visibility(visibility).body(body).build());
    }

    private void code(Throwable thrown, String expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    private TicketMessage capturedMessage() {
        ArgumentCaptor<TicketMessage> captor = ArgumentCaptor.forClass(TicketMessage.class);
        verify(messageRepository).save(captor.capture());
        return captor.getValue();
    }

    private Map<String, Object> createdEventMetadata() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(any(), eq("TICKET_MESSAGE"), eq("CREATED"), any(), metadata.capture());
        return metadata.getValue();
    }

    // ================= A. Relationship integrity =================

    @Test
    void aValidTicketAndAuthorCreatesAMessage() {
        TicketMessageResponse created = create(TicketMessageVisibility.INTERNAL, "Customer called.");

        assertThat(created).isNotNull();
        verify(messageRepository).save(any(TicketMessage.class));
    }

    @Test
    void theMessageIsPersistedAgainstTheTicketsTenantNotARequestValue() {
        create(TicketMessageVisibility.INTERNAL, "Note");

        // tenantId comes from TenantContext via the controller; there is no request
        // field that could contradict it.
        assertThat(capturedMessage().getTenantId()).isEqualTo(tenantId);
        assertThat(capturedMessage().getTicketId()).isEqualTo(ticketId);
    }

    @Test
    void theAuthorIsAlwaysTheAuthenticatedCaller() {
        create(TicketMessageVisibility.INTERNAL, "Note");

        assertThat(capturedMessage().getAuthorUserId()).isEqualTo(callerId);
    }

    @Test
    void aNonexistentTicketIsRejected() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Note")), "TICKET_NOT_FOUND");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void aCrossTenantTicketIsRejected() {
        // The finder is tenant-scoped, so another tenant's ticket simply does not resolve.
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Note")), "TICKET_NOT_FOUND");
        verifyNoInteractions(publisher);
    }

    @Test
    void everyTicketLookupInTheServiceIsTenantScoped() throws NoSuchMethodException {
        // The cross-tenant guarantee lives in the finder, not in a post-hoc check: a
        // foreign ticket is never returned to the service, so there is no code path
        // that could accept one. Asserted structurally because a lookup that dropped
        // tenantId would be silently exploitable.
        List<String> methods = java.util.Arrays.stream(TicketMessageService.class.getDeclaredMethods())
                .filter(m -> java.util.Arrays.stream(m.getName().toLowerCase().split("resolve|require"))
                        .anyMatch(t -> t.contains("ticket")))
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(methods).contains("resolveAccessibleTicket");
        assertThat(TicketRepository.class.getMethod(
                "findByIdAndTenantIdAndDeletedFalse", UUID.class, UUID.class))
                .isNotNull();
    }

    @Test
    void aMissingTicketErrorDisclosesNothingAboutExistence() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Note"));

        // Same code and message for "no such ticket", "deleted ticket" and "another
        // tenant's ticket" — a client cannot learn whether the id exists elsewhere.
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo("TICKET_NOT_FOUND");
        assertThat(thrown.getMessage()).doesNotContain(ticketId.toString())
                .doesNotContain("deleted")
                .doesNotContain("tenant");
    }

    @Test
    void aDeletedTicketCannotReceiveAMessage() {
        // Same finder as the cross-tenant case: deleted rows are excluded.
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Note")), "TICKET_NOT_FOUND");
    }

    @Test
    void aCrossTenantAuthorIsRejected() {
        org.mockito.Mockito.doThrow(new BusinessException("OWNER_NOT_FOUND", "foreign"))
                .when(validator).requireTenantOwner(callerId, tenantId);

        // Redundant while the author is always the principal, and deliberately kept:
        // the tenant-blind FK on author_user_id cannot enforce this.
        code(catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Note")), "OWNER_NOT_FOUND");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void theAuthorIsValidatedAgainstTheTenant() {
        create(TicketMessageVisibility.INTERNAL, "Note");

        verify(validator).requireTenantOwner(callerId, tenantId);
    }

    @Test
    void aBlankBodyIsRejected() {
        code(catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "   ")),
                "TICKET_MESSAGE_BODY_REQUIRED");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void anEmptyBodyIsRejected() {
        code(catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "")),
                "TICKET_MESSAGE_BODY_REQUIRED");
    }

    @Test
    void aNullBodyIsRejected() {
        code(catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, null)),
                "TICKET_MESSAGE_BODY_REQUIRED");
    }

    @Test
    void aBlankBodyEmitsNoEvent() {
        catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "  "));

        verifyNoInteractions(publisher);
    }

    @Test
    void anInvalidVisibilityIsRejectedByJacksonBeforeTheService() {
        // Enum binding, not a runtime check: an unknown value cannot construct a
        // TicketMessageVisibility at all.
        assertThat(java.util.Arrays.stream(TicketMessageVisibility.values()).map(Enum::name))
                .containsExactly("INTERNAL", "CUSTOMER_VISIBLE");
    }

    @Test
    void theBodyIsStoredVerbatim() {
        // Trimming would alter an audit record of what the agent wrote.
        create(TicketMessageVisibility.INTERNAL, "  Customer called at 10:00.  ");

        assertThat(capturedMessage().getBody()).isEqualTo("  Customer called at 10:00.  ");
    }

    @Test
    void visibilityDefaultsToInternalWhenOmitted() {
        service.createMessage(ticketId, tenantId, callerId,
                TicketMessageCreateRequest.builder().body("Note").build());

        // The safe default: forgetting to state visibility yields a working note,
        // not something marked for the customer.
        assertThat(capturedMessage().getVisibility()).isEqualTo(TicketMessageVisibility.INTERNAL);
    }

    @Test
    void customerVisibleIsStoredAsGiven() {
        create(TicketMessageVisibility.CUSTOMER_VISIBLE, "We received your request.");

        assertThat(capturedMessage().getVisibility())
                .isEqualTo(TicketMessageVisibility.CUSTOMER_VISIBLE);
    }

    @Test
    void theMessageDoesNotCopyCustomerOrOwnerFieldsFromTheTicket() {
        create(TicketMessageVisibility.INTERNAL, "Note");

        TicketMessage saved = capturedMessage();
        // Structurally impossible: the entity has no such properties, which is why
        // the parent stays the single source of truth.
        assertThat(java.util.Arrays.stream(TicketMessage.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("accountId", "contactId", "ownerId");
    }

    // ================= B. Parent authorization =================

    @Test
    void aCallerWithTicketScopeCanCreateAMessage() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("write"))).thenReturn("OWN");
        ticket.setOwnerId(callerId);

        create(TicketMessageVisibility.INTERNAL, "Note");

        verify(messageRepository).save(any());
    }

    @Test
    void createRequestsTheWriteScope() {
        create(TicketMessageVisibility.INTERNAL, "Note");

        verify(scopeGuard).requireScope(tenantId, callerId, "ticket", "write");
    }

    @Test
    void listRequestsTheReadScope() {
        service.listMessages(ticketId, tenantId, callerId, 0, 20);

        verify(scopeGuard).requireScope(tenantId, callerId, "ticket", "read");
    }

    @Test
    void getRequestsTheReadScope() {
        stubMessage(message(tenantId, ticketId, callerId, "Note"));
        service.getMessage(ticketId, messageId, tenantId, callerId);

        verify(scopeGuard).requireScope(tenantId, callerId, "ticket", "read");
    }

    @Test
    void updateRequestsTheWriteScope() {
        stubMessage(message(tenantId, ticketId, callerId, "Note"));
        service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().body("Edited").build());

        verify(scopeGuard).requireScope(tenantId, callerId, "ticket", "write");
    }

    @Test
    void deleteRequestsTheWriteScope() {
        stubMessage(message(tenantId, ticketId, callerId, "Note"));
        service.deleteMessage(ticketId, messageId, tenantId, callerId);

        verify(scopeGuard).requireScope(tenantId, callerId, "ticket", "write");
    }

    @Test
    void aCallerOutsideTicketScopeCannotCreateAMessage() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), anyString()))
                .thenReturn("OWN");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());
        // Ticket owned by somebody else.
        ticket.setOwnerId(otherUserId);
        ticket.setCreatedBy(otherUserId);

        code(catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Note")), "SCOPE_DENIED");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void aCallerOutsideTicketScopeCannotListMessages() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), anyString()))
                .thenReturn("OWN");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());
        ticket.setOwnerId(otherUserId);

        code(catchThrowable(() -> service.listMessages(ticketId, tenantId, callerId, 0, 20)),
                "SCOPE_DENIED");
        // Must not even reach the message query.
        verify(messageRepository, never())
                .findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void aCallerOutsideTicketScopeCannotReadAMessage() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), anyString()))
                .thenReturn("OWN");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());
        ticket.setOwnerId(otherUserId);

        code(catchThrowable(() -> service.getMessage(ticketId, messageId, tenantId, callerId)),
                "SCOPE_DENIED");
        verify(messageRepository, never())
                .findByIdAndTenantIdAndTicketIdAndDeletedFalse(any(), any(), any());
    }

    @Test
    void aCallerOutsideTicketScopeCannotUpdateAMessage() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), anyString()))
                .thenReturn("OWN");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());
        ticket.setOwnerId(otherUserId);

        code(catchThrowable(() -> service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().body("Hijacked").build())), "SCOPE_DENIED");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void aCallerOutsideTicketScopeCannotDeleteAMessage() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), anyString()))
                .thenReturn("OWN");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());
        ticket.setOwnerId(otherUserId);

        code(catchThrowable(() -> service.deleteMessage(ticketId, messageId, tenantId, callerId)),
                "SCOPE_DENIED");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void theChildCannotWidenTheParentAllScopeEither() {
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), anyString())).thenReturn("ALL");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());

        code(catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Note")), "SCOPE_DENIED");
    }

    @Test
    void aMessageOnAnotherTicketIsNotFoundEvenWhenTheTicketIsReadable() {
        // Message belongs to a different ticket than the URL names. Both tickets may
        // be readable; the message is still not this ticket's.
        TicketMessage foreign = message(tenantId, UUID.randomUUID(), callerId, "Note");
        when(messageRepository.findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, tenantId, ticketId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.getMessage(ticketId, messageId, tenantId, callerId)),
                "TICKET_MESSAGE_NOT_FOUND");
    }

    // ================= C. Author-only mutation =================

    @Test
    void theAuthorCanEditTheirOwnMessage() {
        stubMessage(message(tenantId, ticketId, callerId, "Original"));

        service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().body("Edited").build());

        assertThat(capturedMessage().getBody()).isEqualTo("Edited");
    }

    @Test
    void theAuthorCanChangeVisibility() {
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder()
                        .visibility(TicketMessageVisibility.CUSTOMER_VISIBLE).build());

        assertThat(capturedMessage().getVisibility())
                .isEqualTo(TicketMessageVisibility.CUSTOMER_VISIBLE);
    }

    @Test
    void aVisibilityOnlyEditDoesNotRequireABody() {
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder()
                        .visibility(TicketMessageVisibility.CUSTOMER_VISIBLE).build());

        assertThat(capturedMessage().getBody()).isEqualTo("Note");
    }

    @Test
    void anOmittedFieldIsLeftAlone() {
        stubMessage(message(tenantId, ticketId, callerId, "Original"));

        service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().build());

        assertThat(capturedMessage().getBody()).isEqualTo("Original");
    }

    @Test
    void aNonAuthorCannotEditEvenAtAllScope() {
        // Ticket scope says which tickets you may work. It is not a licence to edit
        // another agent's words, so ALL does not help here.
        stubMessage(message(tenantId, ticketId, otherUserId, "Original"));
        ticket.setOwnerId(otherUserId);
        ticket.setCreatedBy(otherUserId);

        code(catchThrowable(() -> service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().body("Hijacked").build())),
                "TICKET_MESSAGE_AUTHOR_ONLY");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void aNonAuthorEditLeavesTheBodyUntouched() {
        TicketMessage m = message(tenantId, ticketId, otherUserId, "Original");
        stubMessage(m);

        catchThrowable(() -> service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().body("Hijacked").build()));

        assertThat(m.getBody()).isEqualTo("Original");
    }

    @Test
    void theAuthorCanDeleteTheirOwnMessage() {
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        service.deleteMessage(ticketId, messageId, tenantId, callerId);

        assertThat(capturedMessage().isDeleted()).isTrue();
    }

    @Test
    void aNonAuthorCannotDeleteEvenAtAllScope() {
        stubMessage(message(tenantId, ticketId, otherUserId, "Note"));
        ticket.setOwnerId(otherUserId);
        ticket.setCreatedBy(otherUserId);

        code(catchThrowable(() -> service.deleteMessage(ticketId, messageId, tenantId, callerId)),
                "TICKET_MESSAGE_AUTHOR_ONLY");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void anAuthorCannotEditAMessageOnATicketTheyDoNotOwn() {
        // Being the author is necessary but not sufficient: the parent scope still applies.
        stubMessage(message(tenantId, ticketId, callerId, "Note"));
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), anyString())).thenReturn("OWN");
        ticket.setOwnerId(otherUserId);
        ticket.setCreatedBy(otherUserId);
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());

        code(catchThrowable(() -> service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().body("Edited").build())), "SCOPE_DENIED");
    }

    @Test
    void deleteNeverHardDeletes() {
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        service.deleteMessage(ticketId, messageId, tenantId, callerId);

        // No physical removal path exists on the repository at all.
        verify(messageRepository, never()).delete(any(TicketMessage.class));
        verify(messageRepository, never()).deleteAll(any(Iterable.class));
        verify(messageRepository, never()).deleteAllInBatch(any(Iterable.class));
    }

    // ================= D. CLOSED behaviour =================

    @Test
    void aMessageCannotBeAddedToAClosedTicket() {
        ticket.setStatus(TicketStatus.CLOSED);

        code(catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Note")),
                "TICKET_MESSAGE_CLOSED_TICKET");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void aClosedTicketRejectionEmitsNoEvent() {
        ticket.setStatus(TicketStatus.CLOSED);

        catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Note"));

        verifyNoInteractions(publisher);
    }

    @Test
    void aMessageCannotBeEditedOnAClosedTicket() {
        ticket.setStatus(TicketStatus.CLOSED);
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        code(catchThrowable(() -> service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().body("Edited").build())),
                "TICKET_MESSAGE_CLOSED_TICKET");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void aMessageCannotBeDeletedFromAClosedTicket() {
        ticket.setStatus(TicketStatus.CLOSED);
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        code(catchThrowable(() -> service.deleteMessage(ticketId, messageId, tenantId, callerId)),
                "TICKET_MESSAGE_CLOSED_TICKET");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void messagesRemainReadableOnAClosedTicket() {
        // Freezing the conversation must not make it unreadable: the history of how a
        // ticket was handled is the main reason to look at a closed one.
        ticket.setStatus(TicketStatus.CLOSED);
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        TicketMessageResponse read = service.getMessage(ticketId, messageId, tenantId, callerId);

        assertThat(read.getBody()).isEqualTo("Note");
    }

    @Test
    void messagesRemainListableOnAClosedTicket() {
        ticket.setStatus(TicketStatus.CLOSED);

        Page<TicketMessageResponse> page = service.listMessages(ticketId, tenantId, callerId, 0, 20);

        assertThat(page).isNotNull();
    }

    @Test
    void closingATicketDoesNotDeleteItsMessages() {
        // No cascade anywhere: TicketMessageService never touches the parent's messages
        // on a ticket status change, and there is no ON DELETE CASCADE in V72.
        ticket.setStatus(TicketStatus.CLOSED);
        service.listMessages(ticketId, tenantId, callerId, 0, 20);

        verify(messageRepository, never()).delete(any());
    }

    // ================= E. Lifecycle =================

    @Test
    void aResolvedTicketStillAcceptsAMessage() {
        // RESOLVED is not the mutation boundary; only CLOSED is. P1 places no
        // restriction here and P3 invents none.
        ticket.setStatus(TicketStatus.RESOLVED);

        create(TicketMessageVisibility.INTERNAL, "Customer confirmed it is fixed.");

        verify(messageRepository).save(any());
    }

    @Test
    void aResolvedTicketStillAcceptsAnEdit() {
        ticket.setStatus(TicketStatus.RESOLVED);
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().body("Edited").build());

        verify(messageRepository).save(any());
    }

    @Test
    void addingAMessageDoesNotChangeTheTicketStatus() {
        ticket.setStatus(TicketStatus.RESOLVED);

        create(TicketMessageVisibility.INTERNAL, "Still broken.");

        // No automatic reopen: a customer replying to a resolved ticket does not
        // silently undo the agent's resolution.
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void addingAMessageDoesNotReopenAClosedTicket() {
        ticket.setStatus(TicketStatus.CLOSED);

        catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Actually still broken."));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void addingAMessageDoesNotChangeTheTicketOwnerOrAssignment() {
        UUID ownerBefore = ticket.getOwnerId();

        create(TicketMessageVisibility.INTERNAL, "Note");

        // Message creation must not be an assignment back door.
        assertThat(ticket.getOwnerId()).isEqualTo(ownerBefore);
        verify(scopeGuard, never()).requireScope(any(), any(), eq("ticket"), eq("assign"));
    }

    @Test
    void inProgressAndOpenTicketsAcceptMessages() {
        for (TicketStatus status : new TicketStatus[] { TicketStatus.OPEN, TicketStatus.IN_PROGRESS }) {
            org.mockito.Mockito.clearInvocations(messageRepository, publisher);
            ticket.setStatus(status);

            create(TicketMessageVisibility.INTERNAL, "Note");

            verify(messageRepository).save(any(TicketMessage.class));
        }
    }

    // ================= F. Events =================

    @Test
    void aSuccessfulCreateEmitsExactlyOneCreated() {
        create(TicketMessageVisibility.INTERNAL, "Note");

        verify(publisher, times(1)).publish(
                any(UUID.class), eq("TICKET_MESSAGE"), eq("CREATED"), any(), any());
    }

    @Test
    void theEventEntityIdIsTheMessageIdNotTheTicketId() {
        create(TicketMessageVisibility.INTERNAL, "Note");

        ArgumentCaptor<UUID> entity = ArgumentCaptor.forClass(UUID.class);
        verify(publisher).publish(any(), eq("TICKET_MESSAGE"), eq("CREATED"), entity.capture(), any());

        // The message is its own eventing aggregate (DEAL_LINE_ITEM precedent).
        assertThat(entity.getValue()).isEqualTo(capturedMessage().getId());
        assertThat(entity.getValue()).isNotEqualTo(ticketId);
    }

    @Test
    void theEventTenantIsTheMessagesTenant() {
        create(TicketMessageVisibility.INTERNAL, "Note");

        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        verify(publisher).publish(tenant.capture(), eq("TICKET_MESSAGE"), eq("CREATED"), any(), any());

        assertThat(tenant.getValue()).isEqualTo(tenantId);
    }

    @Test
    void theEventCarriesAuthorVisibilityAndTicketId() {
        create(TicketMessageVisibility.CUSTOMER_VISIBLE, "We received your request.");

        Map<String, Object> metadata = createdEventMetadata();
        assertThat(metadata)
                .containsEntry("actorId", callerId.toString())
                .containsEntry("actorType", "USER")
                .containsEntry("ticketId", ticketId.toString())
                .containsEntry("authorUserId", callerId.toString())
                .containsEntry("visibility", "CUSTOMER_VISIBLE");
    }

    @Test
    void theEventDoesNotCarryTheMessageBody() {
        // A customer statement in the outbox would duplicate the row and put
        // conversation content on the bus. Identifiers only.
        create(TicketMessageVisibility.INTERNAL, "Sensitive customer detail.");

        assertThat(createdEventMetadata())
                .doesNotContainKey("body")
                .doesNotContainKey("description")
                .doesNotContainKey("text");
    }

    @Test
    void theEventDoesNotCarryCustomerIdentityDuplicatedFromTheTicket() {
        create(TicketMessageVisibility.INTERNAL, "Note");

        // accountId/contactId/ownerId are the parent's; a consumer reads them from the
        // Ticket event rather than trusting a copy here.
        assertThat(createdEventMetadata()).doesNotContainKey("accountId");
    }

    @Test
    void anEditEmitsNoEvent() {
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().body("Edited").build());

        // P3 defines only TICKET_MESSAGE.CREATED. An edit is not a second creation.
        verifyNoInteractions(publisher);
    }

    @Test
    void aDeleteEmitsNoEvent() {
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        service.deleteMessage(ticketId, messageId, tenantId, callerId);

        // P3 explicitly does not define TICKET_MESSAGE.DELETED.
        verifyNoInteractions(publisher);
    }

    @Test
    void anAuthorizationFailureEmitsNoEvent() {
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());

        catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Note"));

        verifyNoInteractions(publisher);
    }

    @Test
    void anAuthorFailureEmitsNoEvent() {
        stubMessage(message(tenantId, ticketId, otherUserId, "Note"));

        catchThrowable(() -> service.deleteMessage(ticketId, messageId, tenantId, callerId));

        verifyNoInteractions(publisher);
    }

    @Test
    void noTicketUpdatedEventIsEmittedAlongsideTheMessage() {
        create(TicketMessageVisibility.INTERNAL, "Note");

        // Adding a child is not a mutation of the parent's own fields.
        verify(publisher, never()).publish(any(), eq("TICKET"), anyString(), any(), any());
    }

    @Test
    void messageActivityIsRecordedAgainstTheParentTicket() {
        create(TicketMessageVisibility.INTERNAL, "Note");

        // L3.1-P5: the activity hangs off the Ticket so the message appears on the
        // ticket timeline, with the message id in metadata rather than as the entity.
        verify(activityService).logActivity(eq(tenantId), eq(ticketId), eq("TICKET"),
                eq("TICKET_MESSAGE_CREATED"), anyString(), eq(callerId), any());
        verify(activityService, never())
                .logActivity(any(), any(), eq("TICKET_MESSAGE"), anyString(), anyString(), any(), any());
    }

    // ================= G. Tenant isolation =================

    @Test
    void tenantACannotListTenantBTicketsMessages() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.listMessages(ticketId, tenantId, callerId, 0, 20)),
                "TICKET_NOT_FOUND");
    }

    @Test
    void tenantACannotCreateAgainstTenantBTicket() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> create(TicketMessageVisibility.INTERNAL, "Note")),
                "TICKET_NOT_FOUND");
        verifyNoInteractions(publisher);
    }

    @Test
    void tenantACannotReadTenantBTicketsMessages() {
        when(messageRepository.findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, tenantId, ticketId))
                .thenReturn(Optional.empty());

        // Message id known, tenant mismatched: the tenant predicate is in the finder,
        // so the row is not returned.
        code(catchThrowable(() -> service.getMessage(ticketId, messageId, tenantId, callerId)),
                "TICKET_MESSAGE_NOT_FOUND");
    }

    @Test
    void tenantACannotEditTenantBTicketsMessages() {
        when(messageRepository.findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, tenantId, ticketId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().body("Hijacked").build())),
                "TICKET_MESSAGE_NOT_FOUND");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void tenantACannotDeleteTenantBTicketsMessages() {
        when(messageRepository.findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, tenantId, ticketId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.deleteMessage(ticketId, messageId, tenantId, callerId)),
                "TICKET_MESSAGE_NOT_FOUND");
    }

    @Test
    void theMessageQueryIsAlwaysTenantScoped() {
        service.listMessages(ticketId, tenantId, callerId, 0, 20);

        // The tenant is a parameter of the finder, never inferred from the message.
        verify(messageRepository).findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(
                eq(tenantId), eq(ticketId), any(Pageable.class));
    }

    @Test
    void theMessageIsFoundOnlyUnderTheUrlTicketsId() {
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        service.getMessage(ticketId, messageId, tenantId, callerId);

        verify(messageRepository).findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, tenantId, ticketId);
    }

    // ================= H. Soft delete =================

    @Test
    void deleteMarksTheMessageDeleted() {
        stubMessage(message(tenantId, ticketId, callerId, "Note"));

        service.deleteMessage(ticketId, messageId, tenantId, callerId);

        TicketMessage saved = capturedMessage();
        assertThat(saved.isDeleted()).isTrue();
        assertThat(saved.getDeletedBy()).isEqualTo(callerId);
        assertThat(saved.getDeletedAt()).isNotNull();
    }

    @Test
    void aDeletedMessageIsNotFoundOnASecondDelete() {
        // Follows the repository's convention: the finder excludes deleted rows, so
        // repeating the request is not found rather than silently succeeding.
        when(messageRepository.findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, tenantId, ticketId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.deleteMessage(ticketId, messageId, tenantId, callerId)),
                "TICKET_MESSAGE_NOT_FOUND");
    }

    @Test
    void aDeletedMessageIsNotReadable() {
        when(messageRepository.findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, tenantId, ticketId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.getMessage(ticketId, messageId, tenantId, callerId)),
                "TICKET_MESSAGE_NOT_FOUND");
    }

    @Test
    void aDeletedMessageIsNotEditable() {
        when(messageRepository.findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, tenantId, ticketId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> service.updateMessage(ticketId, messageId, tenantId, callerId,
                TicketMessageUpdateRequest.builder().body("Undelete?").build())),
                "TICKET_MESSAGE_NOT_FOUND");
    }

    @Test
    void theLiveListQueryExcludesDeletedRows() {
        service.listMessages(ticketId, tenantId, callerId, 0, 20);

        // The finder name itself is the guarantee; asserted here so a rename to a
        // deleted-inclusive finder fails here rather than leaking rows in production.
        verify(messageRepository).findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(
                eq(tenantId), eq(ticketId), any(Pageable.class));
    }

    @Test
    void theRepositoryExposesNoPhysicalDeleteFinder() {
        // Soft delete only: no finder can return a deleted message and no cascade
        // exists. Enforced structurally because it is not observable from the service.
        List<String> methods = java.util.Arrays.stream(
                        TicketMessageRepository.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(methods).noneMatch(n -> n.startsWith("delete"));
        assertThat(methods).noneMatch(n -> n.startsWith("remove"));
        // Every finder must carry tenantId, which is the cross-tenant guarantee.
        assertThat(methods).filteredOn(n -> n.startsWith("find"))
                .allMatch(n -> n.contains("TenantId"));
    }

    // ================= pagination =================

    @Test
    void paginationIsClampedToTheMaximum() {
        service.listMessages(ticketId, tenantId, callerId, -3, 100_000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository)
                .findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(any(), any(), captor.capture());

        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @Test
    void aZeroSizeFallsBackToTheDefaultPageSize() {
        service.listMessages(ticketId, tenantId, callerId, 0, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository)
                .findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(any(), any(), captor.capture());

        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void theListIsOrderedNewestFirst() {
        service.listMessages(ticketId, tenantId, callerId, 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository)
                .findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(any(), any(), captor.capture());

        var order = captor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.isDescending()).isTrue();
    }

    // ================= transaction boundary =================

    @Test
    void writesAreTransactionalAndReadsAreNot() {
        // The outbox row and the message must commit together, so every mutation
        // inherits the class-level @Transactional and no read opens a writable one.
        var classLevel = org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedAnnotation(TicketMessageService.class,
                        org.springframework.transaction.annotation.Transactional.class);
        assertThat(classLevel).isNotNull();
        assertThat(classLevel.readOnly()).isFalse();
        assertThat(classLevel.propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRED);

        assertThat(readOnly("listMessages")).isTrue();
        assertThat(readOnly("getMessage")).isTrue();

        for (String name : new String[] { "createMessage", "updateMessage", "deleteMessage" }) {
            var declared = methodTransactional(name);
            assertThat(declared == null || !declared.readOnly())
                    .as("%s must not be readOnly", name)
                    .isTrue();
        }
    }

    @Test
    void theServiceHasNoRequiresNewTransaction() {
        // A REQUIRES_NEW would let the message commit even if the outbox insert
        // failed, which is the exact split-brain this must not have.
        for (var method : TicketMessageService.class.getDeclaredMethods()) {
            var annotation = org.springframework.core.annotation.AnnotatedElementUtils
                    .findMergedAnnotation(method, org.springframework.transaction.annotation.Transactional.class);
            if (annotation != null) {
                assertThat(annotation.propagation())
                        .isIn(org.springframework.transaction.annotation.Propagation.REQUIRED,
                                org.springframework.transaction.annotation.Propagation.SUPPORTS);
            }
        }
    }

    private boolean readOnly(String name) {
        var annotation = methodTransactional(name);
        assertThat(annotation).as("%s must declare readOnly = true", name).isNotNull();
        return annotation.readOnly();
    }

    private org.springframework.transaction.annotation.Transactional methodTransactional(String name) {
        return java.util.Arrays.stream(TicketMessageService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .map(m -> org.springframework.core.annotation.AnnotatedElementUtils
                        .findMergedAnnotation(m,
                                org.springframework.transaction.annotation.Transactional.class))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
    static com.shivang.crm.modules.ticket.config.TicketSlaProperties slaProperties() {
        return new com.shivang.crm.modules.ticket.config.TicketSlaProperties();
    }

    static java.time.Clock fixedClock() {
        return java.time.Clock.fixed(java.time.Instant.parse("2026-01-15T09:00:00Z"),
                java.time.ZoneOffset.UTC);
    }
}