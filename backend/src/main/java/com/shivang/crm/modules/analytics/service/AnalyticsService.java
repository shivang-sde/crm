package com.shivang.crm.modules.analytics.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.analytics.AnalyticsContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import com.shivang.crm.modules.analytics.AnalyticsDateRange;
import com.shivang.crm.modules.analytics.AnalyticsScope;
import com.shivang.crm.modules.analytics.dto.AnalyticsSummaryResponse;
import com.shivang.crm.modules.analytics.dto.AnalyticsSummaryResponse.ActivityMetrics;
import com.shivang.crm.modules.analytics.dto.AnalyticsSummaryResponse.DealMetrics;
import com.shivang.crm.modules.analytics.dto.AnalyticsSummaryResponse.LeadMetrics;
import com.shivang.crm.modules.analytics.dto.AnalyticsTrendResponse;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.meeting.entity.Meeting;
import com.shivang.crm.modules.task.entity.Task;
import com.shivang.crm.modules.task.entity.TaskStatus;
import com.shivang.crm.modules.tenant.entity.Tenant;
import com.shivang.crm.shared.base.TenantOwnedEntity;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds scoped aggregate summaries with database-side COUNT / SUM queries
 * (Criteria API). No entity hydration, no per-record loops.
 *
 * Timestamp semantics:
 *   leads/contacts/deals/tasks/calls/meetings  – createdAt
 *   newLeads                                   – createdAt
 *   convertedLeads                             – convertedAt
 *   openDeals                                  – stage.recordCategory = OPEN
 *   wonDeals / wonValue                        – stage.recordCategory = CLOSED_WON
 *   lostDeals                                  – stage.recordCategory = CLOSED_LOST
 *   pipelineValue                              – SUM(amount) for open deals
 *   openTasks                                  – createdAt, isClosed != true
 *   completedTasks                             – completedAt
 *   overdueTasks                               – dueDate < now, isClosed != true
 *   heldCalls                                  – status = HELD
 *   heldMeetings                               – status = HELD
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    @PersistenceContext
    private EntityManager em;

    public AnalyticsSummaryResponse getSummary(AnalyticsContext context, AnalyticsDateRange range) {
        long totalLeads = count(Lead.class, context, range.from(), range.to());

        return AnalyticsSummaryResponse.builder()
                .scope(context.scope())
                .from(range.from())
                .to(range.to())
                // Basic counts (AN-2, unchanged)
                .leads(totalLeads)
                .contacts(count(Contact.class, context, range.from(), range.to()))
                .deals(count(Deal.class, context, range.from(), range.to()))
                .tasks(count(Task.class, context, range.from(), range.to()))
                .calls(count(Call.class, context, range.from(), range.to()))
                .meetings(count(Meeting.class, context, range.from(), range.to()))
                // Expanded metrics (AN-3)
                .leadMetrics(leadMetrics(context, range, totalLeads))
                .dealMetrics(dealMetrics(context, range))
                .activityMetrics(activityMetrics(context, range))
                .build();
    }

    // ======================== Lead Metrics ========================

    private LeadMetrics leadMetrics(AnalyticsContext ctx, AnalyticsDateRange range, long totalLeads) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Lead> root = q.from(Lead.class);

        List<Predicate> predicates = basePredicates(root, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(root.get("createdAt"), range.to()));
        predicates.add(cb.equal(root.get("isConverted"), true));
        predicates.add(cb.isNotNull(root.get("convertedAt")));

        q.select(cb.count(root)).where(predicates.toArray(new Predicate[0]));
        long converted = nullToZero(em.createQuery(q).getSingleResult());
        double rate = totalLeads > 0 ? (converted * 100.0 / totalLeads) : 0.0;

        return LeadMetrics.builder()
                .newLeads(totalLeads)
                .convertedLeads(converted)
                .conversionRate(rate)
                .build();
    }

    // ======================== Deal Metrics ========================

    private DealMetrics dealMetrics(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Deal> root = q.from(Deal.class);
        Join<Deal, DealStage> stage = root.join("stage");

        List<Predicate> predicates = basePredicates(root, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(root.get("createdAt"), range.to()));

        CriteriaBuilder.Case<Long> openCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.OPEN), 1L);
        CriteriaBuilder.Case<Long> wonCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_WON), 1L);
        CriteriaBuilder.Case<Long> lostCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_LOST), 1L);
        Expression<BigDecimal> amountExpr = root.<BigDecimal>get("amount");

        Expression<Long> openCount = cb.sum(openCase.otherwise(0L));
        Expression<Long> wonCount = cb.sum(wonCase.otherwise(0L));
        Expression<Long> lostCount = cb.sum(lostCase.otherwise(0L));

        CriteriaBuilder.Case<BigDecimal> pipelineCase = cb.<BigDecimal>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.OPEN), amountExpr);
        CriteriaBuilder.Case<BigDecimal> wonAmountCase = cb.<BigDecimal>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_WON), amountExpr);

        Expression<BigDecimal> pipelineSum = cb.coalesce(cb.sum(pipelineCase.otherwise(BigDecimal.ZERO)), BigDecimal.ZERO);
        Expression<BigDecimal> wonSum = cb.coalesce(cb.sum(wonAmountCase.otherwise(BigDecimal.ZERO)), BigDecimal.ZERO);

        q.multiselect(openCount, wonCount, lostCount, pipelineSum, wonSum);
        q.where(predicates.toArray(new Predicate[0]));

        Object[] row = em.createQuery(q).getSingleResult();
        long open = nullToZero((Long) row[0]);
        long won = nullToZero((Long) row[1]);
        long lost = nullToZero((Long) row[2]);
        BigDecimal pipeline = (BigDecimal) row[3];
        BigDecimal wonValue = (BigDecimal) row[4];
        double winRate = (won + lost) > 0 ? (won * 100.0 / (won + lost)) : 0.0;

        return DealMetrics.builder()
                .openDeals(open)
                .wonDeals(won)
                .lostDeals(lost)
                .pipelineValue(pipeline)
                .wonValue(wonValue)
                .winRate(winRate)
                .build();
    }

    // ======================== Activity Metrics ========================

    private ActivityMetrics activityMetrics(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Task> root = q.from(Task.class);

        List<Predicate> predicates = basePredicates(root, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(root.get("createdAt"), range.to()));

        // openTasks: created in range, not closed
        List<Predicate> openPredicates = new ArrayList<>(predicates);
        Predicate notClosed = cb.or(
                cb.equal(root.get("isClosed"), false),
                cb.isNull(root.get("isClosed")));
        openPredicates.add(notClosed);
        q.select(cb.count(root)).where(openPredicates.toArray(new Predicate[0]));
        long openTasks = nullToZero(em.createQuery(q).getSingleResult());

        // completedTasks: completedAt in range
        List<Predicate> completedPredicates = new ArrayList<>(predicates);
        completedPredicates.add(cb.equal(root.get("status"), TaskStatus.COMPLETED));
        completedPredicates.add(cb.greaterThanOrEqualTo(root.get("completedAt"), range.from()));
        completedPredicates.add(cb.lessThan(root.get("completedAt"), range.to()));
        q.where(completedPredicates.toArray(new Predicate[0]));
        long completedTasks = nullToZero(em.createQuery(q).getSingleResult());

        // overdueTasks: created in range, not completed, dueDate < now
        List<Predicate> overduePredicates = new ArrayList<>(predicates);
        overduePredicates.add(notClosed);
        overduePredicates.add(cb.isNotNull(root.get("dueDate")));
        overduePredicates.add(cb.lessThan(root.get("dueDate"), Instant.now()));
        q.where(overduePredicates.toArray(new Predicate[0]));
        long overdueTasks = nullToZero(em.createQuery(q).getSingleResult());

        return ActivityMetrics.builder()
                .openTasks(openTasks)
                .completedTasks(completedTasks)
                .overdueTasks(overdueTasks)
                .build();
    }

    // ======================== Call / Meeting extras ========================

    private long heldCalls(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Call> root = q.from(Call.class);

        List<Predicate> predicates = basePredicates(root, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(root.get("createdAt"), range.to()));
        predicates.add(cb.equal(root.get("status"), com.shivang.crm.modules.call.entity.Call.CallStatus.HELD));

        q.select(cb.count(root)).where(predicates.toArray(new Predicate[0]));
        return nullToZero(em.createQuery(q).getSingleResult());
    }

    private long heldMeetings(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Meeting> root = q.from(Meeting.class);

        List<Predicate> predicates = basePredicates(root, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(root.get("createdAt"), range.to()));
        predicates.add(cb.equal(root.get("status"), com.shivang.crm.modules.meeting.entity.Meeting.MeetingStatus.HELD));

        q.select(cb.count(root)).where(predicates.toArray(new Predicate[0]));
        return nullToZero(em.createQuery(q).getSingleResult());
    }

    // ======================== Shared scoping helpers ========================

    /**
     * Scope predicates identical to AN-1's count() method. Shared by all
     * aggregate queries to ensure consistent tenant/reseller/user isolation.
     */
    private <T extends TenantOwnedEntity> List<Predicate> basePredicates(
            Root<T> root, CriteriaBuilder cb, AnalyticsContext ctx) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get("deleted")));

        switch (ctx.scope()) {
            case TENANT -> predicates.add(
                    cb.equal(root.<UUID>get("tenantId"), ctx.tenantId()));
            case USER -> {
                predicates.add(cb.equal(root.<UUID>get("tenantId"), ctx.tenantId()));
                predicates.add(cb.or(
                        cb.equal(root.<UUID>get("ownerId"), ctx.userId()),
                        cb.equal(root.<UUID>get("createdBy"), ctx.userId())));
            }
            case RESELLER -> predicates.add(root.<UUID>get("tenantId").in(
                    resellerTenantIds(ctx.resellerId())));
            case PLATFORM -> {
                // SUPERADMIN: all non-deleted records.
            }
        }
        return predicates;
    }

    private <T extends TenantOwnedEntity> long count(
            Class<T> entityType, AnalyticsContext ctx, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> root = query.from(entityType);

        List<Predicate> predicates = basePredicates(root, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        predicates.add(cb.lessThan(root.get("createdAt"), to));

        query.select(cb.count(root)).where(predicates.toArray(new Predicate[0]));
        Long result = em.createQuery(query).getSingleResult();
        log.debug("Analytics count {} [{} {} {}] = {}", entityType.getSimpleName(),
                ctx.scope(), from, to, result);
        return nullToZero(result);
    }

    private Subquery<UUID> resellerTenantIds(UUID resellerId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<?> outer = cb.createQuery();
        Subquery<UUID> subquery = outer.subquery(UUID.class);
        Root<Tenant> tenant = subquery.from(Tenant.class);
        subquery.select(tenant.get("id"))
                .where(cb.equal(tenant.<UUID>get("resellerId"), resellerId));
        return subquery;
    }

    private static long nullToZero(Long value) {
        return value != null ? value : 0L;
    }

    // ======================== Trends (AN-4) ========================

    /**
     * Returns time-bucketed counts for leads, contacts, deals and tasks.
     * Bucket granularity is auto-selected: DAY for ≤31 days,
     * WEEK for 32–180 days, MONTH for >180 days.
     *
     * Uses a native SQL UNION ALL query for clean date_trunc support.
     * Scope filtering mirrors {@link #basePredicates} via dynamic WHERE clauses.
     */
    public List<AnalyticsTrendResponse> getTrends(AnalyticsContext ctx, AnalyticsDateRange range) {
        long days = range.from().until(range.to(), ChronoUnit.DAYS);
        String unit = days <= 31 ? "day" : days <= 180 ? "week" : "month";

        String scopeFilter = buildScopeFilter(ctx);

        String sql = """
                SELECT bucket, SUM(leads) AS leads, SUM(contacts) AS contacts,
                       SUM(deals) AS deals, SUM(tasks) AS tasks
                FROM (
                    SELECT date_trunc(:unit, created_at) AS bucket,
                           COUNT(*) AS leads, 0::bigint AS contacts, 0::bigint AS deals, 0::bigint AS tasks
                    FROM leads WHERE deleted = false AND %s AND created_at >= :from AND created_at < :to
                    GROUP BY bucket
                    UNION ALL
                    SELECT date_trunc(:unit, created_at) AS bucket,
                           0::bigint AS leads, COUNT(*) AS contacts, 0::bigint AS deals, 0::bigint AS tasks
                    FROM contacts WHERE deleted = false AND %s AND created_at >= :from AND created_at < :to
                    GROUP BY bucket
                    UNION ALL
                    SELECT date_trunc(:unit, created_at) AS bucket,
                           0::bigint AS leads, 0::bigint AS contacts, COUNT(*) AS deals, 0::bigint AS tasks
                    FROM deals WHERE deleted = false AND %s AND created_at >= :from AND created_at < :to
                    GROUP BY bucket
                    UNION ALL
                    SELECT date_trunc(:unit, created_at) AS bucket,
                           0::bigint AS leads, 0::bigint AS contacts, 0::bigint AS deals, COUNT(*) AS tasks
                    FROM tasks WHERE deleted = false AND %s AND created_at >= :from AND created_at < :to
                    GROUP BY bucket
                ) combined
                GROUP BY bucket
                ORDER BY bucket
                """.formatted(scopeFilter, scopeFilter, scopeFilter, scopeFilter);

        @SuppressWarnings("unchecked")
        jakarta.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("unit", unit);
        query.setParameter("from", range.from());
        query.setParameter("to", range.to());

        List<Object[]> rows = query.getResultList();
        List<AnalyticsTrendResponse> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            result.add(AnalyticsTrendResponse.builder()
                    .bucket(instantFromTimestamp(row[0]))
                    .leads(nullToZero(asLong(row[1])))
                    .contacts(nullToZero(asLong(row[2])))
                    .deals(nullToZero(asLong(row[3])))
                    .tasks(nullToZero(asLong(row[4])))
                    .build());
        }
        return result;
    }

    /**
     * PostgreSQL timestamp columns map to java.time.LocalDateTime here, but
     * some drivers return java.sql.Timestamp. Both encode UTC wall-clock time.
     */
    private static Instant instantFromTimestamp(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toInstant(java.time.ZoneOffset.UTC);
        }
        return (Instant) value;
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String buildScopeFilter(AnalyticsContext ctx) {
        return switch (ctx.scope()) {
            case PLATFORM -> "1=1";
            case RESELLER -> "tenant_id IN (SELECT id FROM tenants WHERE reseller_id = '%s')"
                    .formatted(ctx.resellerId());
            case TENANT -> "tenant_id = '%s'".formatted(ctx.tenantId());
            case USER -> "tenant_id = '%s' AND (owner_id = '%s' OR created_by = '%s')"
                    .formatted(ctx.tenantId(), ctx.userId(), ctx.userId());
        };
    }
}
