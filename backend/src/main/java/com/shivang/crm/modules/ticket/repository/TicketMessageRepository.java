package com.shivang.crm.modules.ticket.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.ticket.entity.TicketMessage;

/**
 * L3.1-P3: TicketMessage persistence.
 *
 * <p>Two rules hold for every finder here, matching {@code TicketRepository}:
 *
 * <ol>
 *   <li><b>Tenant-scoped.</b> A message must never be fetchable by id alone. Its
 *       parent Ticket is what establishes tenancy, and a finder that omitted
 *       {@code tenantId} would let a known message UUID be read across tenants.</li>
 *   <li><b>Deleted-aware.</b> Soft delete is not an ORM-level filter in this codebase
 *       (see {@code TicketSpecifications.notDeleted}), so every live read excludes
 *       deleted rows explicitly.</li>
 * </ol>
 *
 * <p>There is deliberately no {@code findById(UUID)}, no cross-tenant lookup and no
 * cascade delete. Messages outlive their Ticket's closure.
 */
@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {

    /**
     * Live message within a tenant, optionally narrowed to one parent Ticket.
     *
     * <p>The service always supplies {@code ticketId}: a message found under a
     * different parent than the one in the URL is a rejected request, not a match.
     */
    Optional<TicketMessage> findByIdAndTenantIdAndTicketIdAndDeletedFalse(
            UUID id, UUID tenantId, UUID ticketId);

    /** Live message within a tenant, without asserting its parent. */
    Optional<TicketMessage> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    /**
     * A ticket's live conversation, newest first.
     *
     * <p>{@code Pageable} is supplied by the service, which clamps it — see
     * {@code TicketMessageService#listMessages}. No unbounded {@code List} overload
     * exists, because a ticket can accumulate an arbitrary number of messages.
     */
    Page<TicketMessage> findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID ticketId, Pageable pageable);

    /** Author's live messages across the tenant, newest first. */
    List<TicketMessage> findByTenantIdAndAuthorUserIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID authorUserId);
}
