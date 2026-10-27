package com.shivang.crm.modules.feedback.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.5: ticket feedback request lifecycle and the anonymous response path.
 *
 * <p>Two independent authorization boundaries meet here:
 * <ul>
 *   <li>Staff operations require ticket:write/read plus feedback:write/read, and
 *       apply the ticket's OWN/TEAM/ALL scope through {@link RecordScopeGuard}.</li>
 *   <li>The public path has no user identity at all. Its only credential is the
 *       high-entropy token; every customer id is read from the persisted request,
 *       never from the caller.</li>
 * </ul>
 *
 * <p>At most one live PENDING request exists per ticket. A partial unique index
 * cannot express "not expired", so creation and regeneration take a pessimistic
 * lock on the ticket row before checking. Public submission locks the request row
 * so two clicks cannot create two feedback rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackRequestService {

    /** Public links expire after 30 days; this is shown in the staff UI. */
    public static final Duration REQUEST_TTL = Duration.ofDays(30);

    private static final Set<String> PUBLIC_UNAVAILABLE_CODES = Set.of(
            "ACCOUNT_NOT_FOUND", "CONTACT_NOT_FOUND", "TICKET_NOT_FOUND");

    private final FeedbackRequestRepository requestRepository;
    private final TicketRepository ticketRepository;
    private final FeedbackService feedbackService;
    private final FeedbackTokenService tokenService;
    private final RecordScopeGuard recordScopeGuard;
    private final Clock clock;

    // ------------------------------------------------------------------
    // Staff surface
    // ------------------------------------------------------------------

    @Transactional
    public FeedbackRequestResponse create(
            UUID tenantId, UUID userId, UUID ticketId, FeedbackRequestCreateRequest body) {

        FeedbackType type = resolveRequestType(body);
        Ticket ticket = requireWritableTicket(tenantId, userId, ticketId);
        Instant now = Instant.now(clock);

        if (!requestRepository.findLivePending(tenantId, ticketId, now).isEmpty()) {
            throw new BusinessException("FEEDBACK_REQUEST_PENDING_EXISTS",
                    "This ticket already has a pending feedback request; revoke or regenerate it first");
        }

        Issued issued = createRequest(tenantId, userId, ticket, type, now);
        log.info("Created feedback request {} for ticket {} tenant {}",
                issued.request().getId(), ticketId, tenantId);
        return toStaffResponse(issued.request(), issued.rawToken(), now);
    }

    @Transactional(readOnly = true)
    public List<FeedbackRequestResponse> list(UUID tenantId, UUID userId, UUID ticketId) {
        requireReadableTicket(tenantId, userId, ticketId);
        Instant now = Instant.now(clock);
        return requestRepository
                .findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, ticketId)
                .stream()
                .map(request -> toStaffResponse(request, null, now))
                .toList();
    }

    @Transactional
    public FeedbackRequestResponse revoke(
            UUID tenantId, UUID userId, UUID ticketId, UUID requestId) {

        requireWritableTicket(tenantId, userId, ticketId);
        FeedbackRequest request = requireRequestForUpdate(tenantId, ticketId, requestId);
        Instant now = Instant.now(clock);

        return switch (request.statusAt(now)) {
            case SUBMITTED -> throw new BusinessException("FEEDBACK_REQUEST_NOT_REVOCABLE",
                    "A submitted feedback request cannot be revoked");
            case REVOKED -> toStaffResponse(request, null, now);
            case EXPIRED -> throw new BusinessException("FEEDBACK_REQUEST_EXPIRED",
                    "This feedback request has already expired");
            case PENDING -> {
                request.revoke(userId, now);
                yield toStaffResponse(request, null, now);
            }
        };
    }

    @Transactional
    public FeedbackRequestResponse regenerate(
            UUID tenantId, UUID userId, UUID ticketId, UUID requestId) {

        Ticket ticket = requireWritableTicket(tenantId, userId, ticketId);
        FeedbackRequest old = requireRequestForUpdate(tenantId, ticketId, requestId);
        Instant now = Instant.now(clock);

        if (old.statusAt(now) == FeedbackRequestStatus.SUBMITTED) {
            throw new BusinessException("FEEDBACK_REQUEST_NOT_REVOCABLE",
                    "A submitted feedback request cannot be regenerated");
        }
        if (old.statusAt(now) == FeedbackRequestStatus.PENDING) {
            old.revoke(userId, now);
        }

        if (!requestRepository.findLivePending(tenantId, ticketId, now).isEmpty()) {
            throw new BusinessException("FEEDBACK_REQUEST_PENDING_EXISTS",
                    "This ticket already has a pending feedback request");
        }

        Issued issued = createRequest(tenantId, userId, ticket, old.getType(), now);
        log.info("Regenerated feedback request {} for ticket {} tenant {}",
                issued.request().getId(), ticketId, tenantId);
        return toStaffResponse(issued.request(), issued.rawToken(), now);
    }

    // ------------------------------------------------------------------
    // Public surface
    // ------------------------------------------------------------------

    /**
     * Returns only what the anonymous page needs. Expired/revoked/submitted
     * states are returned as data rather than errors so the page can render a
     * clear terminal state instead of a broken form.
     */
    @Transactional(readOnly = true)
    public PublicFeedbackRequestResponse publicMetadata(String rawToken) {
        FeedbackRequest request = requireByToken(rawToken);
        Instant now = Instant.now(clock);
        FeedbackType type = request.getType();
        return PublicFeedbackRequestResponse.builder()
                .status(request.statusAt(now))
                .type(type)
                .minScore(type.getMinScore())
                .maxScore(type.getMaxScore())
                .expiresAt(request.getExpiresAt())
                .submittedAt(request.getSubmittedAt())
                .build();
    }

    @Transactional
    public PublicFeedbackSubmitResponse submit(String rawToken, PublicFeedbackSubmitRequest body) {
        FeedbackRequest request = requestRepository
                .findByTokenHashAndDeletedFalseForUpdate(tokenService.hash(rawToken))
                .orElseThrow(FeedbackRequestService::invalidToken);
        Instant now = Instant.now(clock);

        switch (request.statusAt(now)) {
            case SUBMITTED -> throw new BusinessException("FEEDBACK_REQUEST_ALREADY_SUBMITTED",
                    "This feedback has already been submitted");
            case REVOKED -> throw new BusinessException("FEEDBACK_REQUEST_REVOKED",
                    "This feedback link has been revoked");
            case EXPIRED -> throw new BusinessException("FEEDBACK_REQUEST_EXPIRED",
                    "This feedback link has expired");
            case PENDING -> {
                // continue below
            }
        }

        FeedbackResponse feedback;
        try {
            feedback = feedbackService.submitResolved(
                    request.getTenantId(),
                    request.getCreatedBy(),
                    new FeedbackService.TrustedFeedbackSubmission(
                            request.getAccountId(),
                            request.getContactId(),
                            request.getTicketId(),
                            request.getType(),
                            body.getScore(),
                            body.getComment(),
                            FeedbackSource.WEBSITE,
                            "feedback-request:" + request.getId()));
        } catch (BusinessException ex) {
            // A deleted customer/ticket after link issuance is not something the
            // anonymous holder should be able to distinguish from a dead link.
            if (PUBLIC_UNAVAILABLE_CODES.contains(ex.getErrorCode())) {
                throw new BusinessException("FEEDBACK_REQUEST_UNAVAILABLE",
                        "This feedback request is no longer available");
            }
            throw ex;
        }

        request.markSubmitted(feedback.getId(), request.getCreatedBy(), now);
        log.info("Public feedback request {} submitted for ticket {}",
                request.getId(), request.getTicketId());

        return PublicFeedbackSubmitResponse.builder()
                .status(FeedbackRequestStatus.SUBMITTED)
                .submittedAt(request.getSubmittedAt())
                .build();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private FeedbackType resolveRequestType(FeedbackRequestCreateRequest body) {
        FeedbackType type = body == null || body.getType() == null
                ? FeedbackType.CSAT
                : body.getType();
        if (!type.isScored()) {
            throw new BusinessException("FEEDBACK_REQUEST_TYPE_UNSUPPORTED",
                    "A public feedback request must use CSAT, NPS or CES");
        }
        return type;
    }

    private Issued createRequest(
            UUID tenantId, UUID userId, Ticket ticket, FeedbackType type, Instant now) {

        String rawToken = tokenService.generateToken();
        FeedbackRequest request = new FeedbackRequest();
        request.setTenantId(tenantId);
        request.setOwnerId(ticket.getOwnerId());
        request.setCreatedBy(userId);
        request.setUpdatedBy(userId);
        request.setTicketId(ticket.getId());
        request.setAccountId(ticket.getAccountId());
        request.setContactId(ticket.getContactId());
        request.setType(type);
        request.setTokenHash(tokenService.hash(rawToken));
        request.setExpiresAt(now.plus(REQUEST_TTL));
        request.setDeleted(false);

        return new Issued(requestRepository.save(request), rawToken);
    }

    private FeedbackRequest requireRequestForUpdate(UUID tenantId, UUID ticketId, UUID requestId) {
        return requestRepository.findForUpdate(requestId, tenantId, ticketId)
                .orElseThrow(() -> new BusinessException("FEEDBACK_REQUEST_NOT_FOUND",
                        "Feedback request not found"));
    }

    private FeedbackRequest requireByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        return requestRepository.findByTokenHashAndDeletedFalse(tokenService.hash(rawToken))
                .orElseThrow(FeedbackRequestService::invalidToken);
    }

    private static BusinessException invalidToken() {
        return new BusinessException("FEEDBACK_REQUEST_NOT_FOUND", "Feedback request not found");
    }

    private Ticket requireWritableTicket(UUID tenantId, UUID userId, UUID ticketId) {
        String scope = recordScopeGuard.requireScope(tenantId, userId, "ticket", "write");
        Ticket ticket = ticketRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(ticketId, tenantId)
                .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, userId, ticket.getOwnerId(), ticket.getCreatedBy());
        return ticket;
    }

    private Ticket requireReadableTicket(UUID tenantId, UUID userId, UUID ticketId) {
        String scope = recordScopeGuard.requireScope(tenantId, userId, "ticket", "read");
        Ticket ticket = ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId)
                .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, userId, ticket.getOwnerId(), ticket.getCreatedBy());
        return ticket;
    }

    private FeedbackRequestResponse toStaffResponse(
            FeedbackRequest request, String rawToken, Instant now) {
        return FeedbackRequestResponse.builder()
                .id(request.getId())
                .ticketId(request.getTicketId())
                .accountId(request.getAccountId())
                .contactId(request.getContactId())
                .type(request.getType())
                .status(request.statusAt(now))
                .expiresAt(request.getExpiresAt())
                .submittedAt(request.getSubmittedAt())
                .revokedAt(request.getRevokedAt())
                .feedbackId(request.getFeedbackId())
                .createdAt(request.getCreatedAt())
                .token(rawToken)
                .tokenPath(rawToken == null ? null : tokenService.tokenPath(rawToken))
                .build();
    }

    private record Issued(FeedbackRequest request, String rawToken) {
    }
}
