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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.entity.Activity;
import com.shivang.crm.modules.activity.mapper.ActivityMapper;
import com.shivang.crm.modules.activity.mapper.ActivityMapperImpl;
import com.shivang.crm.modules.activity.repository.ActivityRepository;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.config.TicketSlaProperties;
import com.shivang.crm.modules.ticket.dto.TicketAssignRequest;
import com.shivang.crm.modules.ticket.dto.TicketCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketMessageCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketStatusChangeRequest;
import com.shivang.crm.modules.ticket.dto.TicketUpdateRequest;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketMessage;
import com.shivang.crm.modules.ticket.entity.TicketMessageVisibility;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.mapper.TicketMapperImpl;
import com.shivang.crm.modules.ticket.mapper.TicketMessageMapperImpl;
import com.shivang.crm.modules.ticket.repository.TicketMessageRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.PermissionDeniedException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.1-P5: the Ticket timeline.
 *
 * <p>Two halves that meet here: the domain services that write timeline entries, and
 * the read that returns them. The read half is the interesting one, because a
 * timeline is only correct if it is never wider than the ticket it describes.
 */
class TicketTimelineTest {

    private static final Instant NOW = Instant.parse("2026-02-01T10:00:00Z");

    private ActivityRepository activityRepository;
    private ActivityService activityService;
    private TicketRepository ticketRepository;
    private TicketMessageRepository messageRepository;
    private RecordScopeGuard scopeGuard;
    private CustomerRelationshipValidator validator;
    private CanonicalCrmEventPublisher publisher;
    private TicketService ticketService;
    private TicketMessageService messageService;
    private TicketTimelineService timelineService;

    private UUID tenantId;
    private UUID tenantB;
    private UUID callerId;
    private UUID otherUserId;
    private UUID accountId;
    private UUID ticketId;

    @BeforeEach
    void setup() {
        activityRepository = mock(ActivityRepository.class);
        activityService = mock(ActivityService.class);
        ticketRepository = mock(TicketRepository.class);
        messageRepository = mock(TicketMessageRepository.class);
        scopeGuard = mock(RecordScopeGuard.class);
        validator = mock(CustomerRelationshipValidator.class);
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

        TicketSlaProperties sla = new TicketSlaProperties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        ticketService = new TicketService(ticketRepository, new TicketMapperImpl(), scopeGuard,
                validator, activityService, publisher, sla, clock);
        messageService = new TicketMessageService(ticketRepository, messageRepository,
                new TicketMessageMapperImpl(), scopeGuard, validator, activityService, publisher, clock);
        timelineService = new TicketTimelineService(activityRepository, new ActivityMapperImpl(),
                ticketService);

        tenantId = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        callerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
    }

    // --- helpers ---

    private Ticket liveTicket(TicketStatus status, UUID owner) {
        Ticket t = new Ticket();
        t.setId(ticketId);
        t.setTenantId(tenantId);
        t.setAccountId(accountId);
        t.setSubject("Cannot log in");
        t.setStatus(status);
        t.setOwnerId(owner);
        t.setCreatedBy(owner);
        t.setDeleted(false);
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.of(t));
        return t;
    }

    private void stubTimeline(Activity... activities) {
        when(activityRepository
                .findByEntityTypeAndEntityIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(
                        anyString(), any(), any(), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(activities)));
    }

    private Activity activity(String type) {
        Activity a = new Activity();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setEntityType("TICKET");
        a.setEntityId(ticketId);
        a.setActivityType(type);
        a.setDescription(type);
        a.setActorType("USER");
        a.setDeleted(false);
        return a;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> activityMetadata() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(activityService).logActivity(any(), any(), any(), any(), any(), any(), captor.capture());
        return captor.getValue();
    }

    private String activityTypeLogged() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(activityService).logActivity(any(), any(), any(), captor.capture(), any(), any(), any());
        return captor.getValue();
    }

    // ================= Activity creation =================

    @Test
    void ticketCreationRecordsACreatedActivity() {
        ticketService.createTicket(tenantId, callerId, TicketCreateRequest.builder()
                .accountId(accountId).subject("Cannot log in").build());

        assertThat(activityTypeLogged()).isEqualTo("TICKET_CREATED");
    }

    @Test
    void theCreatedActivityIsRecordedAgainstTheTicket() {
        ticketService.createTicket(tenantId, callerId, TicketCreateRequest.builder()
                .accountId(accountId).subject("Cannot log in").build());

        ArgumentCaptor<UUID> entityId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        verify(activityService).logActivity(eq(tenantId), entityId.capture(), entityType.capture(),
                any(), any(), eq(callerId), any());

        assertThat(entityType.getValue()).isEqualTo("TICKET");
        assertThat(entityId.getValue()).isNotNull();
    }

    @Test
    void theCreatedActivityCarriesCustomerIdentityButNoContent() {
        ticketService.createTicket(tenantId, callerId, TicketCreateRequest.builder()
                .accountId(accountId).subject("Secret customer detail")
                .description("Bank account 12345").build());

        Map<String, Object> metadata = activityMetadata();
        assertThat(metadata).containsEntry("accountId", accountId.toString());
        // The subject is in the description field only; the body never reaches metadata.
        assertThat(metadata.values())
                .noneMatch(v -> String.valueOf(v).contains("12345"));
    }

    @Test
    void aMeaningfulUpdateRecordsAnUpdatedActivity() {
        liveTicket(TicketStatus.OPEN, callerId);

        ticketService.updateTicket(ticketId, tenantId, callerId, TicketUpdateRequest.builder()
                .subject("Renamed").build());

        assertThat(activityTypeLogged()).isEqualTo("TICKET_UPDATED");
    }

    @Test
    void aNoOpUpdateRecordsNoActivity() {
        liveTicket(TicketStatus.OPEN, callerId);

        ticketService.updateTicket(ticketId, tenantId, callerId, TicketUpdateRequest.builder().build());

        verifyNoInteractions(activityService);
    }

    @Test
    void resubmittingIdenticalValuesRecordsNoActivity() {
        Ticket ticket = liveTicket(TicketStatus.OPEN, callerId);
        String subject = ticket.getSubject();

        ticketService.updateTicket(ticketId, tenantId, callerId, TicketUpdateRequest.builder()
                .subject(subject).build());

        verifyNoInteractions(activityService);
    }

    @Test
    void aStatusTransitionRecordsExactlyOneStatusActivity() {
        liveTicket(TicketStatus.OPEN, callerId);

        ticketService.changeStatus(ticketId, tenantId, callerId,
                TicketStatusChangeRequest.builder().status(TicketStatus.IN_PROGRESS).build());

        verify(activityService, times(1)).logActivity(any(), any(), eq("TICKET"),
                eq("TICKET_STATUS_CHANGED"), any(), eq(callerId), any());
    }

    @Test
    void aSameStatusRequestRecordsNoDuplicateActivity() {
        // The idempotent path returns before any write, so no second status entry.
        liveTicket(TicketStatus.OPEN, callerId);

        ticketService.changeStatus(ticketId, tenantId, callerId,
                TicketStatusChangeRequest.builder().status(TicketStatus.OPEN).build());

        verifyNoInteractions(activityService);
    }

    @Test
    void aStatusActivityDescribesTheTransition() {
        liveTicket(TicketStatus.OPEN, callerId);

        ticketService.changeStatus(ticketId, tenantId, callerId,
                TicketStatusChangeRequest.builder().status(TicketStatus.RESOLVED).build());

        assertThat(activityMetadata()).containsEntry("status", "RESOLVED");
    }

    @Test
    void ownerReassignmentRecordsExactlyOneOwnerActivity() {
        liveTicket(TicketStatus.OPEN, callerId);

        ticketService.assignTicket(ticketId, tenantId, callerId,
                TicketAssignRequest.builder().ownerId(otherUserId).build());

        verify(activityService, times(1)).logActivity(any(), any(), eq("TICKET"),
                eq("TICKET_OWNER_CHANGED"), any(), eq(callerId), any());
    }

    @Test
    void aSameOwnerAssignmentRecordsNoActivity() {
        liveTicket(TicketStatus.OPEN, callerId);

        ticketService.assignTicket(ticketId, tenantId, callerId,
                TicketAssignRequest.builder().ownerId(callerId).build());

        verifyNoInteractions(activityService);
    }

    @Test
    void messageCreationRecordsExactlyOneMessageActivity() {
        liveTicket(TicketStatus.OPEN, callerId);

        messageService.createMessage(ticketId, tenantId, callerId,
                TicketMessageCreateRequest.builder().body("Agent replied").build());

        verify(activityService, times(1)).logActivity(any(), any(), eq("TICKET"),
                eq("TICKET_MESSAGE_CREATED"), any(), eq(callerId), any());
    }

    @Test
    void aMessageActivityCarriesTheMessageNotTheBody() {
        liveTicket(TicketStatus.OPEN, callerId);

        messageService.createMessage(ticketId, tenantId, callerId,
                TicketMessageCreateRequest.builder()
                        .body("Customer bank details 98765").visibility(TicketMessageVisibility.INTERNAL)
                        .build());

        Map<String, Object> metadata = activityMetadata();
        assertThat(metadata)
                .containsEntry("visibility", "INTERNAL")
                .containsKey("messageId")
                .containsKey("authorUserId");
        // The message body stays in ticket_messages, not in timeline metadata.
        assertThat(metadata.values())
                .noneMatch(v -> String.valueOf(v).contains("98765"));
    }

    @Test
    void aMessageActivityIsAttributedToTheTicketNotTheMessage() {
        liveTicket(TicketStatus.OPEN, callerId);

        messageService.createMessage(ticketId, tenantId, callerId,
                TicketMessageCreateRequest.builder().body("Note").build());

        ArgumentCaptor<UUID> entityId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        verify(activityService).logActivity(any(), entityId.capture(), entityType.capture(),
                any(), any(), any(), any());

        // Otherwise the entry would never surface on the ticket timeline.
        assertThat(entityType.getValue()).isEqualTo("TICKET");
        assertThat(entityId.getValue()).isEqualTo(ticketId);
    }

    @Test
    void editingAMessageDoesNotRecordACreationActivity() {
        liveTicket(TicketStatus.OPEN, callerId);
        UUID messageId = UUID.randomUUID();
        TicketMessage existing = new TicketMessage();
        existing.setId(messageId);
        existing.setTenantId(tenantId);
        existing.setTicketId(ticketId);
        existing.setAuthorUserId(callerId);
        existing.setBody("Original");
        existing.setVisibility(TicketMessageVisibility.INTERNAL);
        existing.setDeleted(false);
        when(messageRepository.findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, tenantId, ticketId)).thenReturn(Optional.of(existing));

        messageService.updateMessage(ticketId, messageId, tenantId, callerId,
                com.shivang.crm.modules.ticket.dto.TicketMessageUpdateRequest.builder()
                        .body("Edited").build());

        verify(activityService, never()).logActivity(any(), any(), any(), eq("TICKET_MESSAGE_CREATED"),
                any(), any(), any());
    }

    @Test
    void deletingAMessageDoesNotRecordACreationActivity() {
        liveTicket(TicketStatus.OPEN, callerId);
        UUID messageId = UUID.randomUUID();
        TicketMessage existing = new TicketMessage();
        existing.setId(messageId);
        existing.setTenantId(tenantId);
        existing.setTicketId(ticketId);
        existing.setAuthorUserId(callerId);
        existing.setBody("Original");
        existing.setVisibility(TicketMessageVisibility.INTERNAL);
        existing.setDeleted(false);
        when(messageRepository.findByIdAndTenantIdAndTicketIdAndDeletedFalse(
                messageId, tenantId, ticketId)).thenReturn(Optional.of(existing));

        messageService.deleteMessage(ticketId, messageId, tenantId, callerId);

        verify(activityService, never()).logActivity(any(), any(), any(), eq("TICKET_MESSAGE_CREATED"),
                any(), any(), any());
    }

    @Test
    void aFailedOperationRecordsNoActivity() {
        liveTicket(TicketStatus.OPEN, callerId);
        org.mockito.Mockito.doThrow(new BusinessException("OWNER_NOT_FOUND", "foreign"))
                .when(validator).requireTenantOwner(any(), any());

        catchThrowable(() -> ticketService.assignTicket(ticketId, tenantId, callerId,
                TicketAssignRequest.builder().ownerId(otherUserId).build()));

        // The activity shares the mutation's transaction, so a rejected write leaves
        // no timeline entry behind.
        verifyNoInteractions(activityService);
    }

    @Test
    void closingATicketRecordsTheStatusActivity() {
        liveTicket(TicketStatus.OPEN, callerId);

        ticketService.changeStatus(ticketId, tenantId, callerId,
                TicketStatusChangeRequest.builder().status(TicketStatus.RESOLVED).build());
        ticketService.changeStatus(ticketId, tenantId, callerId,
                TicketStatusChangeRequest.builder().status(TicketStatus.CLOSED).build());

        // Two real transitions, so two entries: the timeline records what happened,
        // not that something happened.
        verify(activityService, times(2)).logActivity(any(), any(), eq("TICKET"),
                eq("TICKET_STATUS_CHANGED"), any(), eq(callerId), any());
    }

    // ================= SLA SYSTEM activity =================

    @Test
    void anSlaBreachRecordsASystemActivity() {
        TicketSlaBreachService breach = new TicketSlaBreachService(ticketRepository, publisher,
                activityService, mock(TicketEscalationService.class),
                Clock.fixed(NOW.plus(Duration_days(2)), ZoneOffset.UTC));
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setTenantId(tenantId);
        ticket.setAccountId(accountId);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setOwnerId(callerId);
        ticket.setCreatedBy(callerId);
        ticket.setDeleted(false);
        ticket.setResponseDueAt(NOW.minus(Duration_days(2)));
        ticket.setResolutionDueAt(NOW.plus(Duration_days(1)));
        when(ticketRepository.markSlaBreachedIfNotBreached(ticketId, tenantId)).thenReturn(1);

        breach.markBreached(ticket, NOW.plus(Duration_days(2)));

        verify(activityService).logSystemActivity(eq(tenantId), eq(ticketId), eq("TICKET"),
                eq("TICKET_SLA_BREACHED"), anyString(), eq("TICKET_SLA_LIFECYCLE"), any());
    }

    @Test
    void anSlaBreachActivityHasNoPerformedUser() {
        // activities.performed_by is nullable and actor_type carries SYSTEM, so the
        // scheduler is not attributed to an invented user.
        TicketSlaBreachService breach = new TicketSlaBreachService(ticketRepository, publisher,
                activityService, mock(TicketEscalationService.class), Clock.fixed(NOW, ZoneOffset.UTC));
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setTenantId(tenantId);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setDeleted(false);
        ticket.setResponseDueAt(NOW.minus(Duration_days(1)));
        ticket.setResolutionDueAt(NOW.minus(Duration_days(1)));
        when(ticketRepository.markSlaBreachedIfNotBreached(ticketId, tenantId)).thenReturn(1);

        breach.markBreached(ticket, NOW);

        verify(activityService, never()).logActivity(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aLosingBreachWorkerRecordsNoActivity() {
        TicketSlaBreachService breach = new TicketSlaBreachService(ticketRepository, publisher,
                activityService, mock(TicketEscalationService.class), Clock.fixed(NOW, ZoneOffset.UTC));
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setTenantId(tenantId);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setDeleted(false);
        ticket.setResponseDueAt(NOW.minus(Duration_days(1)));
        ticket.setResolutionDueAt(NOW.minus(Duration_days(1)));
        when(ticketRepository.markSlaBreachedIfNotBreached(ticketId, tenantId)).thenReturn(0);

        breach.markBreached(ticket, NOW);

        verifyNoInteractions(activityService);
    }

    @Test
    void repeatedSchedulerRunsRecordOneBreachActivity() {
        TicketSlaBreachService breach = new TicketSlaBreachService(ticketRepository, publisher,
                activityService, mock(TicketEscalationService.class), Clock.fixed(NOW, ZoneOffset.UTC));
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setTenantId(tenantId);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setDeleted(false);
        ticket.setResponseDueAt(NOW.minus(Duration_days(1)));
        ticket.setResolutionDueAt(NOW.minus(Duration_days(1)));
        when(ticketRepository.markSlaBreachedIfNotBreached(ticketId, tenantId))
                .thenReturn(1)
                .thenReturn(0);

        breach.markBreached(ticket, NOW);
        breach.markBreached(ticket, NOW);

        verify(activityService, times(1)).logSystemActivity(any(), any(), any(), any(), any(),
                any(), any());
    }

    private static java.time.Duration Duration_days(int days) {
        return java.time.Duration.ofDays(days);
    }

    // ================= Timeline read =================

    @Test
    void theTimelineReturnsTicketActivities() {
        liveTicket(TicketStatus.OPEN, callerId);
        stubTimeline(activity("TICKET_CREATED"), activity("TICKET_STATUS_CHANGED"));

        Page<ActivityResponse> page = timelineService.getTicketTimeline(
                ticketId, tenantId, callerId, 0, 20);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getActivityType()).isEqualTo("TICKET_CREATED");
    }

    @Test
    void theTimelineQueryIsScopedToTheTicketEntityAndTenant() {
        liveTicket(TicketStatus.OPEN, callerId);
        stubTimeline();

        timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20);

        // entityType AND entityId AND tenantId: entityId alone would cross tenants.
        verify(activityRepository)
                .findByEntityTypeAndEntityIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(
                        eq("TICKET"), eq(ticketId), eq(tenantId), any(Pageable.class));
    }

    @Test
    void theTimelineExcludesSoftDeletedActivities() {
        liveTicket(TicketStatus.OPEN, callerId);
        stubTimeline();

        timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20);

        verify(activityRepository)
                .findByEntityTypeAndEntityIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(
                        anyString(), any(), any(), any(Pageable.class));
    }

    @Test
    void anEmptyTimelineIsAnEmptyPage() {
        liveTicket(TicketStatus.OPEN, callerId);
        stubTimeline();

        Page<ActivityResponse> page = timelineService.getTicketTimeline(
                ticketId, tenantId, callerId, 0, 20);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void theTimelineIsOrderedNewestFirst() {
        liveTicket(TicketStatus.OPEN, callerId);
        stubTimeline();

        timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(activityRepository)
                .findByEntityTypeAndEntityIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(
                        anyString(), any(), any(), captor.capture());

        var order = captor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.isDescending()).isTrue();
    }

    @Test
    void timelinePaginationIsClamped() {
        liveTicket(TicketStatus.OPEN, callerId);
        stubTimeline();

        timelineService.getTicketTimeline(ticketId, tenantId, callerId, -4, 500_000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(activityRepository)
                .findByEntityTypeAndEntityIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(
                        anyString(), any(), any(), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void timelinePaginationDefaultsToTwenty() {
        liveTicket(TicketStatus.OPEN, callerId);
        stubTimeline();

        timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(activityRepository)
                .findByEntityTypeAndEntityIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(
                        anyString(), any(), any(), captor.capture());

        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    // ================= Timeline authorization =================

    @Test
    void theTimelineResolvesTheTicketBeforeQueryingActivities() {
        liveTicket(TicketStatus.OPEN, callerId);
        stubTimeline();

        timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20);

        // Read access is checked first, so a denial cannot leak activity existence.
        verify(scopeGuard).requireScope(tenantId, callerId, "ticket", "read");
    }

    @Test
    void theTimelineRequestsTheReadScopeNotAnActivityScope() {
        liveTicket(TicketStatus.OPEN, callerId);
        stubTimeline();

        timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20);

        // A timeline entry is visible exactly when its ticket is: no activity scope.
        verify(scopeGuard, never()).requireScope(any(), any(), eq("activity"), anyString());
    }

    @Test
    void aCallerOutsideTicketScopeCannotReadTheTimeline() {
        liveTicket(TicketStatus.OPEN, otherUserId);
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("read"))).thenReturn("OWN");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());

        code(catchThrowable(() -> timelineService.getTicketTimeline(
                ticketId, tenantId, callerId, 0, 20)), "SCOPE_DENIED");
    }

    @Test
    void aDeniedCallerNeverReachesTheActivityQuery() {
        liveTicket(TicketStatus.OPEN, otherUserId);
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("read"))).thenReturn("OWN");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());

        catchThrowable(() -> timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20));

        verify(activityRepository, never())
                .findByEntityTypeAndEntityIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(
                        anyString(), any(), any(), any(Pageable.class));
    }

    @Test
    void teamScopeCannotWidenTheTimeline() {
        liveTicket(TicketStatus.OPEN, otherUserId);
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("read"))).thenReturn("TEAM");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());

        code(catchThrowable(() -> timelineService.getTicketTimeline(
                ticketId, tenantId, callerId, 0, 20)), "SCOPE_DENIED");
    }

    @Test
    void allScopeMayReadTheTimeline() {
        liveTicket(TicketStatus.OPEN, otherUserId);
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("read"))).thenReturn("ALL");
        stubTimeline(activity("TICKET_CREATED"));

        Page<ActivityResponse> page = timelineService.getTicketTimeline(
                ticketId, tenantId, callerId, 0, 20);

        assertThat(page.getContent()).hasSize(1);
    }

    // ================= Tenant isolation =================

    @Test
    void aCrossTenantTicketCannotBeTimelined() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> timelineService.getTicketTimeline(
                ticketId, tenantId, callerId, 0, 20)), "TICKET_NOT_FOUND");
    }

    @Test
    void aCrossTenantTicketNeverReachesTheActivityQuery() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        catchThrowable(() -> timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20));

        verifyNoInteractions(activityRepository);
    }

    @Test
    void aCrossTenantRejectionIsIndistinguishableFromAMissingTicket() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());
        Throwable crossTenant = catchThrowable(() -> timelineService.getTicketTimeline(
                ticketId, tenantId, callerId, 0, 20));

        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantB))
                .thenReturn(Optional.empty());
        Throwable missing = catchThrowable(() -> timelineService.getTicketTimeline(
                ticketId, tenantB, callerId, 0, 20));

        assertThat(crossTenant.getMessage()).isEqualTo(missing.getMessage());
    }

    @Test
    void aDeletedTicketCannotBeTimelined() {
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> timelineService.getTicketTimeline(
                ticketId, tenantId, callerId, 0, 20)), "TICKET_NOT_FOUND");
    }

    @Test
    void theActivityQueryAlwaysCarriesTheTenant() {
        // No finder may retrieve activities by entity id alone.
        var tenantAwareFinders = java.util.Arrays.stream(ActivityRepository.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(n -> n.startsWith("find"))
                .toList();

        assertThat(tenantAwareFinders).allMatch(n -> n.contains("TenantId"));
    }

    @Test
    void anotherTicketsActivitiesCannotAppear() {
        liveTicket(TicketStatus.OPEN, callerId);
        // The tenant and entity id are both bound to the requested ticket.
        stubTimeline(activity("TICKET_CREATED"));

        timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20);

        verify(activityRepository)
                .findByEntityTypeAndEntityIdAndTenantIdAndDeletedFalseOrderByCreatedAtDesc(
                        eq("TICKET"), eq(ticketId), eq(tenantId), any(Pageable.class));
    }

    // ================= Reads never write =================

    @Test
    void readingTheTimelineWritesNoActivity() {
        liveTicket(TicketStatus.OPEN, callerId);
        stubTimeline(activity("TICKET_CREATED"));

        timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20);

        verifyNoInteractions(activityService);
    }

    @Test
    void repeatedReadsWriteNothing() {
        liveTicket(TicketStatus.OPEN, callerId);
        stubTimeline(activity("TICKET_CREATED"));

        timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20);
        timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20);

        verifyNoInteractions(activityService);
    }

    @Test
    void aDeniedReadWritesNothing() {
        liveTicket(TicketStatus.OPEN, otherUserId);
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), eq("read"))).thenReturn("OWN");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(scopeGuard).assertWithinOwnerCreatorScope(anyString(), any(), any(), any(), any());

        catchThrowable(() -> timelineService.getTicketTimeline(ticketId, tenantId, callerId, 0, 20));

        verifyNoInteractions(activityService);
    }

    @Test
    void theTimelineServiceHasNoWritePath() {
        // Structurally read-only: no logActivity/logSystemActivity on this class.
        assertThat(java.util.Arrays.stream(TicketTimelineService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .map(n -> n.toLowerCase()))
                .noneMatch(n -> n.contains("log") || n.contains("create") || n.contains("write"));

        assertThat(java.util.Arrays.stream(TicketTimelineService.class.getDeclaredFields())
                .map(f -> f.getType().getSimpleName()))
                .doesNotContain("ActivityService");
    }

    @Test
    void theTimelineServiceIsReadOnlyTransactional() throws Exception {
        var method = java.util.Arrays.stream(TicketTimelineService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("getTicketTimeline"))
                .findFirst()
                .orElseThrow();

        var transactional = method.getAnnotation(
                org.springframework.transaction.annotation.Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void theTimelineWritesNothingOfItsOwnOnTheActivityTable() {
        // Ticket activities are written only by the domain services that own each
        // mutation, in that mutation's transaction.
        assertThat(java.util.Arrays.stream(ActivityRepository.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(n -> n.startsWith("delete"));
    }

    @Test
    void onlyOneActivityWritePathExistsPerTicketOperation() {
        // TicketService owns the ticket lifecycle entries, TicketMessageService the
        // message entries, TicketSlaBreachService the breach entry. No projection
        // from CanonicalCrmEvent, so one operation cannot write two entries.
        assertThat(java.util.Arrays.stream(TicketService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(n -> n.toLowerCase().contains("timeline"));
        assertThat(java.util.Arrays.stream(TicketSlaBreachService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(n -> n.toLowerCase().contains("timeline"));
    }

    @Test
    void noEventToActivityProjectionWasIntroduced() {
        // The canonical event publisher must not acquire an activity-writing path.
        assertThat(java.util.Arrays.stream(
                        com.shivang.crm.shared.event.CanonicalCrmEventPublisher.class
                                .getDeclaredFields())
                .map(f -> f.getType().getSimpleName()))
                .doesNotContain("ActivityService");
    }

    @Test
    void theTimelineResponseCarriesTheSystemActor() {
        Activity system = activity("TICKET_SLA_BREACHED");
        system.setActorType("SYSTEM");
        system.setActorSource("TICKET_SLA_LIFECYCLE");
        system.setPerformedBy(null);

        ActivityResponse mapped = new ActivityMapperImpl().toResponse(system);

        // Without this a SYSTEM entry is indistinguishable from a missing performer.
        assertThat(mapped.getActorType()).isEqualTo("SYSTEM");
        assertThat(mapped.getActorSource()).isEqualTo("TICKET_SLA_LIFECYCLE");
        assertThat(mapped.getPerformedBy()).isNull();
    }

    @Test
    void theTimelineExposesNoCustomerContentFields() {
        assertThat(java.util.Arrays.stream(ActivityResponse.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("accountId", "contactId", "ownerId", "tenantId");
    }

    private void code(Throwable thrown, String expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
