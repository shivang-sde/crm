package com.shivang.crm.modules.feedback.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.feedback.entity.Feedback;
import com.shivang.crm.modules.feedback.entity.FeedbackType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * L3.3-P4: database-side Voice-of-Customer aggregation.
 *
 * <p>One query, grouped by {@code (type, score)}, powers all three metric blocks.
 * Each returned row is already a count — {@code (NPS, 9) -> 14} — so the database
 * does the aggregation and Java only assembles at most a few dozen rows into the
 * response. No Feedback entity is ever loaded, and there is no per-bucket query.
 *
 * <p>Every predicate the query applies is one the read path already uses:
 * tenant, soft-delete, the caller's {@code visibleToUser} scope, the valid
 * score range per type, the date range and the optional account. A caller therefore
 * cannot obtain aggregate metrics over rows they could not read individually.
 *
 * <p>The score-range predicate matters even though submission validates: a row
 * written outside the API (a backfill, a manual fix) must not silently move an
 * average. {@code GENERAL_FEEDBACK} and {@code COMPLAINT} are excluded by the same
 * mechanism — they have no score semantics to aggregate.
 */
@Repository
public class FeedbackAnalyticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * One row per {@code (type, score)} present in the filtered set.
     *
     * @param from inclusive lower bound on {@code createdAt}, or null
     * @param to exclusive upper bound on {@code createdAt}, or null
     * @param accountId optional canonical-customer filter, or null
     */
    public List<ScoreBucket> countByTypeAndScore(
            UUID tenantId,
            UUID userId,
            String scope,
            List<UUID> teamUserIds,
            Instant from,
            Instant to,
            UUID accountId) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ScoreBucket> query = cb.createQuery(ScoreBucket.class);
        Root<Feedback> root = query.from(Feedback.class);

        Predicate base = cb.and(
                FeedbackSpecifications.byTenantId(tenantId).toPredicate(root, query, cb),
                FeedbackSpecifications.notDeleted().toPredicate(root, query, cb),
                FeedbackSpecifications.customerOriginated().toPredicate(root, query, cb),
                FeedbackSpecifications.visibleToUser(scope, userId, teamUserIds)
                        .toPredicate(root, query, cb),
                inValidScoredRange(cb, root));

        if (from != null) {
            base = cb.and(base, cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            base = cb.and(base, cb.lessThan(root.get("createdAt"), to));
        }
        if (accountId != null) {
            base = cb.and(base, FeedbackSpecifications.byAccountId(accountId)
                    .toPredicate(root, query, cb));
        }

        query.select(cb.construct(
                        ScoreBucket.class,
                        root.get("type"),
                        root.get("score"),
                        cb.count(root)))
                .where(base)
                .groupBy(root.get("type"), root.get("score"));

        return entityManager.createQuery(query).getResultList();
    }

    /**
     * Only rows whose score is inside their own type's validated range participate.
     * Submission enforces this, but the read path must not assume the table contains
     * only API-written rows.
     */
    private Predicate inValidScoredRange(CriteriaBuilder cb, Root<Feedback> root) {
        return cb.or(
                cb.and(
                        cb.equal(root.get("type"), FeedbackType.NPS),
                        cb.between(root.get("score"), 0, 10)),
                cb.and(
                        cb.equal(root.get("type"), FeedbackType.CSAT),
                        cb.between(root.get("score"), 1, 5)),
                cb.and(
                        cb.equal(root.get("type"), FeedbackType.CES),
                        cb.between(root.get("score"), 1, 7)));
    }

    /** One pre-aggregated row: how many visible responses gave this score. */
    public record ScoreBucket(FeedbackType type, Integer score, long count) {
    }
}
