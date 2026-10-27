package com.shivang.crm.modules.ticket.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.config.TicketSlaProperties;
import com.shivang.crm.modules.ticket.dto.TicketAssignRequest;
import com.shivang.crm.modules.ticket.dto.TicketCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.modules.ticket.dto.TicketStatusChangeRequest;
import com.shivang.crm.modules.ticket.dto.TicketUpdateRequest;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.mapper.TicketMapper;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.modules.ticket.repository.TicketSpecifications;
import com.shivang.crm.util.UserUtil;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.1-P1: the Ticket aggregate.
 *
 * <p>Single authoritative producer of the four Ticket canonical events. Nothing
 * else publishes TICKET events — not the controller, not the repository, and there
 * are no JPA entity listeners.
 *
 * <p>Every mutation runs inside this class's {@code @Transactional} (REQUIRED) so
 * the database change and its outbox row commit or roll back together:
 * <pre>
 *   mutation → canonical event → transactional outbox → commit
 * </pre>
 * A {@code BusinessException} is a {@code RuntimeException}, so an authorisation or
 * validation failure discards the event with the mutation.
 *
 * <p>Authorization is layered exactly as elsewhere in this codebase: {@code
 * RbacFilter} resolves the module/action from the URL, {@code @PreAuthorize} on the
 * controller restates it, and {@code RecordScopeGuard} here owns the record
 * boundary. Scope narrows rows WITHIN a tenant and never widens it, so every lookup
 * below is tenant-scoped and deleted-aware regardless of scope.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TicketService {

    /**
     * Pagination bounds. The Deal list uses a bare {@code PageRequest.of(page, size)}
     * with no clamp, which lets a client request an unbounded page; CrmRecordService
     * clamps. Ticket clamps, and orders explicitly, so list results are deterministic.
     */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;
    private final RecordScopeGuard recordScopeGuard;
    private final CustomerRelationshipValidator relationshipValidator;
    private final ActivityService activityService;
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;
    private final TicketSlaProperties slaProperties;
    private final Clock clock;

    // ================= Create =================

    /**
     * Creates a Ticket. Always {@link TicketStatus#OPEN}: a client cannot supply an
     * initial status, so it cannot invent a delivered outcome.
     *
     * <p>Create deliberately does not evaluate record scope — there is no record yet.
     * What it does enforce is relationship integrity, which is the reason Account
     * cannot be crossed: both foreign keys on {@code tickets} are tenant-blind, so
     * {@link CustomerRelationshipValidator} is the only thing that stops a
     * known-but-foreign UUID from being attached.
     */
    public TicketResponse createTicket(UUID tenantId, UUID userId, TicketCreateRequest request) {
        log.info("Creating ticket for tenant: {}", tenantId);

        // requireLiveAccount is null-tolerant, which is correct for Deal (an
        // unlinked deal is legal) but not here: tickets.account_id is NOT NULL and
        // Account is the canonical customer identity. Without this guard a missing
        // account would reach the database and surface as a constraint violation
        // instead of a stable business error. The DTO's @NotNull also covers the
        // normal path; this covers direct service calls.
        if (request.getAccountId() == null) {
            throw new BusinessException("TICKET_ACCOUNT_REQUIRED",
                    "A ticket must belong to an account");
        }

        relationshipValidator.requireLiveAccount(request.getAccountId(), tenantId);
        if (request.getContactId() != null) {
            relationshipValidator.requireLiveContact(request.getContactId(), tenantId);
            relationshipValidator.requireContactBelongsToAccount(
                    request.getContactId(), request.getAccountId(), tenantId);
        }
        // Null-tolerant: validates only when an owner was supplied. Reassignment
        // behaviour is L3.1-P2; P1 validates ownership given at creation.
        relationshipValidator.requireTenantOwner(request.getOwnerId(), tenantId);

        Ticket ticket = new Ticket();
        ticket.setTenantId(tenantId);
        ticket.setCreatedBy(userId);
        ticket.setUpdatedBy(userId);
        // An unassigned ticket defaults to its creator, so a new ticket is always
        // visible to whoever raised it under an OWN scope.
        ticket.setOwnerId(request.getOwnerId() != null ? request.getOwnerId() : userId);
        ticket.setAccountId(request.getAccountId());
        ticket.setContactId(request.getContactId());
        ticket.setSubject(request.getSubject());
        ticket.setDescription(request.getDescription());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setDeleted(false);

        Instant createdAt = clock.instant();
        ticket.applySlaPolicy(createdAt, slaProperties.responseDuration(),
                slaProperties.resolutionDuration());

        Ticket saved = ticketRepository.save(ticket);

        logTicketActivity(saved, userId, "TICKET_CREATED", "Ticket created: " + saved.getSubject(), null);
        publishTicketCreated(saved, userId);
        return ticketMapper.toResponse(saved);
    }

    // ================= Read =================

    /**
     * Asserts the caller may read this Ticket under its record scope.
     *
     * <p>Lets a Ticket-scoped collaborator (the timeline) reuse the aggregate's
     * authorization instead of re-deriving scope and risking a second, weaker
     * definition of Ticket access.
     */
    @Transactional(readOnly = true)
    public void requireReadable(UUID id, UUID tenantId, UUID userId) {
        String readScope = recordScopeGuard.requireScope(tenantId, userId, "ticket", "read");

        Ticket ticket = ticketRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));

        recordScopeGuard.assertWithinOwnerCreatorScope(
                readScope, tenantId, userId, ticket.getOwnerId(), ticket.getCreatedBy());
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(UUID id, UUID tenantId) {
        UUID currentUserId = UserUtil.currentUserId();
        String readScope = recordScopeGuard.requireScope(tenantId, currentUserId, "ticket", "read");

        Ticket ticket = ticketRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));

        recordScopeGuard.assertWithinOwnerCreatorScope(
                readScope, tenantId, currentUserId, ticket.getOwnerId(), ticket.getCreatedBy());

        return ticketMapper.toResponse(ticket);
    }

    /**
     * Lists Tickets the caller may see, tenant-scoped, deleted-excluded, newest
     * first.
     *
     * <p>Paging is bounded and ordered: the tenant predicate always narrows, and
     * {@code visibleToUser} is applied on top of it rather than instead of it.
     */
    @Transactional(readOnly = true)
    public Page<TicketResponse> listTickets(
            UUID tenantId,
            UUID accountId,
            TicketStatus status,
            String search,
            int page,
            int size) {

        UUID currentUserId = UserUtil.currentUserId();
        String readScope = recordScopeGuard.requireScope(tenantId, currentUserId, "ticket", "read");

        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size == 0 ? DEFAULT_PAGE_SIZE : size));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<UUID> teamUserIds = "TEAM".equals(readScope)
                ? recordScopeGuard.teamMemberIds(tenantId, currentUserId)
                : List.of();

        Specification<Ticket> spec = TicketSpecifications.byTenantId(tenantId)
                .and(TicketSpecifications.notDeleted())
                .and(TicketSpecifications.visibleToUser(readScope, currentUserId, teamUserIds));

        if (accountId != null) {
            spec = spec.and(TicketSpecifications.byAccountId(accountId));
        }
        if (status != null) {
            spec = spec.and(TicketSpecifications.byStatus(status));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(TicketSpecifications.searchBySubject(search.trim()));
        }

        return ticketRepository.findAll(spec, pageable).map(ticketMapper::toResponse);
    }

    // ================= Update =================

    /**
     * Updates a Ticket's mutable fields. Partial: an omitted field is left alone.
     *
     * <p>Lifecycle state is not reachable here. Status has its own endpoint so the
     * transition matrix cannot be bypassed by a field edit.
     */
    public TicketResponse updateTicket(UUID id, UUID tenantId, UUID userId, TicketUpdateRequest request) {
        String writeScope = recordScopeGuard.requireScope(tenantId, userId, "ticket", "write");

        Ticket ticket = ticketRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                writeScope, tenantId, userId, ticket.getOwnerId(), ticket.getCreatedBy());

        // L3.0-P2: validate the values this request would PERSIST, not just the
        // fields it carries. An omitted field means "unchanged", so the resulting
        // Account/Contact/Owner are the merge of the request and the current row.
        UUID resultingAccountId = request.getAccountId() != null ? request.getAccountId() : ticket.getAccountId();
        UUID resultingContactId = request.getContactId() != null ? request.getContactId() : ticket.getContactId();

        relationshipValidator.requireLiveAccount(resultingAccountId, tenantId);
        if (resultingContactId != null) {
            relationshipValidator.requireLiveContact(resultingContactId, tenantId);
            // Re-checked against the RESULTING account, so moving a ticket to
            // another account cannot leave a contact attached to the old one.
            relationshipValidator.requireContactBelongsToAccount(
                    resultingContactId, resultingAccountId, tenantId);
        }

        // Snapshot before mapping so a no-op PATCH publishes nothing.
        Map<String, Object> before = mutableSnapshot(ticket);

        ticketMapper.updateEntity(request, ticket);
        // L3.1-P2: ownerId is NOT updatable here. Assignment is a privileged
        // operation behind ticket:assign and POST /{id}/assign, so ticket:write
        // cannot reach ownership. The field stays on the DTO only so a payload
        // carrying it is not rejected as unknown; it is ignored here.
        ticket.setAccountId(resultingAccountId);
        ticket.setContactId(resultingContactId);
        ticket.setUpdatedBy(userId);

        Ticket updated = ticketRepository.save(ticket);

        List<String> changedFields = changedFields(before, mutableSnapshot(updated));
        if (!changedFields.isEmpty()) {
            logTicketActivity(updated, userId, "TICKET_UPDATED", "Ticket updated", changedFields);
            publishTicketUpdated(updated, userId, changedFields);
        } else {
            log.debug("Ticket {} update changed no fields; no event published", id);
        }
        return ticketMapper.toResponse(updated);
    }

    // ================= Status =================

    /**
     * The single status endpoint. Covers resolve, close and reopen.
     *
     * <p>Idempotent by design: requesting the current status is accepted and
     * publishes no {@code STATUS_CHANGED}, so a retried request neither errors nor
     * produces a phantom event.
     */
    public TicketResponse changeStatus(UUID id, UUID tenantId, UUID userId, TicketStatusChangeRequest request) {
        String writeScope = recordScopeGuard.requireScope(tenantId, userId, "ticket", "write");

        Ticket ticket = ticketRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                writeScope, tenantId, userId, ticket.getOwnerId(), ticket.getCreatedBy());

        TicketStatus previousStatus = ticket.getStatus();
        TicketStatus requested = request.getStatus();

        // Idempotence is checked FIRST. canTransitionTo deliberately excludes the
        // current status, so testing it first would reject a retried request instead
        // of absorbing it — the exact failure idempotency exists to prevent.
        if (previousStatus.isSameAs(requested)) {
            log.debug("Ticket {} already in status {}; treating as idempotent no-op", id, requested);
            return ticketMapper.toResponse(ticket);
        }

        if (!previousStatus.canTransitionTo(requested)) {
            throw new BusinessException("TICKET_INVALID_STATUS_TRANSITION",
                    "Cannot change ticket status from " + previousStatus + " to " + requested
                            + ". Allowed: " + previousStatus.allowedTransitions());
        }

        // The clock is injected so the timestamp is deterministic under test.
        ticket.applyStatus(requested, clock.instant());
        ticket.setUpdatedBy(userId);
        Ticket updated = ticketRepository.save(ticket);

        logTicketActivity(updated, userId, "TICKET_STATUS_CHANGED",
                "Ticket status changed from " + previousStatus + " to " + requested, null);
        publishTicketStatusChanged(updated, userId, previousStatus, requested);
        return ticketMapper.toResponse(updated);
    }

    // ================= Assign =================

    /**
     * L3.1-P2: the one canonical way to change Ticket ownership.
     *
     * <p>Separate from {@link #updateTicket} by design, matching the
     * {@code entitlement:assign} precedent in {@code CustomerEntitlementService}:
     * {@code ticket:write} must never imply reassignment, so the permission is
     * distinct, the endpoint is distinct, and the event is distinct.
     *
     * <p>Authorization is the same three layers as every other mutation, with
     * {@code ticket:assign} substituted for the write permission — and record scope
     * is enforced against the ticket as it exists BEFORE the change, so a caller
     * cannot reassign a ticket out of their own scope and into someone else's.
     *
     * <p>Idempotent: assigning the current owner succeeds and publishes nothing.
     * A retried request is an ordinary occurrence in a distributed system, and a
     * phantom OWNER_CHANGED would make consumers believe work moved when it did not.
     */
    public TicketResponse assignTicket(UUID id, UUID tenantId, UUID userId, TicketAssignRequest request) {
        return applyOwnerChange(id, tenantId, userId, request.getOwnerId(), false);
    }

    /**
     * Reassigns a Ticket without applying the {@code ticket:assign} permission check.
     *
     * <p>For SYSTEM-origin work such as SLA escalation, where there is no authenticated
     * user holding a role. The record-scope assert is also skipped: a manager reporting
     * to themselves holds neither OWN nor TEAM scope over the ticket, yet the whole
     * point of escalation is that they must take it over.
     *
     * <p>Everything that actually protects the data is still enforced — tenant-safe
     * lookup, same-tenant target validation, owner-change activity and event, and the
     * same-owner no-op.
     *
     * <p>Package-private on purpose: this bypasses authorization, so it must not be
     * reachable from a controller or any other user-facing entry point.
     * {@code TicketEscalationService} is the sole caller.
     */
    TicketResponse escalateTicket(UUID id, UUID tenantId, UUID newOwnerId) {
        return applyOwnerChange(id, tenantId, null, newOwnerId, true);
    }

    private TicketResponse applyOwnerChange(
            UUID id, UUID tenantId, UUID userId, UUID newOwnerId, boolean systemActor) {

        String assignScope = null;
        if (!systemActor) {
            // 'assign' is its own permission. A caller holding only ticket:write is
            // rejected before the ticket is even loaded, so an unauthorized caller
            // cannot learn whether it exists.
            assignScope = recordScopeGuard.requireScope(tenantId, userId, "ticket", "assign");
        }

        Ticket ticket = ticketRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));

        if (assignScope != null) {
            // Scoped on the PRE-change owner: assignment must not be a way to escape
            // your own record boundary.
            recordScopeGuard.assertWithinOwnerCreatorScope(
                    assignScope, tenantId, userId, ticket.getOwnerId(), ticket.getCreatedBy());
        }

        UUID previousOwnerId = ticket.getOwnerId();

        // No-op first: a same-owner assignment must not validate, mutate, or emit.
        // (Validation is skipped deliberately — the existing owner is already known
        // good, so re-checking it can only fail for unrelated reasons.)
        if (Objects.equals(previousOwnerId, newOwnerId)) {
            log.debug("Ticket {} already owned by {}; assignment is a no-op", id, newOwnerId);
            return ticketMapper.toResponse(ticket);
        }

        // Rejects an unknown, deleted, or cross-tenant target user: the FK on
        // owner_user_id is tenant-blind, so this is the only real check.
        relationshipValidator.requireTenantOwner(newOwnerId, tenantId);

        ticket.setOwnerId(newOwnerId);
        ticket.setUpdatedBy(userId);
        Ticket updated = ticketRepository.save(ticket);

        if (systemActor) {
            logEscalationActivity(updated, previousOwnerId, newOwnerId);
        } else {
            logTicketActivity(updated, userId, "TICKET_OWNER_CHANGED",
                    "Ticket owner changed from " + previousOwnerId + " to " + newOwnerId, null);
        }
        publishOwnerChanged(updated, previousOwnerId, newOwnerId, userId);
        return ticketMapper.toResponse(updated);
    }

    /**
     * SYSTEM escalation is attributed to the scheduler, not to a user. activities
     * .performed_by is nullable with actor_type carrying SYSTEM, and the owner-change
     * event drops actorId for the same reason.
     */
    private void logEscalationActivity(Ticket ticket, UUID previousOwnerId, UUID newOwnerId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ticketId", ticket.getId().toString());
        metadata.put("previousOwnerId", previousOwnerId == null ? null : previousOwnerId.toString());
        metadata.put("newOwnerId", newOwnerId.toString());
        metadata.put("reason", "SLA_BREACH");

        activityService.logSystemActivity(
                ticket.getTenantId(), ticket.getId(), "TICKET", "TICKET_ESCALATED",
                "Ticket escalated to manager after SLA breach", "TICKET_SLA_LIFECYCLE", metadata);
    }

    private void publishOwnerChanged(
            Ticket ticket, UUID previousOwnerId, UUID newOwnerId, UUID actorUserId) {
        Map<String, Object> metadata = new HashMap<>();
        addCustomerIdentifiers(metadata, ticket);
        metadata.put("previousOwnerId", previousOwnerId == null ? null : previousOwnerId.toString());
        metadata.put("newOwnerId", newOwnerId.toString());
        if (actorUserId != null) {
            metadata.put("actorId", actorUserId.toString());
            metadata.put("actorType", "USER");
            metadata.put("source", "MANUAL");
        } else {
            metadata.put("actorType", "SYSTEM");
            metadata.put("source", "SCHEDULER");
        }

        canonicalCrmEventPublisher.publish(
                ticket.getTenantId(),
                CanonicalCrmEvent.TICKET_ENTITY_TYPE,
                CanonicalCrmEvent.OWNER_CHANGED_EVENT_TYPE,
                ticket.getId(),
                metadata);
    }

    // ================= Delete =================

    /**
     * Soft-deletes a Ticket. Never a hard delete: a Ticket is customer
     * correspondence, so removing the row would destroy the record of what was
     * reported. Follows the L3.0-P4 convention used for Account, Contact and Deal.
     */
    public void deleteTicket(UUID id, UUID tenantId, UUID userId) {
        String deleteScope = recordScopeGuard.requireScope(tenantId, userId, "ticket", "delete");

        Ticket ticket = ticketRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                deleteScope, tenantId, userId, ticket.getOwnerId(), ticket.getCreatedBy());

        ticket.softDelete(userId);
        ticket.setUpdatedBy(userId);
        ticketRepository.save(ticket);

        logTicketActivity(ticket, userId, "TICKET_DELETED", "Ticket deleted: " + ticket.getSubject(), null);
        publishTicketDeleted(ticket, userId);
        log.info("Ticket soft deleted: {} for tenant {}", id, tenantId);
    }

    // ================= Change detection =================

    /**
     * The fields a PATCH may change, and therefore the only fields whose movement
     * justifies an event. Lifecycle and soft-delete state are absent because they
     * cannot be changed through this path, and {@code ownerId} is absent as of
     * L3.1-P2 because ownership belongs to {@link #assignTicket}.
     */
    private Map<String, Object> mutableSnapshot(Ticket ticket) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("subject", ticket.getSubject());
        snapshot.put("description", ticket.getDescription());
        snapshot.put("accountId", ticket.getAccountId());
        snapshot.put("contactId", ticket.getContactId());
        return snapshot;
    }

    private List<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
        List<String> changed = new ArrayList<>();
        before.forEach((field, oldValue) -> {
            if (!Objects.equals(oldValue, after.get(field))) {
                changed.add(field);
            }
        });
        return changed;
    }

    // ================= Events =================

    /** L3.1-P1: sole producer of {@code TICKET.CREATED}. */
    private void publishTicketCreated(Ticket ticket, UUID userId) {
        Map<String, Object> metadata = baseEventMetadata(userId);
        addCustomerIdentifiers(metadata, ticket);

        canonicalCrmEventPublisher.publish(
                ticket.getTenantId(),
                CanonicalCrmEvent.TICKET_ENTITY_TYPE,
                CanonicalCrmEvent.CREATED_EVENT_TYPE,
                ticket.getId(),
                metadata);
    }

    /** L3.1-P1: sole producer of {@code TICKET.UPDATED}, called only on real change. */
    private void publishTicketUpdated(Ticket ticket, UUID userId, List<String> changedFields) {
        Map<String, Object> metadata = baseEventMetadata(userId);
        addCustomerIdentifiers(metadata, ticket);
        metadata.put("changedFields", changedFields);
        putIfPresent(metadata, "status", ticket.getStatus() == null ? null : ticket.getStatus().name());

        canonicalCrmEventPublisher.publish(
                ticket.getTenantId(),
                CanonicalCrmEvent.TICKET_ENTITY_TYPE,
                CanonicalCrmEvent.UPDATED_EVENT_TYPE,
                ticket.getId(),
                metadata);
    }

    /** L3.1-P1: sole producer of {@code TICKET.STATUS_CHANGED}. */
    private void publishTicketStatusChanged(
            Ticket ticket, UUID userId, TicketStatus previousStatus, TicketStatus newStatus) {
        Map<String, Object> metadata = baseEventMetadata(userId);
        addCustomerIdentifiers(metadata, ticket);
        metadata.put("previousStatus", previousStatus.name());
        metadata.put("newStatus", newStatus.name());
        putIfPresent(metadata, "resolvedAt", ticket.getResolvedAt());
        putIfPresent(metadata, "closedAt", ticket.getClosedAt());

        canonicalCrmEventPublisher.publish(
                ticket.getTenantId(),
                CanonicalCrmEvent.TICKET_ENTITY_TYPE,
                CanonicalCrmEvent.STATUS_CHANGED_EVENT_TYPE,
                ticket.getId(),
                metadata);
    }

    /**
     * L3.1-P2: sole producer of {@code TICKET.OWNER_CHANGED}, called only when the
     * owner actually moves.
     *
     * <p>Uses {@code previousOwnerId} / {@code newOwnerId} so future workflow and
     * event consumers can rely on that vocabulary. Both go through
     * {@link #putIfPresent}, which is the repository's null-safe metadata behaviour:
     * a ticket with no previous owner simply omits the key rather than writing a
     * null that would break {@code Map.copyOf} in the event record.
     */
    /** L3.1-P1: sole producer of {@code TICKET.DELETED}, called only on live → deleted. */
    private void publishTicketDeleted(Ticket ticket, UUID userId) {
        Map<String, Object> metadata = baseEventMetadata(userId);
        addCustomerIdentifiers(metadata, ticket);
        metadata.put("deleted", Boolean.TRUE);

        canonicalCrmEventPublisher.publish(
                ticket.getTenantId(),
                CanonicalCrmEvent.TICKET_ENTITY_TYPE,
                CanonicalCrmEvent.DELETED_EVENT_TYPE,
                ticket.getId(),
                metadata);
    }

    /**
     * Actor metadata. {@code actorType} is SYSTEM when the mutation was caused by
     * a workflow action, following the L3.0-P6 convention in LeadService.
     */
    private Map<String, Object> baseEventMetadata(UUID userId) {
        boolean workflowCaused = com.shivang.crm.shared.event.CausalEventContext.get() != null;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("actorId", userId.toString());
        metadata.put("actorType", workflowCaused ? "SYSTEM" : "USER");
        metadata.put("source", workflowCaused ? "WORKFLOW" : "MANUAL");
        return metadata;
    }

    /** Stable identifiers only: enough to place the ticket in context, no entity dump. */
    private static void addCustomerIdentifiers(Map<String, Object> metadata, Ticket ticket) {
        putIfPresent(metadata, "accountId", ticket.getAccountId());
        putIfPresent(metadata, "contactId", ticket.getContactId());
        putIfPresent(metadata, "ownerId", ticket.getOwnerId());
    }

    /** Omits absent optional values rather than writing nulls into metadata. */
    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value.toString());
        }
    }

    // ================= Activity =================

    /**
     * Writes the audit row for a Ticket action. {@code activities} is the table
     * every existing domain reads back (there are four {@code /{id}/activities}
     * endpoints), so Ticket lifecycle history is visible without inventing a second
     * audit store or a status-history table.
     */
    private void logTicketActivity(
            Ticket ticket, UUID userId, String activityType, String description, List<String> changedFields) {
        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "accountId", ticket.getAccountId());
        putIfPresent(metadata, "contactId", ticket.getContactId());
        putIfPresent(metadata, "status", ticket.getStatus() == null ? null : ticket.getStatus().name());
        if (changedFields != null && !changedFields.isEmpty()) {
            metadata.put("changedFields", changedFields);
        }
        activityService.logActivity(
                ticket.getTenantId(), ticket.getId(), "TICKET", activityType, description, userId, metadata);
    }
}
