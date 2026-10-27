package com.shivang.crm.modules.ticket.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketStatus;

public class TicketSpecifications {

    public static Specification<Ticket> byTenantId(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    /**
     * L3.0-P2 convention: soft delete is not enforced by an ORM filter here, so
     * every live read must exclude deleted rows explicitly. Mirrors
     * {@code DealSpecifications.notDeleted} and {@code OfferingSpecifications.notDeleted}.
     */
    public static Specification<Ticket> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Ticket> byAccountId(UUID accountId) {
        return (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
    }

    public static Specification<Ticket> byContactId(UUID contactId) {
        return (root, query, cb) -> cb.equal(root.get("contactId"), contactId);
    }

    public static Specification<Ticket> byOwnerUserId(UUID ownerUserId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerUserId);
    }

    public static Specification<Ticket> byStatus(TicketStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * L3.1-P4: tickets eligible for a breach decision right now.
     *
     * <p>Eligible means still open work with an overdue deadline. RESOLVED and CLOSED
     * are excluded here rather than only in the entity, so the scan does not fetch
     * rows it would then reject.
     *
     * <p>Deliberately not tenant-scoped: the scheduler runs across all tenants and
     * each ticket carries its own. This matches {@code EntitlementExpiryService},
     * whose expiry query is equally tenant-blind.
     *
     * <p>{@code slaBreached = false} is the idempotency boundary. A second scan
     * returns nothing, so no duplicate event is possible regardless of how often the
     * job runs.
     */
    public static Specification<Ticket> slaBreachCandidates(Instant now) {
        Instant nowParam = now;
        return (root, query, cb) -> {
            var openWork = root.get("status").in(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);

            var resolutionOverdue = cb.and(
                    root.get("resolutionDueAt").isNotNull(),
                    cb.lessThanOrEqualTo(root.get("resolutionDueAt"), nowParam));

            var responseOverdue = cb.and(
                    root.get("firstResponseAt").isNull(),
                    root.get("responseDueAt").isNotNull(),
                    cb.lessThanOrEqualTo(root.get("responseDueAt"), nowParam));

            return cb.and(
                    cb.isFalse(root.get("deleted")),
                    cb.isFalse(root.get("slaBreached")),
                    openWork,
                    cb.or(resolutionOverdue, responseOverdue));
        };
    }

    /** Case-insensitive match on the subject, which is what an agent searches by. */
    public static Specification<Ticket> searchBySubject(String searchTerm) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("subject")), "%" + searchTerm.toLowerCase() + "%");
    }

    /**
     * L3.1-P1: record-scope filter.
     *
     * <p>Structurally identical to the six existing {@code visibleToUser}
     * specifications (Deal, Lead, Account, Contact, Task, CustomerEntitlement), and
     * deliberately so: {@code RecordScopeGuard} is the single authority for what a
     * scope means, so its semantics must not drift here.
     *
     * <ul>
     *   <li>{@code ALL} — every ticket in the tenant.</li>
     *   <li>{@code TEAM} — owned by a direct report, or owned by, or created by,
     *       the caller. The self-check is required because
     *       {@code teamMemberIds} returns a manager's direct reports and does not
     *       include the manager.</li>
     *   <li>{@code OWN} — owned by or created by the caller.</li>
     *   <li>anything else — no rows, matching the guard's fail-closed behaviour.</li>
     * </ul>
     *
     * <p>This filter narrows rows WITHIN the tenant. It never substitutes for the
     * tenant predicate, which must always be present alongside it.
     */
    public static Specification<Ticket> visibleToUser(
            String scope,
            UUID userId,
            List<UUID> teamUserIds
    ) {
        return (root, query, cb) -> switch (scope) {
            case "ALL" -> cb.conjunction();
            case "TEAM" -> cb.or(
                    root.get("ownerId").in(teamUserIds),
                    cb.equal(root.get("ownerId"), userId),
                    cb.equal(root.get("createdBy"), userId)
            );
            case "OWN" -> cb.or(
                    cb.equal(root.get("ownerId"), userId),
                    cb.equal(root.get("createdBy"), userId)
            );
            default -> cb.disjunction();
        };
    }
}
