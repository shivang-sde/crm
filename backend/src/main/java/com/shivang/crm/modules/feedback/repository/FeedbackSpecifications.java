package com.shivang.crm.modules.feedback.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.shivang.crm.modules.feedback.entity.Feedback;
import com.shivang.crm.modules.feedback.entity.FeedbackSource;
import com.shivang.crm.modules.feedback.entity.FeedbackType;

/**
 * L3.3-P1: read predicates for Feedback.
 *
 * <p>Structurally identical to the six existing {@code visibleToUser} specifications
 * (Deal, Lead, Account, Contact, Task, CustomerEntitlement, Ticket) and deliberately
 * so: {@code RecordScopeGuard} is the single authority for what a scope means, so its
 * semantics must not be restated an eighth time.
 *
 * <p>The owner/creator filter works here because {@link Feedback} inherits
 * {@code ownerId} from {@code TenantOwnedEntity} and populates it from the parent
 * Account at submission. Feedback submitted through an external system has no
 * natural submitting CRM user, so without that inheritance an OWN-scoped employee
 * would see nothing at all.
 */
public class FeedbackSpecifications {

    public static Specification<Feedback> byTenantId(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    /**
     * Soft delete is conventional here (no {@code @SQLDelete} / {@code @Where}), so
     * live reads must exclude deleted rows explicitly.
     */
    public static Specification<Feedback> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Feedback> byAccountId(UUID accountId) {
        return (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
    }

    public static Specification<Feedback> byTicketId(UUID ticketId) {
        return (root, query, cb) -> cb.equal(root.get("ticketId"), ticketId);
    }

    public static Specification<Feedback> byType(FeedbackType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    /**
     * Customer-originated feedback only.
     *
     * <p>{@code INTERNAL} is feedback captured by CRM staff, and a null source is
     * unattributed/legacy data. Neither is evidence of what a customer said, so
     * neither may feed customer risk rules or Voice-of-Customer aggregates. This
     * is a deliberately conservative predicate: an unknown source is not promoted
     * to "customer" just because it might have been one.
     */
    public static Specification<Feedback> customerOriginated() {
        return (root, query, cb) -> {
            var source = root.<FeedbackSource>get("source");
            return cb.and(cb.isNotNull(source), cb.notEqual(source, FeedbackSource.INTERNAL));
        };
    }

    /**
     * Keep only each account's latest customer-originated feedback row.
     *
     * <p>The tenant, soft-delete and scope predicates are repeated inside the
     * correlated subquery on purpose. A subquery that only compared account ids
     * would let a newer row outside the caller's scope (or another tenant) decide
     * which row this caller sees. The subquery instead answers the same question
     * the caller's own read would: "the newest customer feedback I can see for
     * this account."
     *
     * <p>Callers that list multiple accounts must still de-duplicate the returned
     * rows by account, because equal {@code createdAt} values can produce a tie.
     */
    public static Specification<Feedback> latestCustomerFeedbackPerAccount(
            UUID tenantId,
            String scope,
            UUID userId,
            List<UUID> teamUserIds) {

        Specification<Feedback> visible = Specification.allOf(
                byTenantId(tenantId),
                notDeleted(),
                customerOriginated(),
                visibleToUser(scope, userId, teamUserIds));

        return (root, query, cb) -> {
            var latest = query.subquery(Instant.class);
            var candidate = latest.from(Feedback.class);
            latest.select(cb.greatest(candidate.<Instant>get("createdAt")))
                    .where(cb.and(
                            cb.equal(candidate.get("accountId"), root.get("accountId")),
                            visible.toPredicate(candidate, query, cb)));
            return cb.equal(root.get("createdAt"), latest);
        };
    }

    /**
     * Record-scope filter.
     *
     * <ul>
     *   <li>{@code ALL} — every feedback row in the tenant.</li>
     *   <li>{@code TEAM} — owned by a direct report, or owned by, or created by,
     *       the caller. The self-check is required because {@code teamMemberIds}
     *       returns a manager's direct reports and does not include the manager.</li>
     *   <li>{@code OWN} — owned by or created by the caller.</li>
     *   <li>anything else — no rows, matching the guard's fail-closed behaviour.</li>
     * </ul>
     *
     * <p>Never substitutes for the tenant predicate, which is always composed
     * alongside it.
     */
    public static Specification<Feedback> visibleToUser(
            String scope,
            UUID userId,
            List<UUID> teamUserIds) {
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

    /**
     * Newest first with a stable tiebreaker, applied by the caller as a
     * {@code PageRequest} Sort rather than here.
     *
     * <p>Mutating {@code query.orderBy} from inside a specification is fragile:
     * Spring Data reuses the same {@code CriteriaQuery} shape for the count query,
     * so an ordering added here leaks into counting. {@code createdAt DESC, id DESC}
     * breaks ties on equal timestamps so pagination cannot repeat or skip a row.
     */
    public static Sort newestFirst() {
        return Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id"));
    }
}
