package com.shivang.crm.modules.feedback.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.feedback.entity.Feedback;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.repository.FeedbackRepository;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.dto.TicketCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.modules.ticket.service.TicketService;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.3-P2: converts a {@code COMPLAINT} Feedback record into operational work.
 *
 * <p>This service orchestrates; it does not own Ticket behaviour. {@link TicketService}
 * already owns validation, the OPEN initial status, assigning the caller's default
 * owner when none is supplied, the SLA policy date calculation, activity logging and
 * the canonical {@code TICKET.CREATED} event. Reusing it means a complaint ticket is
 * indistinguishable from one raised directly against an account, which is the whole
 * point: a customer complaint is the same kind of work item, regardless of how it
 * entered the system.
 *
 * <p>Feedback stays what it is — the customer's statement. The Ticket is the
 * operational work started in response to it. The two are linked, not merged: no
 * status, SLA, assignee or message concept is added to Feedback.
 *
 * <h2>One-to-zero-or-one</h2>
 * <p>A complaint creates at most one Ticket, and any re-invocation of the conversion
 * returns that Ticket rather than creating another. The feedback row is locked
 * ({@code PESSIMISTIC_WRITE}) for the duration of the transaction, so two concurrent
 * conversions of the same complaint serialise on that lock: the first creates the
 * Ticket, the second then reads the non-null {@code ticket_id} and returns it. No
 * distributed lock and no Redis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintTicketService {

    private final FeedbackRepository feedbackRepository;
    private final TicketService ticketService;
    private final RecordScopeGuard recordScopeGuard;
    private final CustomerRelationshipValidator relationshipValidator;

    /**
     * @throws BusinessException {@code FEEDBACK_NOT_COMPLAINT} when the resolved
     *         feedback is not a complaint, {@code NOT_FOUND} when it is outside the
     *         caller's record scope, and whatever {@code TicketService} raises for a
     *         bad ticket creation (validation, authorization)
     */
    @Transactional
    public TicketResponse convertToTicket(UUID tenantId, UUID userId, UUID feedbackId) {
        String feedbackScope = recordScopeGuard.requireScope(tenantId, userId, "feedback", "read");

        Feedback feedback = feedbackRepository
                .findByIdAndTenantIdAndDeletedFalseForUpdate(feedbackId, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Feedback not found"));

        List<UUID> teamUserIds = "TEAM".equals(feedbackScope)
                ? recordScopeGuard.teamMemberIds(tenantId, userId)
                : List.of();
        if (!recordScopeGuard.withinOwnerCreatorScope(feedbackScope, tenantId, userId,
                feedback.getOwnerId(), feedback.getCreatedBy())) {
            // Fail closed, and do not distinguish from NOT_FOUND.
            throw new BusinessException("NOT_FOUND", "Feedback not found");
        }

        if (feedback.getType() != FeedbackType.COMPLAINT) {
            throw new BusinessException("FEEDBACK_NOT_COMPLAINT",
                    "Only complaints can be converted into a ticket");
        }

        if (feedback.getTicketId() != null) {
            // Idempotent repeat: the complaint is already operationally linked. Return
            // the existing ticket after asserting the link still points at this account
            // and tenant, so a stale or tampered link cannot resurrect a foreign ticket.
            relationshipValidator.requireTicketBelongsToAccount(
                    feedback.getTicketId(), feedback.getAccountId(), tenantId);
            relationshipValidator.requireContactBelongsToAccount(
                    feedback.getContactId(), feedback.getAccountId(), tenantId);
            log.info("Complaint {} already linked to ticket {}; returning it unchanged",
                    feedbackId, feedback.getTicketId());
            return ticketService.getTicketById(feedback.getTicketId(), tenantId);
        }

        String comment = feedback.getComment() == null ? "" : feedback.getComment().trim();
        String subject = "Complaint: " + (comment.isEmpty()
                ? "new complaint"
                : (comment.length() <= 120 ? comment : comment.substring(0, 120) + "…"));

        TicketCreateRequest createRequest = TicketCreateRequest.builder()
                .accountId(feedback.getAccountId())
                .contactId(feedback.getContactId())
                .subject(subject)
                .description(comment.isEmpty() ? null : comment)
                .build();

        TicketResponse ticket = ticketService.createTicket(tenantId, userId, createRequest);

        feedback.setTicketId(ticket.getId());
        feedback.setUpdatedBy(userId);
        feedbackRepository.save(feedback);

        log.info("Complaint {} converted to ticket {}", feedbackId, ticket.getId());
        return ticket;
    }
}
