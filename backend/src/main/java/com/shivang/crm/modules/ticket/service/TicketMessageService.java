package com.shivang.crm.modules.ticket.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.shivang.crm.modules.ticket.repository.TicketMessageRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.1-P3: the Ticket conversation.
 *
 * <p><b>Authorization is inherited, never independent.</b> A message has no
 * {@code ownerId} and no scope of its own, so every operation here begins by
 * resolving the parent Ticket and running the same {@code RecordScopeGuard} checks
 * {@code TicketService} runs. A message can therefore never become a way to reach a
 * ticket its caller could not otherwise see — the child grants nothing the parent
 * does not already grant.
 *
 * <p>Two further constraints exist beyond the parent's scope:
 * <ul>
 *   <li><b>Author-only mutation.</b> Only {@code authorUserId == caller} may edit or
 *       delete a message, even at ALL scope. Ticket scope says which tickets you may
 *       work; it is not a licence to edit another agent's words. No administrative
 *       override exists, and none was invented.</li>
 *   <li><b>CLOSED freezes the conversation.</b> Reads stay allowed — a closed
 *       ticket's history is exactly what an agent needs — but create, update and
 *       delete are refused. RESOLVED is not treated as closed; P1's lifecycle
 *       contract places no such restriction on it.</li>
 * </ul>
 *
 * <p>Writes run inside this class's {@code @Transactional} so the row and its outbox
 * event commit or roll back together, exactly as Ticket's do.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TicketMessageService {

    /**
     * Pagination bounds, matching {@code TicketService}. A ticket can accumulate an
     * unbounded number of messages, so an unclamped page request is a real exposure
     * rather than a hypothetical one.
     */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final TicketMessageMapper ticketMessageMapper;
    private final RecordScopeGuard recordScopeGuard;
    private final CustomerRelationshipValidator relationshipValidator;
    private final ActivityService activityService;
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;
    private final java.time.Clock clock;

    // ================= Create =================

    /**
     * Adds a message to a ticket's conversation.
     *
     * <p>The author is always the authenticated caller. P3 rejects author
     * impersonation rather than honouring a client-supplied author, because a
     * conversation whose authorship is not trustworthy is not an audit record.
     *
     * <p>No {@code TICKET.UPDATED} is published: adding a message is a child
     * creation, and the parent's own lifecycle and mutability state have not moved.
     * Consumers interested in the conversation follow {@code TICKET_MESSAGE}.
     */
    public TicketMessageResponse createMessage(
            UUID ticketId,
            UUID tenantId,
            UUID authorUserId,
            TicketMessageCreateRequest request) {

        Ticket ticket = resolveAccessibleTicket(ticketId, tenantId, authorUserId, "write");
        requireNotClosed(ticket);

        // The FKs on ticket_messages are tenant-blind, so this is the only thing
        // stopping an author from another tenant being recorded here. Redundant while
        // the author always comes from the principal, and deliberately so: it keeps the
        // guarantee local to this row rather than relying on who calls the service.
        relationshipValidator.requireTenantOwner(authorUserId, tenantId);

        String body = requireBody(request.getBody());

        TicketMessage message = new TicketMessage();
        message.setTenantId(tenantId);
        message.setTicketId(ticketId);
        message.setAuthorUserId(authorUserId);
        // Storing the body verbatim. A support note's whitespace is part of what the
        // agent wrote, and trimming it would alter an audit record.
        message.setBody(body);
        message.setVisibility(request.getVisibility() != null
                ? request.getVisibility()
                : TicketMessageVisibility.INTERNAL);
        message.setDeleted(false);

        Instant respondedAt = clock.instant();
        // L3.1-P4: the first agent message is the response SLA answer, so it stamps
        // firstResponseAt here rather than through the ticket update path — assignment,
        // status changes and field edits are not responses. Saving the ticket in the
        // same transaction means the stamp and the message commit or roll back
        // together, and deliberately publishes no TICKET.UPDATED.
        boolean recordedFirstResponse = ticket.recordFirstResponse(respondedAt);
        if (recordedFirstResponse) {
            ticketRepository.save(ticket);
        }

        TicketMessage saved = ticketMessageRepository.save(message);

        logMessageActivity(saved, authorUserId, "TICKET_MESSAGE_CREATED",
                "Message added to ticket");
        publishMessageCreated(saved, authorUserId);
        return ticketMessageMapper.toResponse(saved);
    }

    // ================= Read =================

    @Transactional(readOnly = true)
    public Page<TicketMessageResponse> listMessages(
            UUID ticketId,
            UUID tenantId,
            UUID userId,
            int page,
            int size) {

        // Read scope, same as GET /tickets/{id}.
        resolveAccessibleTicket(ticketId, tenantId, userId, "read");

        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size == 0 ? DEFAULT_PAGE_SIZE : size));
        Pageable pageable = PageRequest.of(
                safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        return ticketMessageRepository
                .findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, ticketId, pageable)
                .map(ticketMessageMapper::toResponse);
    }

    /**
     * Reads one message.
     *
     * <p>Allows reading on a CLOSED ticket: the conversation is the record of how a
     * ticket was handled, and freezing it must not make it unreadable.
     */
    @Transactional(readOnly = true)
    public TicketMessageResponse getMessage(
            UUID ticketId, UUID messageId, UUID tenantId, UUID userId) {

        resolveAccessibleTicket(ticketId, tenantId, userId, "read");

        // Scoped to the ticket from the URL, so a message that belongs to a different
        // ticket is not found here even if the caller may read both tickets.
        TicketMessage message = ticketMessageRepository
                .findByIdAndTenantIdAndTicketIdAndDeletedFalse(messageId, tenantId, ticketId)
                .orElseThrow(() -> new BusinessException(
                        "TICKET_MESSAGE_NOT_FOUND", "Message not found"));

        return ticketMessageMapper.toResponse(message);
    }

    // ================= Update =================

    /**
     * Edits the body and/or visibility of a message.
     *
     * <p>Author-only, at every scope including ALL. No event is published: P3 defines
     * only {@code TICKET_MESSAGE.CREATED}, and an edit is not a second creation. The
     * change is still audited through the activity log.
     */
    public TicketMessageResponse updateMessage(
            UUID ticketId,
            UUID messageId,
            UUID tenantId,
            UUID userId,
            TicketMessageUpdateRequest request) {

        Ticket ticket = resolveAccessibleTicket(ticketId, tenantId, userId, "write");
        requireNotClosed(ticket);

        TicketMessage message = requireMessage(ticketId, messageId, tenantId);
        requireAuthor(message, userId);

        // Body is validated only when supplied, so a visibility-only edit is not
        // rejected for having no body.
        if (request.getBody() != null) {
            message.setBody(requireBody(request.getBody()));
        }
        if (request.getVisibility() != null) {
            message.setVisibility(request.getVisibility());
        }

        TicketMessage updated = ticketMessageRepository.save(message);

        logMessageActivity(updated, userId, "TICKET_MESSAGE_UPDATED", "Message edited");
        return ticketMessageMapper.toResponse(updated);
    }

    // ================= Delete =================

    /**
     * Soft-deletes a message. Never a physical delete.
     *
     * <p>No {@code TICKET_MESSAGE.DELETED} event is emitted in P3; the activity log
     * records the removal.
     */
    public void deleteMessage(UUID ticketId, UUID messageId, UUID tenantId, UUID userId) {
        Ticket ticket = resolveAccessibleTicket(ticketId, tenantId, userId, "write");
        requireNotClosed(ticket);

        TicketMessage message = requireMessage(ticketId, messageId, tenantId);
        requireAuthor(message, userId);

        message.softDelete(userId);
        ticketMessageRepository.save(message);

        logMessageActivity(message, userId, "TICKET_MESSAGE_DELETED", "Message deleted");
    }

    // ================= Shared resolution =================

    /**
     * Resolves the parent ticket inside the tenant and applies its record scope.
     *
     * <p>Every message operation starts here, so the parent is always checked before
     * the child. The not-found message is deliberately identical for "no such ticket",
     * "deleted ticket" and "another tenant's ticket" — it must not disclose whether a
     * record exists elsewhere.
     */
    private Ticket resolveAccessibleTicket(UUID ticketId, UUID tenantId, UUID userId, String action) {
        String scope = recordScopeGuard.requireScope(tenantId, userId, "ticket", action);

        Ticket ticket = ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId)
                .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));

        // Same scope semantics as Ticket itself, so a message cannot widen access.
        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, userId, ticket.getOwnerId(), ticket.getCreatedBy());

        return ticket;
    }

    /**
     * Resolves a live message that belongs to the ticket named in the URL.
     *
     * <p>Authorship is asserted separately by {@link #requireAuthor}. A deleted
     * message is simply not found, matching the repository's deleted-record
     * convention.
     */
    private TicketMessage requireMessage(UUID ticketId, UUID messageId, UUID tenantId) {
        return ticketMessageRepository
                .findByIdAndTenantIdAndTicketIdAndDeletedFalse(messageId, tenantId, ticketId)
                .orElseThrow(() -> new BusinessException(
                        "TICKET_MESSAGE_NOT_FOUND", "Message not found"));
    }

    /**
     * Rejects mutation of a message by anyone but its author.
     *
     * <p>Separate from {@link #requireOwnedMessage} so the caller id is explicit at
     * the decision point rather than passed and forgotten.
     */
    private void requireAuthor(TicketMessage message, UUID userId) {
        if (!message.getAuthorUserId().equals(userId)) {
            // Deliberately the same error whether or not the caller's ticket scope
            // would have allowed the edit: a manager outside the conversation learns
            // nothing beyond "not yours".
            throw new BusinessException("TICKET_MESSAGE_AUTHOR_ONLY",
                    "Only the original author may modify this message");
        }
    }

    /** CLOSED freezes the conversation. RESOLVED does not. */
    private void requireNotClosed(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessException("TICKET_MESSAGE_CLOSED_TICKET",
                    "Cannot modify messages on a closed ticket");
        }
    }

    private String requireBody(String body) {
        if (body == null || body.isBlank()) {
            throw new BusinessException("TICKET_MESSAGE_BODY_REQUIRED",
                    "Message body is required");
        }
        return body;
    }

    // ================= Events and activity =================

    /**
     * L3.1-P3: sole producer of {@code TICKET_MESSAGE.CREATED}.
     *
     * <p>The event's {@code entityId} is the message id, not the ticket id: the
     * message is its own aggregate for eventing, following the DEAL_LINE_ITEM
     * precedent. Consumers receive the parent separately via {@code ticketId}.
     */
    private void publishMessageCreated(TicketMessage message, UUID authorUserId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("actorId", authorUserId.toString());
        metadata.put("actorType", "USER");
        metadata.put("source", "MANUAL");
        // Explicit: the message's own ticket, so a consumer does not have to infer
        // the parent from the entity id.
        metadata.put("ticketId", message.getTicketId().toString());
        metadata.put("authorUserId", message.getAuthorUserId().toString());
        metadata.put("visibility", message.getVisibility().name());

        canonicalCrmEventPublisher.publish(
                message.getTenantId(),
                CanonicalCrmEvent.TICKET_MESSAGE_ENTITY_TYPE,
                CanonicalCrmEvent.CREATED_EVENT_TYPE,
                message.getId(),
                metadata);
    }

    /**
     * Writes the audit row against the parent Ticket, so a message appears on the
     * ticket's timeline. The message body is deliberately absent: the TicketMessage
     * remains the source of truth for content, and metadata is identifiers only.
     */
    private void logMessageActivity(
            TicketMessage message, UUID userId, String activityType, String description) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("messageId", message.getId().toString());
        metadata.put("authorUserId", message.getAuthorUserId().toString());
        metadata.put("visibility", message.getVisibility().name());

        activityService.logActivity(
                message.getTenantId(), message.getTicketId(), "TICKET",
                activityType, description, userId, metadata);
    }
}
