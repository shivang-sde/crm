package com.shivang.crm.modules.ticket.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketStatus;

/**
 * L3.1-P1: Ticket persistence.
 *
 * <p>Every finder is tenant-scoped and deleted-aware. This is not a stylistic
 * choice: {@code RecordScopeGuard} documents that scope NEVER widens the tenant
 * boundary, so the tenant predicate is the only thing standing between one tenant
 * and another's tickets.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    /** Live, tenant-owned lookup. The only way a Ticket should ever be fetched. */
    Optional<Ticket> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    boolean existsByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    /** Account-scoped list, ordered newest first. Used by the account detail view. */
    List<Ticket> findByTenantIdAndAccountIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID accountId);

    List<Ticket> findByTenantIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, TicketStatus status);

    /**
     * Atomically transitions {@code sla_breached} from false to true.
     *
     * <p>Single-statement conditional update, so the row lock is taken and released
     * inside the database and only one concurrent caller can match the predicate. The
     * affected-row count is the arbitration: exactly one caller sees 1 and may publish
     * {@code TICKET.SLA_BREACHED}; every other caller sees 0 and must not.
     *
     * <p>{@code tenantId} and {@code deleted = false} are in the predicate, so this
     * cannot breach another tenant's ticket or resurrect a soft-deleted one. There is
     * deliberately no variant without them.
     *
     * @return 1 for the caller that won the transition, 0 otherwise
     */
    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("""
            UPDATE Ticket t
               SET t.slaBreached = true
            WHERE t.id = :id
              AND t.tenantId = :tenantId
              AND t.deleted = false
              AND t.slaBreached = false
            """)
    int markSlaBreachedIfNotBreached(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}