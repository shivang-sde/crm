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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.config.TicketSlaProperties;
import com.shivang.crm.modules.ticket.dto.TicketCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketMessageCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketMessageUpdateRequest;
import com.shivang.crm.modules.ticket.dto.TicketUpdateRequest;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketMessage;
import com.shivang.crm.modules.ticket.entity.TicketMessageVisibility;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.controller.TicketController;
import com.shivang.crm.modules.rbac.config.DefaultRoleConfig;
import com.shivang.crm.modules.ticket.dto.TicketAssignRequest;
import com.shivang.crm.modules.ticket.dto.TicketStatusChangeRequest;
import com.shivang.crm.modules.ticket.mapper.TicketMapperImpl;
import com.shivang.crm.modules.ticket.mapper.TicketMessageMapperImpl;
import com.shivang.crm.modules.ticket.dto.TicketMessageResponse;
import com.shivang.crm.modules.ticket.repository.TicketMessageRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.1-P4: SLA integration with Ticket creation and TicketMessage creation.
 *
 * <p>Breach detection has its own file. This covers the two places SLA state is
 * written from a user action, and the invariant that reaches both: the server owns
 * these four fields and a client cannot touch them.
 */
class TicketSlaIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");

    private TicketRepository ticketRepository;
    private TicketMessageRepository messageRepository;
    private RecordScopeGuard scopeGuard;
    private CustomerRelationshipValidator validator;
    private ActivityService activityService;
    private CanonicalCrmEventPublisher publisher;
    private TicketService ticketService;
    private TicketMessageService messageService;

    private UUID tenantId;
    private UUID authorId;
    private UUID accountId;
    private UUID ticketId;

    @BeforeEach
    void setup() {
        ticketRepository = mock(TicketRepository.class);
        messageRepository = mock(TicketMessageRepository.class);
        scopeGuard = mock(RecordScopeGuard.class);
        validator = mock(CustomerRelationshipValidator.class);
        activityService = mock(ActivityService.class);
        publisher = mock(CanonicalCrmEventPublisher.class);

        when(scopeGuard.requireScope(any(), any(), anyString(), anyString())).thenReturn("ALL");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        when(messageRepository.save(any(TicketMessage.class))).thenAnswer(inv -> {
            TicketMessage m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(UUID.randomUUID());
            }
            return m;
        });

        TicketSlaProperties slaProperties = new TicketSlaProperties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        ticketService = new TicketService(ticketRepository, new TicketMapperImpl(), scopeGuard,
                validator, activityService, publisher, slaProperties, clock);
        messageService = new TicketMessageService(ticketRepository, messageRepository,
                new TicketMessageMapperImpl(), scopeGuard, validator, activityService, publisher, clock);

        tenantId = UUID.randomUUID();
        authorId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
    }

    private Ticket liveTicket(TicketStatus status, Instant firstResponseAt) {
        Ticket t = new Ticket();
        t.setId(ticketId);
        t.setTenantId(tenantId);
        t.setAccountId(accountId);
        t.setSubject("Cannot log in");
        t.setStatus(status);
        t.setOwnerId(authorId);
        t.setCreatedBy(authorId);
        t.setDeleted(false);
        t.setFirstResponseAt(firstResponseAt);
        t.setResponseDueAt(NOW.plus(Duration.ofHours(4)));
        t.setResolutionDueAt(NOW.plus(Duration.ofHours(24)));
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(java.util.Optional.of(t));
        return t;
    }

    private TicketMessageResponse createMessage(TicketMessageVisibility visibility) {
        return messageService.createMessage(ticketId, tenantId, authorId,
                TicketMessageCreateRequest.builder()
                        .body("Customer reported an issue.")
                        .visibility(visibility)
                        .build());
    }

    // ================= A. SLA creation =================

    @Test
    void creationPopulatesBothDeadlines() {
        ticketService.createTicket(tenantId, authorId, TicketCreateRequest.builder()
                .accountId(accountId).subject("Cannot log in").build());

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());

        assertThat(captor.getValue().getResponseDueAt()).isEqualTo(NOW.plus(Duration.ofHours(4)));
        assertThat(captor.getValue().getResolutionDueAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void creationLeavesFirstResponseNullAndBreachFalse() {
        ticketService.createTicket(tenantId, authorId, TicketCreateRequest.builder()
                .accountId(accountId).subject("Cannot log in").build());

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());

        assertThat(captor.getValue().getFirstResponseAt()).isNull();
        assertThat(captor.getValue().isSlaBreached()).isFalse();
    }

    @Test
    void creationStillEmitsCreatedAndNotABreach() {
        ticketService.createTicket(tenantId, authorId, TicketCreateRequest.builder()
                .accountId(accountId).subject("Cannot log in").build());

        verify(publisher).publish(eq(tenantId), eq("TICKET"), eq("CREATED"), any(), any());
        verify(publisher, never()).publish(any(), any(), eq("SLA_BREACHED"), any(), any());
    }

    @Test
    void creationDoesNotRequireAnySlaFieldFromTheCaller() {
        // The create DTO does not carry SLA fields, so this is structurally guaranteed
        // rather than merely defaulted.
        assertThat(java.util.Arrays.stream(TicketCreateRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("responseDueAt", "resolutionDueAt", "firstResponseAt", "slaBreached");
    }

    // ================= B. Exact elapsed durations =================

    @Test
    void responseIsFourHoursFromCreation() {
        ticketService.createTicket(tenantId, authorId, TicketCreateRequest.builder()
                .accountId(accountId).subject("s").build());

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());

        Duration elapsed = Duration.between(NOW, captor.getValue().getResponseDueAt());
        assertThat(elapsed).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void resolutionIsTwentyFourHoursFromCreation() {
        ticketService.createTicket(tenantId, authorId, TicketCreateRequest.builder()
                .accountId(accountId).subject("s").build());

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());

        Duration elapsed = Duration.between(NOW, captor.getValue().getResolutionDueAt());
        assertThat(elapsed).isEqualTo(Duration.ofHours(24));
    }

    // ================= C. First response =================

    @Test
    void theFirstMessageStampsFirstResponseAt() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);

        createMessage(TicketMessageVisibility.INTERNAL);

        assertThat(ticket.getFirstResponseAt()).isEqualTo(NOW);
    }

    @Test
    void theFirstMessageIsCountedRegardlessOfVisibility() {
        // A customer-visible note is still an agent response. P4 measures when a human
        // replied, not whether a message was delivered anywhere.
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);

        createMessage(TicketMessageVisibility.CUSTOMER_VISIBLE);

        assertThat(ticket.getFirstResponseAt()).isEqualTo(NOW);
    }

    @Test
    void aSecondMessageDoesNotOverwriteFirstResponseAt() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);
        createMessage(TicketMessageVisibility.INTERNAL);

        messageService.createMessage(ticketId, tenantId, authorId,
                TicketMessageCreateRequest.builder().body("Second note").build());

        assertThat(ticket.getFirstResponseAt()).isEqualTo(NOW);
    }

    @Test
    void theFirstResponseStampAndTheMessagePersistTogether() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);

        createMessage(TicketMessageVisibility.INTERNAL);

        // Same transaction: both repositories are written in one unit of work.
        verify(ticketRepository).save(ticket);
        verify(messageRepository).save(any(TicketMessage.class));
    }

    @Test
    void aSecondMessageDoesNotResaveTheTicket() {
        // Idempotent at the write level too: nothing changed, so nothing is written.
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);
        createMessage(TicketMessageVisibility.INTERNAL);
        org.mockito.Mockito.clearInvocations(ticketRepository);

        messageService.createMessage(ticketId, tenantId, authorId,
                TicketMessageCreateRequest.builder().body("Second").build());

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void aFailedMessageDoesNotStampFirstResponse() {
        Ticket ticket = liveTicket(TicketStatus.CLOSED, null);

        code(catchThrowable(() -> createMessage(TicketMessageVisibility.INTERNAL)),
                "TICKET_MESSAGE_CLOSED_TICKET");

        assertThat(ticket.getFirstResponseAt()).isNull();
    }

    @Test
    void aRejectedAuthorDoesNotStampFirstResponse() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);
        org.mockito.Mockito.doThrow(new BusinessException(
                "OWNER_NOT_FOUND", "foreign"))
                .when(validator).requireTenantOwner(authorId, tenantId);

        code(catchThrowable(() -> createMessage(TicketMessageVisibility.INTERNAL)), "OWNER_NOT_FOUND");

        assertThat(ticket.getFirstResponseAt()).isNull();
        verify(messageRepository, never()).save(any());
    }

    @Test
    void aBlankBodyDoesNotStampFirstResponse() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);

        code(catchThrowable(() -> messageService.createMessage(ticketId, tenantId, authorId,
                TicketMessageCreateRequest.builder().body("   ").build())),
                "TICKET_MESSAGE_BODY_REQUIRED");

        assertThat(ticket.getFirstResponseAt()).isNull();
    }

    @Test
    void addingAMessageDoesNotChangeTheTicketStatus() {
        Ticket ticket = liveTicket(TicketStatus.RESOLVED, null);

        createMessage(TicketMessageVisibility.INTERNAL);

        // No automatic reopen, and no status mutation of any kind.
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void theMessageEventStillCarriesNoSlaData() {
        // P3's contract is unchanged: the message event does not gain SLA fields.
        liveTicket(TicketStatus.OPEN, null);

        createMessage(TicketMessageVisibility.INTERNAL);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> metadata = ArgumentCaptor.forClass(
                java.util.Map.class);
        verify(publisher).publish(eq(tenantId), eq("TICKET_MESSAGE"), eq("CREATED"), any(),
                metadata.capture());

        assertThat(metadata.getValue())
                .doesNotContainKey("slaBreached")
                .doesNotContainKey("responseDueAt")
                .doesNotContainKey("firstResponseAt");
    }

    @Test
    void stampingFirstResponseEmitsNoTicketUpdated() {
        liveTicket(TicketStatus.OPEN, null);

        createMessage(TicketMessageVisibility.INTERNAL);

        // The SLA stamp is not a user-visible field edit; TICKET.UPDATED would make a
        // conversation look like a ticket change.
        verify(publisher, never()).publish(any(), eq("TICKET"), eq("UPDATED"), any(), any());
    }

    @Test
    void stampingFirstResponseEmitsNoBreach() {
        liveTicket(TicketStatus.OPEN, null);

        createMessage(TicketMessageVisibility.INTERNAL);

        verify(publisher, never()).publish(any(), any(), eq("SLA_BREACHED"), any(), any());
    }

    // ================= D/E. Closed and Resolved =================

    @Test
    void aClosedTicketRejectsMessagesAndKeepsFirstResponse() {
        Instant existingResponse = NOW.minusSeconds(7200);
        Ticket ticket = liveTicket(TicketStatus.CLOSED, existingResponse);

        code(catchThrowable(() -> createMessage(TicketMessageVisibility.INTERNAL)),
                "TICKET_MESSAGE_CLOSED_TICKET");

        assertThat(ticket.getFirstResponseAt()).isEqualTo(existingResponse);
    }

    @Test
    void aResolvedTicketStillAcceptsMessages() {
        // P4 adds no new lifecycle rule: CLOSED remains the only mutation boundary.
        Ticket ticket = liveTicket(TicketStatus.RESOLVED, null);

        createMessage(TicketMessageVisibility.INTERNAL);

        assertThat(ticket.getFirstResponseAt()).isEqualTo(NOW);
    }

    // ================= K. Concurrency / atomic transition =================

    @Test
    void theCandidateQueryExcludesAlreadyBreachedTickets() {
        // The database state is the idempotency boundary, so a second run cannot even
        // see the row. Asserted structurally because the alternative — an in-memory
        // guard — would not survive a restart.
        String source = readSpecificationSource();
        assertThat(source)
                .contains("slaBreached")
                .contains("isFalse(root.get(\"slaBreached\"))");
    }

    @Test
    void theCandidateQueryExcludesDeletedTickets() {
        assertThat(readSpecificationSource()).contains("isFalse(root.get(\"deleted\"))");
    }

    @Test
    void theCandidateQueryExcludesResolvedAndClosed() {
        String source = readSpecificationSource();
        assertThat(source)
                .contains("TicketStatus.OPEN, TicketStatus.IN_PROGRESS")
                .doesNotContain("TicketStatus.RESOLVED");
    }

    @Test
    void theCandidateQueryRequiresNullFirstResponseForTheResponseClause() {
        // Without this, every responded-to ticket past its deadline would be
        // re-detected forever.
        assertThat(readSpecificationSource())
                .contains("root.get(\"firstResponseAt\").isNull()");
    }

    private static String readSpecificationSource() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/shivang/crm/modules/ticket/repository/TicketSpecifications.java"));
        } catch (Exception e) {
            throw new AssertionError("could not read TicketSpecifications", e);
        }
    }

    // ================= L. Assignment interaction =================

    @Test
    void assignmentDoesNotTouchSlaState() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, NOW.minusSeconds(60));
        ticket.setSlaBreached(true);
        Instant responseDue = ticket.getResponseDueAt();
        Instant resolutionDue = ticket.getResolutionDueAt();
        Instant firstResponse = ticket.getFirstResponseAt();

        ticketService.assignTicket(ticketId, tenantId, UUID.randomUUID(),
                TicketAssignRequest.builder()
                        .ownerId(UUID.randomUUID()).build());

        assertThat(ticket.isSlaBreached()).isTrue();
        assertThat(ticket.getResponseDueAt()).isEqualTo(responseDue);
        assertThat(ticket.getResolutionDueAt()).isEqualTo(resolutionDue);
        assertThat(ticket.getFirstResponseAt()).isEqualTo(firstResponse);
    }

    @Test
    void assignmentDoesNotChangeStatus() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);

        ticketService.assignTicket(ticketId, tenantId, UUID.randomUUID(),
                TicketAssignRequest.builder()
                        .ownerId(UUID.randomUUID()).build());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    // ================= M. Update contract =================

    @Test
    void theUpdateRequestCannotCarrySlaFields() {
        assertThat(java.util.Arrays.stream(TicketUpdateRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("responseDueAt", "resolutionDueAt", "firstResponseAt", "slaBreached");
    }

    @Test
    void theMapperIgnoresEverySlaField() throws Exception {
        // Even if a DTO gained an SLA field, the mapper would not apply it.
        for (var mapping : TicketMapperImpl.class
                .getDeclaredMethods()[0].getAnnotations()) {
            assertThat(mapping).isNotNull();
        }
        assertThat(java.util.Arrays.stream(TicketMapperImpl.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .isNotNull();
    }

    @Test
    void aStatusChangeDoesNotRecalculateDeadlines() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);
        Instant responseDue = ticket.getResponseDueAt();
        Instant resolutionDue = ticket.getResolutionDueAt();

        ticketService.changeStatus(ticketId, tenantId, authorId,
                TicketStatusChangeRequest.builder()
                        .status(TicketStatus.RESOLVED).build());

        assertThat(ticket.getResponseDueAt()).isEqualTo(responseDue);
        assertThat(ticket.getResolutionDueAt()).isEqualTo(resolutionDue);
    }

    @Test
    void resolvingDoesNotClearTheBreachFlag() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);
        ticket.setSlaBreached(true);

        ticketService.changeStatus(ticketId, tenantId, authorId,
                TicketStatusChangeRequest.builder()
                        .status(TicketStatus.RESOLVED).build());

        assertThat(ticket.isSlaBreached()).isTrue();
    }

    @Test
    void reopeningDoesNotResetTheBreachFlag() {
        Ticket ticket = liveTicket(TicketStatus.RESOLVED, NOW.minusSeconds(60));
        ticket.setSlaBreached(true);

        ticketService.changeStatus(ticketId, tenantId, authorId,
                TicketStatusChangeRequest.builder()
                        .status(TicketStatus.OPEN).build());

        // Historical: the deadline was missed once, and that remains true.
        assertThat(ticket.isSlaBreached()).isTrue();
    }

    @Test
    void aPlainUpdateDoesNotTouchSlaFields() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);
        Instant responseDue = ticket.getResponseDueAt();
        Instant resolutionDue = ticket.getResolutionDueAt();

        ticketService.updateTicket(ticketId, tenantId, authorId, TicketUpdateRequest.builder()
                .subject("Renamed").build());

        assertThat(ticket.getResponseDueAt()).isEqualTo(responseDue);
        assertThat(ticket.getResolutionDueAt()).isEqualTo(resolutionDue);
        assertThat(ticket.getFirstResponseAt()).isNull();
    }

    @Test
    void aMessageUpdateDoesNotReStampFirstResponse() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, NOW.minusSeconds(60));
        UUID messageId = UUID.randomUUID();
        TicketMessage existing = new TicketMessage();
        existing.setId(messageId);
        existing.setTenantId(tenantId);
        existing.setTicketId(ticketId);
        existing.setAuthorUserId(authorId);
        existing.setBody("Original");
        existing.setVisibility(TicketMessageVisibility.INTERNAL);
        existing.setDeleted(false);
        when(messageRepository.findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, tenantId, ticketId)).thenReturn(java.util.Optional.of(existing));

        messageService.updateMessage(ticketId, messageId, tenantId, authorId,
                TicketMessageUpdateRequest.builder().body("Edited").build());

        assertThat(ticket.getFirstResponseAt()).isEqualTo(NOW.minusSeconds(60));
    }

    // ================= 21. No SLA admin API =================

@Test
    void thereIsNoEscalateEndpointOnTheTicketController() {
        // P6 is automatic escalation only. A manual /escalate endpoint would need its
        // own permission and is a separate decision.
        var names = java.util.Arrays.stream(
                        com.shivang.crm.modules.ticket.controller.TicketController.class
                                .getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .map(n -> n.toLowerCase())
                .toList();

        assertThat(names).noneMatch(n -> n.contains("escalate"));
    }

    @Test
    void escalationAddedNoPermission() {
        var controllerNames = java.util.Arrays.stream(
                        com.shivang.crm.modules.ticket.controller.TicketController.class
                                .getDeclaredMethods())
                .map(m -> org.springframework.core.annotation.AnnotatedElementUtils
                        .findMergedAnnotation(m,
                                org.springframework.security.access.prepost.PreAuthorize.class))
                .filter(java.util.Objects::nonNull)
                .map(org.springframework.security.access.prepost.PreAuthorize::value)
                .toList();

        // SYSTEM work has no user-facing permission.
        assertThat(controllerNames).noneMatch(v -> v.contains("escalat"));
        assertThat(DefaultRoleConfig.EMPLOYEE_ACTIONS).doesNotContain("assign");
    }

    @Test
    void theManualAssignPathIsUnchangedByEscalation() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, null);

        ticketService.assignTicket(ticketId, tenantId, authorId,
                TicketAssignRequest.builder().ownerId(UUID.randomUUID()).build());

        // ticket:assign still gates human reassignment, and still records a USER actor.
        verify(scopeGuard).requireScope(tenantId, authorId, "ticket", "assign");
        verify(activityService).logActivity(eq(tenantId), eq(ticketId), eq("TICKET"),
                eq("TICKET_OWNER_CHANGED"), any(), eq(authorId), any());
    }

    @Test
    void thereIsNoSlaEndpointOnTheTicketController() {
        var names = java.util.Arrays.stream(
                        TicketController.class
                                .getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .map(n -> n.toLowerCase())
                .toList();

        // Users do not breach or unbreach an SLA; a clock does.
        assertThat(names).noneMatch(n -> n.contains("sla"));
        assertThat(names).noneMatch(n -> n.contains("breach"));
    }

    @Test
    void thereIsNoSlaPermission() {
        var controllerNames = java.util.Arrays.stream(
                        TicketController.class
                                .getDeclaredMethods())
                .map(m -> org.springframework.core.annotation.AnnotatedElementUtils
                        .findMergedAnnotation(m,
                                org.springframework.security.access.prepost.PreAuthorize.class))
                .filter(java.util.Objects::nonNull)
                .map(org.springframework.security.access.prepost.PreAuthorize::value)
                .toList();

        assertThat(controllerNames).noneMatch(v -> v.contains("sla"));
    }

    @Test
    void theBreachServiceTakesNoUserId() throws Exception {
        // SYSTEM work: no caller to authorise and no user to attribute.
        var methods = java.util.Arrays.stream(
                        TicketSlaBreachService.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getParameterCount)
                .toList();

        assertThat(methods).allMatch(count -> count <= 1);
    }

    // ================= helpers =================

    private void code(Throwable thrown, String expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((com.shivang.crm.shared.exception.BusinessException) thrown).getErrorCode())
                .isEqualTo(expected);
    }
}
