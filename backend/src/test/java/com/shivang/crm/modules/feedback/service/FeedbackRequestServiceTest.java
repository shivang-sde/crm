package com.shivang.crm.modules.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.shivang.crm.modules.feedback.dto.FeedbackRequestCreateRequest;
import com.shivang.crm.modules.feedback.dto.FeedbackRequestResponse;
import com.shivang.crm.modules.feedback.dto.FeedbackResponse;
import com.shivang.crm.modules.feedback.dto.PublicFeedbackRequestResponse;
import com.shivang.crm.modules.feedback.dto.PublicFeedbackSubmitRequest;
import com.shivang.crm.modules.feedback.dto.PublicFeedbackSubmitResponse;
import com.shivang.crm.modules.feedback.entity.FeedbackRequest;
import com.shivang.crm.modules.feedback.entity.FeedbackRequestStatus;
import com.shivang.crm.modules.feedback.entity.FeedbackSource;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.repository.FeedbackRequestRepository;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.5: request lifecycle, single-pending policy, token handling, and anonymous
 * submission rules. These are mocked-repository tests; the SQL/mapping proof is
 * the migration contract test below.
 */
class FeedbackRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private FeedbackRequestRepository requests;
    private TicketRepository tickets;
    private FeedbackService feedbackService;
    private RecordScopeGuard scopeGuard;
    private FeedbackTokenService tokenService;
    private FeedbackRequestService service;

    private UUID tenantId;
    private UUID userId;
    private UUID ticketId;
    private UUID accountId;
    private UUID contactId;

    @BeforeEach
    void setUp() {
        requests = mock(FeedbackRequestRepository.class);
        tickets = mock(TicketRepository.class);
        feedbackService = mock(FeedbackService.class);
        scopeGuard = mock(RecordScopeGuard.class);
        tokenService = new FeedbackTokenService();
        service = new FeedbackRequestService(
                requests, tickets, feedbackService, tokenService, scopeGuard,
                Clock.fixed(NOW, ZoneOffset.UTC));

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        contactId = UUID.randomUUID();

        Ticket ticket = ticket();
        when(tickets.findByIdAndTenantIdAndDeletedFalseForUpdate(ticketId, tenantId))
                .thenReturn(Optional.of(ticket));
        when(tickets.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId))
                .thenReturn(Optional.of(ticket));
        when(scopeGuard.requireScope(any(), any(), eq("ticket"), any())).thenReturn("ALL");
        when(requests.save(any(FeedbackRequest.class))).thenAnswer(invocation -> {
            FeedbackRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            request.setCreatedAt(NOW);
            request.setUpdatedAt(NOW);
            return request;
        });
    }

    private Ticket ticket() {
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setTenantId(tenantId);
        ticket.setAccountId(accountId);
        ticket.setContactId(contactId);
        ticket.setOwnerId(userId);
        ticket.setCreatedBy(userId);
        return ticket;
    }

    private FeedbackRequest request(FeedbackRequestStatus status) {
        FeedbackRequest request = new FeedbackRequest();
        request.setId(UUID.randomUUID());
        request.setTenantId(tenantId);
        request.setOwnerId(userId);
        request.setCreatedBy(userId);
        request.setUpdatedBy(userId);
        request.setTicketId(ticketId);
        request.setAccountId(accountId);
        request.setContactId(contactId);
        request.setType(FeedbackType.CSAT);
        request.setExpiresAt(NOW.plus(FeedbackRequestService.REQUEST_TTL));
        request.setDeleted(false);
        if (status == FeedbackRequestStatus.REVOKED) {
            request.setRevokedAt(NOW.minusSeconds(60));
        }
        if (status == FeedbackRequestStatus.SUBMITTED) {
            request.setSubmittedAt(NOW.minusSeconds(60));
            request.setFeedbackId(UUID.randomUUID());
        }
        if (status == FeedbackRequestStatus.EXPIRED) {
            request.setExpiresAt(NOW.minusSeconds(60));
        }
        return request;
    }

    private String codeOf(Throwable thrown) {
        return ((BusinessException) thrown).getErrorCode();
    }

    @Test
    void createDefaultsToCsatAndReturnsTheRawTokenOnlyOnce() {
        when(requests.findLivePending(tenantId, ticketId, NOW)).thenReturn(List.of());

        FeedbackRequestResponse response = service.create(
                tenantId, userId, ticketId, FeedbackRequestCreateRequest.builder().build());

        assertThat(response.getType()).isEqualTo(FeedbackType.CSAT);
        assertThat(response.getStatus()).isEqualTo(FeedbackRequestStatus.PENDING);
        assertThat(response.getExpiresAt()).isEqualTo(NOW.plus(FeedbackRequestService.REQUEST_TTL));
        assertThat(response.getToken()).isNotBlank().hasSize(43);
        assertThat(response.getTokenPath()).isEqualTo("/feedback/" + response.getToken());

        ArgumentCaptor<FeedbackRequest> saved = ArgumentCaptor.forClass(FeedbackRequest.class);
        verify(requests).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(tokenService.hash(response.getToken()))
                .hasSize(64)
                .isNotEqualTo(response.getToken());
        assertThat(saved.getValue().getAccountId()).isEqualTo(accountId);
        assertThat(saved.getValue().getContactId()).isEqualTo(contactId);
    }

    @Test
    void createRejectsASecondLivePendingRequest() {
        when(requests.findLivePending(tenantId, ticketId, NOW))
                .thenReturn(List.of(request(FeedbackRequestStatus.PENDING)));

        assertThat(codeOf(catchThrowable(() -> service.create(
                tenantId, userId, ticketId, FeedbackRequestCreateRequest.builder().build()))))
                .isEqualTo("FEEDBACK_REQUEST_PENDING_EXISTS");
    }

    @Test
    void createRejectsAnUnscoredType() {
        assertThat(codeOf(catchThrowable(() -> service.create(
                tenantId, userId, ticketId,
                FeedbackRequestCreateRequest.builder().type(FeedbackType.COMPLAINT).build()))))
                .isEqualTo("FEEDBACK_REQUEST_TYPE_UNSUPPORTED");
    }

    @Test
    void createRefusesACrossTenantTicket() {
        when(tickets.findByIdAndTenantIdAndDeletedFalseForUpdate(ticketId, tenantId))
                .thenReturn(Optional.empty());

        assertThat(codeOf(catchThrowable(() -> service.create(
                tenantId, userId, ticketId, FeedbackRequestCreateRequest.builder().build()))))
                .isEqualTo("TICKET_NOT_FOUND");
    }

    @Test
    void listNeverReturnsTokens() {
        when(requests.findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, ticketId))
                .thenReturn(List.of(request(FeedbackRequestStatus.PENDING)));

        List<FeedbackRequestResponse> result = service.list(tenantId, userId, ticketId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getToken()).isNull();
        assertThat(result.get(0).getTokenPath()).isNull();
    }

    @Test
    void revokeIsIdempotentForAnAlreadyRevokedRequest() {
        FeedbackRequest revoked = request(FeedbackRequestStatus.REVOKED);
        when(requests.findForUpdate(revoked.getId(), tenantId, ticketId))
                .thenReturn(Optional.of(revoked));

        FeedbackRequestResponse response = service.revoke(
                tenantId, userId, ticketId, revoked.getId());

        assertThat(response.getStatus()).isEqualTo(FeedbackRequestStatus.REVOKED);
        assertThat(revoked.getUpdatedBy()).isEqualTo(userId);
    }

    @Test
    void revokeCannotTouchASubmittedResponse() {
        FeedbackRequest submitted = request(FeedbackRequestStatus.SUBMITTED);
        when(requests.findForUpdate(submitted.getId(), tenantId, ticketId))
                .thenReturn(Optional.of(submitted));

        assertThat(codeOf(catchThrowable(() -> service.revoke(
                tenantId, userId, ticketId, submitted.getId()))))
                .isEqualTo("FEEDBACK_REQUEST_NOT_REVOCABLE");
    }

    @Test
    void regenerateRevokesThePendingLinkAndIssuesANewToken() {
        FeedbackRequest pending = request(FeedbackRequestStatus.PENDING);
        pending.setTokenHash(tokenService.hash("old-token"));
        when(requests.findForUpdate(pending.getId(), tenantId, ticketId))
                .thenReturn(Optional.of(pending));
        when(requests.findLivePending(tenantId, ticketId, NOW)).thenReturn(List.of());

        FeedbackRequestResponse response = service.regenerate(
                tenantId, userId, ticketId, pending.getId());

        assertThat(pending.getRevokedAt()).isEqualTo(NOW);
        assertThat(response.getStatus()).isEqualTo(FeedbackRequestStatus.PENDING);
        assertThat(response.getToken()).isNotEqualTo("old-token");
        assertThat(response.getTokenPath()).isEqualTo("/feedback/" + response.getToken());
    }

    @Test
    void metadataReturnsTheScaleWithoutCustomerDetails() {
        FeedbackRequest pending = request(FeedbackRequestStatus.PENDING);
        pending.setTokenHash(tokenService.hash("public-token"));
        when(requests.findByTokenHashAndDeletedFalse(pending.getTokenHash()))
                .thenReturn(Optional.of(pending));

        PublicFeedbackRequestResponse metadata = service.publicMetadata("public-token");

        assertThat(metadata.getStatus()).isEqualTo(FeedbackRequestStatus.PENDING);
        assertThat(metadata.getType()).isEqualTo(FeedbackType.CSAT);
        assertThat(metadata.getMinScore()).isEqualTo(1);
        assertThat(metadata.getMaxScore()).isEqualTo(5);
    }

    @Test
    void anUnknownPublicTokenIsNotFound() {
        when(requests.findByTokenHashAndDeletedFalse(any())).thenReturn(Optional.empty());

        assertThat(codeOf(catchThrowable(() -> service.publicMetadata("missing"))))
                .isEqualTo("FEEDBACK_REQUEST_NOT_FOUND");
    }

    @Test
    void publicSubmitUsesTheRequestSnapshotAndWebsiteProvenance() {
        FeedbackRequest pending = request(FeedbackRequestStatus.PENDING);
        pending.setTokenHash(tokenService.hash("public-token"));
        when(requests.findByTokenHashAndDeletedFalseForUpdate(pending.getTokenHash()))
                .thenReturn(Optional.of(pending));
        UUID feedbackId = UUID.randomUUID();
        when(feedbackService.submitResolved(eq(tenantId), eq(userId), any()))
                .thenReturn(FeedbackResponse.builder().id(feedbackId).build());

        PublicFeedbackSubmitResponse response = service.submit(
                "public-token", PublicFeedbackSubmitRequest.builder()
                        .score(5).comment("Quick and helpful").build());

        ArgumentCaptor<FeedbackService.TrustedFeedbackSubmission> submission =
                ArgumentCaptor.forClass(FeedbackService.TrustedFeedbackSubmission.class);
        verify(feedbackService).submitResolved(eq(tenantId), eq(userId), submission.capture());

        assertThat(submission.getValue().accountId()).isEqualTo(accountId);
        assertThat(submission.getValue().contactId()).isEqualTo(contactId);
        assertThat(submission.getValue().ticketId()).isEqualTo(ticketId);
        assertThat(submission.getValue().source()).isEqualTo(FeedbackSource.WEBSITE);
        assertThat(submission.getValue().externalReference())
                .isEqualTo("feedback-request:" + pending.getId());
        assertThat(submission.getValue().score()).isEqualTo(5);

        assertThat(response.getStatus()).isEqualTo(FeedbackRequestStatus.SUBMITTED);
        assertThat(pending.getSubmittedAt()).isEqualTo(NOW);
        assertThat(pending.getFeedbackId()).isEqualTo(feedbackId);
    }

    @Test
    void publicSubmitRejectsExpiredAndAlreadySubmittedLinks() {
        FeedbackRequest expired = request(FeedbackRequestStatus.EXPIRED);
        expired.setTokenHash(tokenService.hash("expired"));
        when(requests.findByTokenHashAndDeletedFalseForUpdate(expired.getTokenHash()))
                .thenReturn(Optional.of(expired));

        assertThat(codeOf(catchThrowable(() -> service.submit(
                "expired", PublicFeedbackSubmitRequest.builder().score(5).build()))))
                .isEqualTo("FEEDBACK_REQUEST_EXPIRED");

        FeedbackRequest submitted = request(FeedbackRequestStatus.SUBMITTED);
        submitted.setTokenHash(tokenService.hash("submitted"));
        when(requests.findByTokenHashAndDeletedFalseForUpdate(submitted.getTokenHash()))
                .thenReturn(Optional.of(submitted));

        assertThat(codeOf(catchThrowable(() -> service.submit(
                "submitted", PublicFeedbackSubmitRequest.builder().score(5).build()))))
                .isEqualTo("FEEDBACK_REQUEST_ALREADY_SUBMITTED");
    }

    @Test
    void aDeletedCustomerMakesTheLinkUnavailableRatherThanRevealingWhy() {
        FeedbackRequest pending = request(FeedbackRequestStatus.PENDING);
        pending.setTokenHash(tokenService.hash("public-token"));
        when(requests.findByTokenHashAndDeletedFalseForUpdate(pending.getTokenHash()))
                .thenReturn(Optional.of(pending));
        when(feedbackService.submitResolved(eq(tenantId), eq(userId), any()))
                .thenThrow(new BusinessException("ACCOUNT_NOT_FOUND", "account deleted"));

        assertThat(codeOf(catchThrowable(() -> service.submit(
                "public-token", PublicFeedbackSubmitRequest.builder().score(5).build()))))
                .isEqualTo("FEEDBACK_REQUEST_UNAVAILABLE");
    }
}
