package com.shivang.crm.modules.analytics.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.shivang.crm.modules.analytics.dto.AccountsByOwnerRow;
import com.shivang.crm.modules.analytics.dto.ActivityRatesSummary;
import com.shivang.crm.modules.analytics.dto.CallDurationSummary;
import com.shivang.crm.modules.analytics.dto.CallStatusSummary;
import com.shivang.crm.modules.analytics.dto.ContactsPerAccountRow;
import com.shivang.crm.modules.analytics.dto.ConversionOwnerRow;
import com.shivang.crm.modules.analytics.dto.ConversionPeriodSummary;
import com.shivang.crm.modules.analytics.dto.CurrentStageAgeSummary;
import com.shivang.crm.modules.analytics.dto.DealAgingRow;
import com.shivang.crm.modules.analytics.dto.ForecastCategoryRow;
import com.shivang.crm.modules.analytics.dto.LeadSourcePerformanceRow;
import com.shivang.crm.modules.analytics.dto.PipelineAccountRow;
import com.shivang.crm.modules.analytics.dto.PipelineOwnerRow;
import com.shivang.crm.modules.analytics.dto.PipelineStageRow;
import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.meeting.entity.Meeting;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.task.entity.Task;
import com.shivang.crm.modules.task.entity.TaskStatus;
import com.shivang.crm.modules.tenant.entity.Tenant;
import com.shivang.crm.shared.base.TenantOwnedEntity;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds scoped aggregate summaries with database-side COUNT / SUM queries
 * (Criteria API). No entity hydration, no per-record loops.
 *
 * Timestamp semantics (created-window convention — a record belongs to the
 * selected period in which it was created; this matches the basic counts and
 * the trends buckets):
 *   leads/contacts/deals/tasks/calls/meetings  – createdAt
 *   newLeads                                   – createdAt
 *   convertedLeads                             – createdAt of newLeads that
 *                                                have since converted (not
 *                                                "converted during the period")
 *   openDeals                                  – stage.recordCategory = OPEN
 *   wonDeals / wonValue                        – stage.recordCategory = CLOSED_WON
 *   lostDeals                                  – stage.recordCategory = CLOSED_LOST
 *   pipelineValue                              – SUM(amount) for open deals
 *   openTasks                                  – createdAt, isClosed != true
 *   completedTasks                             – createdAt (tasks created in the
 *                                                selected period that were also
 *                                                completed within it)
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

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public AnalyticsService(PermissionEvaluatorService permissionEvaluatorService,
                            UserRepository userRepository,
                            AccountRepository accountRepository) {
        this.permissionEvaluatorService = permissionEvaluatorService;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

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

        // convertedLeads: newLeads (created in the selected period) that have
        // since converted. Created-window semantics — a lead created in the
        // period counts even if it converts after the period ends.
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

        // completedTasks: created in range AND completed within the same range
        // (created-window convention; tasks created before the range are excluded)
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

    // ======================== Grouped analytics (AN-13) ========================

    /**
     * AN-13 A1: aggregate deal rows grouped by current stage. Same created-window,
     * current-stage and scope predicates as the summary, so row totals reconcile
     * with {@link AnalyticsSummaryResponse.DealMetrics}.
     */
    public List<PipelineStageRow> getPipelineByStage(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Deal> deal = q.from(Deal.class);
        Join<Deal, DealStage> stage = deal.join("stage");

        List<Predicate> predicates = basePredicates(deal, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(deal.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(deal.get("createdAt"), range.to()));

        Expression<BigDecimal> amount = deal.get("amount");
        CriteriaBuilder.Case<Long> openCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.OPEN), 1L);
        CriteriaBuilder.Case<Long> wonCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_WON), 1L);
        CriteriaBuilder.Case<Long> lostCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_LOST), 1L);
        CriteriaBuilder.Case<BigDecimal> pipelineCase = cb.<BigDecimal>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.OPEN), amount);
        CriteriaBuilder.Case<BigDecimal> wonAmountCase = cb.<BigDecimal>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_WON), amount);

        Expression<Long> totalCount = cb.count(deal);
        q.multiselect(
                stage.get("id"),
                stage.get("name"),
                cb.sum(openCase.otherwise(0L)),
                cb.sum(wonCase.otherwise(0L)),
                cb.sum(lostCase.otherwise(0L)),
                cb.sum(pipelineCase.otherwise(BigDecimal.ZERO)),
                cb.sum(wonAmountCase.otherwise(BigDecimal.ZERO)),
                totalCount);
        q.where(predicates.toArray(new Predicate[0]));
        q.groupBy(stage.get("id"), stage.get("name"));
        q.orderBy(cb.asc(stage.get("displayOrder")), cb.asc(stage.get("name")));

        List<Object[]> rows = em.createQuery(q).getResultList();
        List<PipelineStageRow> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            result.add(PipelineStageRow.builder()
                    .stageId((UUID) row[0])
                    .stageName((String) row[1])
                    .openCount(nullToZero(asLong(row[2])))
                    .wonCount(nullToZero(asLong(row[3])))
                    .lostCount(nullToZero(asLong(row[4])))
                    .pipelineValue(orZero((BigDecimal) row[5]))
                    .wonValue(orZero((BigDecimal) row[6]))
                    .totalCount(nullToZero(asLong(row[7])))
                    .build());
        }
        return result;
    }

    /**
     * AN-13 A2: aggregate deal rows grouped by owner, derived only from deals
     * already inside the caller's resolved analytics scope. No ownerId filter is
     * accepted; the group dimension is the {@code ownerId} column of those
     * authorized records. A null-owner group keeps reconciliation with
     * {@code dealMetrics} exact.
     */
    public List<PipelineOwnerRow> getPipelineByOwner(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Deal> deal = q.from(Deal.class);
        Join<Deal, DealStage> stage = deal.join("stage");

        List<Predicate> predicates = basePredicates(deal, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(deal.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(deal.get("createdAt"), range.to()));

        Expression<BigDecimal> amount = deal.get("amount");
        CriteriaBuilder.Case<Long> openCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.OPEN), 1L);
        CriteriaBuilder.Case<Long> wonCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_WON), 1L);
        CriteriaBuilder.Case<Long> lostCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_LOST), 1L);
        CriteriaBuilder.Case<BigDecimal> pipelineCase = cb.<BigDecimal>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.OPEN), amount);
        CriteriaBuilder.Case<BigDecimal> wonAmountCase = cb.<BigDecimal>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_WON), amount);

        Expression<Long> totalCount = cb.count(deal);
        q.multiselect(
                deal.get("ownerId"),
                cb.sum(openCase.otherwise(0L)),
                cb.sum(wonCase.otherwise(0L)),
                cb.sum(lostCase.otherwise(0L)),
                cb.sum(pipelineCase.otherwise(BigDecimal.ZERO)),
                cb.sum(wonAmountCase.otherwise(BigDecimal.ZERO)),
                totalCount);
        q.where(predicates.toArray(new Predicate[0]));
        q.groupBy(deal.get("ownerId"));
        q.orderBy(cb.desc(totalCount));

        List<Object[]> rows = em.createQuery(q).getResultList();
        List<PipelineOwnerRow> result = new ArrayList<>(rows.size());
        List<UUID> ownerIds = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            UUID ownerId = (UUID) row[0];
            if (ownerId != null) {
                ownerIds.add(ownerId);
            }
            result.add(PipelineOwnerRow.builder()
                    .ownerUserId(ownerId)
                    .openCount(nullToZero(asLong(row[1])))
                    .wonCount(nullToZero(asLong(row[2])))
                    .lostCount(nullToZero(asLong(row[3])))
                    .pipelineValue(orZero((BigDecimal) row[4]))
                    .wonValue(orZero((BigDecimal) row[5]))
                    .totalCount(nullToZero(asLong(row[6])))
                    .build());
        }
        Map<UUID, String> names = ownerDisplayNames(ownerIds);
        for (PipelineOwnerRow row : result) {
            if (row.getOwnerUserId() != null) {
                row.setOwnerDisplayName(names.get(row.getOwnerUserId()));
            }
        }
        return result;
    }

    /**
     * AN-13 A3: aggregate deal rows grouped by account, derived only from deals
     * inside the caller's resolved analytics scope. Deals without an account are
     * excluded, so the summed totalCount is <= the authorized deal population.
     */
    public List<PipelineAccountRow> getPipelineByAccount(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Deal> deal = q.from(Deal.class);
        Join<Deal, DealStage> stage = deal.join("stage");

        List<Predicate> predicates = basePredicates(deal, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(deal.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(deal.get("createdAt"), range.to()));
        predicates.add(cb.isNotNull(deal.get("accountId")));

        Expression<BigDecimal> amount = deal.get("amount");
        CriteriaBuilder.Case<Long> openCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.OPEN), 1L);
        CriteriaBuilder.Case<Long> wonCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_WON), 1L);
        CriteriaBuilder.Case<Long> lostCase = cb.<Long>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_LOST), 1L);
        CriteriaBuilder.Case<BigDecimal> pipelineCase = cb.<BigDecimal>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.OPEN), amount);
        CriteriaBuilder.Case<BigDecimal> wonAmountCase = cb.<BigDecimal>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_WON), amount);

        Expression<Long> totalCount = cb.count(deal);
        q.multiselect(
                deal.get("accountId"),
                cb.sum(openCase.otherwise(0L)),
                cb.sum(wonCase.otherwise(0L)),
                cb.sum(lostCase.otherwise(0L)),
                cb.sum(pipelineCase.otherwise(BigDecimal.ZERO)),
                cb.sum(wonAmountCase.otherwise(BigDecimal.ZERO)),
                totalCount);
        q.where(predicates.toArray(new Predicate[0]));
        q.groupBy(deal.get("accountId"));
        q.orderBy(cb.desc(totalCount));

        List<Object[]> rows = em.createQuery(q).getResultList();
        List<PipelineAccountRow> result = new ArrayList<>(rows.size());
        List<UUID> accountIds = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            UUID accountId = (UUID) row[0];
            accountIds.add(accountId);
            result.add(PipelineAccountRow.builder()
                    .accountId(accountId)
                    .openCount(nullToZero(asLong(row[1])))
                    .wonCount(nullToZero(asLong(row[2])))
                    .lostCount(nullToZero(asLong(row[3])))
                    .pipelineValue(orZero((BigDecimal) row[4]))
                    .wonValue(orZero((BigDecimal) row[5]))
                    .totalCount(nullToZero(asLong(row[6])))
                    .build());
        }
        Map<UUID, String> names = accountNames(accountIds);
        for (PipelineAccountRow row : result) {
            row.setAccountName(names.get(row.getAccountId()));
        }
        return result;
    }

    /**
     * AN-13 A4: lead conversion aggregates grouped by owner. Preserves the
     * AN-10.1 created-window conversion semantics (newLeadCount = created in
     * period; convertedLeadCount = those same leads that have since converted).
     * Grouping comes only from the authorized lead set.
     */
    public List<ConversionOwnerRow> getConversionByOwner(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Lead> lead = q.from(Lead.class);

        List<Predicate> predicates = basePredicates(lead, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(lead.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(lead.get("createdAt"), range.to()));

        CriteriaBuilder.Case<Long> convertedCase = cb.<Long>selectCase()
                .when(cb.and(
                        cb.equal(lead.get("isConverted"), true),
                        cb.isNotNull(lead.get("convertedAt"))), 1L);

        q.multiselect(lead.get("ownerId"), cb.count(lead), cb.sum(convertedCase.otherwise(0L)));
        q.where(predicates.toArray(new Predicate[0]));
        q.groupBy(lead.get("ownerId"));

        List<Object[]> rows = em.createQuery(q).getResultList();
        List<ConversionOwnerRow> result = new ArrayList<>(rows.size());
        List<UUID> ownerIds = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            UUID ownerId = (UUID) row[0];
            long newCount = nullToZero(asLong(row[1]));
            long converted = nullToZero(asLong(row[2]));
            if (ownerId != null) {
                ownerIds.add(ownerId);
            }
            double rate = newCount > 0 ? converted * 100.0 / newCount : 0.0;
            result.add(ConversionOwnerRow.builder()
                    .ownerUserId(ownerId)
                    .newLeadCount(newCount)
                    .convertedLeadCount(converted)
                    .conversionRate(rate)
                    .build());
        }
        Map<UUID, String> names = ownerDisplayNames(ownerIds);
        for (ConversionOwnerRow row : result) {
            if (row.getOwnerUserId() != null) {
                row.setOwnerDisplayName(names.get(row.getOwnerUserId()));
            }
        }
        return result;
    }

    /**
     * AN-13 A5: age buckets for OPEN deals created within the selected period.
     * Age basis is now() - created_at measured in days at request time (no
     * historical aging). All five fixed buckets are always returned; empty
     * buckets carry zero counts so the UI is stable.
     */
    public List<DealAgingRow> getDealAging(AnalyticsContext ctx, AnalyticsDateRange range) {
        String scopeFilter = buildScopeFilter(ctx);
        String sql = """
                SELECT bucket, deal_count, pipeline_value
                FROM (
                    SELECT CASE
                               WHEN age_days <= 7 THEN '0-7'
                               WHEN age_days <= 30 THEN '8-30'
                               WHEN age_days <= 60 THEN '31-60'
                               WHEN age_days <= 90 THEN '61-90'
                               ELSE '90+'
                           END AS bucket,
                           COUNT(*) AS deal_count,
                           COALESCE(SUM(amount), 0) AS pipeline_value
                    FROM (
                        SELECT d.id AS deal_id,
                               d.amount AS amount,
                               EXTRACT(EPOCH FROM (now() - d.created_at)) / 86400.0 AS age_days
                        FROM deals d
                        WHERE d.deleted = false AND %s
                          AND d.created_at >= :from AND d.created_at < :to
                          AND d.stage_id IN (SELECT s.id FROM deal_stages s WHERE s.record_category = 'OPEN')
                    ) AS open_deals
                    GROUP BY bucket
                ) AS aged
                """.formatted(scopeFilter);

        jakarta.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("from", range.from());
        query.setParameter("to", range.to());

        List<Object[]> rows = query.getResultList();
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (String bucket : AGING_BUCKETS) {
            counts.put(bucket, 0L);
            values.put(bucket, BigDecimal.ZERO);
        }
        for (Object[] row : rows) {
            String bucket = (String) row[0];
            counts.put(bucket, nullToZero(asLong(row[1])));
            values.put(bucket, orZero((BigDecimal) row[2]));
        }
        List<DealAgingRow> result = new ArrayList<>(AGING_BUCKETS.size());
        for (String bucket : AGING_BUCKETS) {
            result.add(DealAgingRow.builder()
                    .bucket(bucket)
                    .count(counts.get(bucket))
                    .pipelineValue(values.get(bucket))
                    .build());
        }
        return result;
    }

    private static final List<String> AGING_BUCKETS = List.of("0-7", "8-30", "31-60", "61-90", "90+");

    /**
     * AN-13 A6: minimal call status summary for calls created in the selected
     * period and inside the caller's resolved analytics scope. heldRate =
     * held / (held + notHeld + cancelled) * 100 (planned/scheduled calls are
     * deliberately excluded from the denominator); 0 on a zero denominator.
     */
    public CallStatusSummary getCallStatus(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Call> call = q.from(Call.class);

        List<Predicate> predicates = basePredicates(call, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(call.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(call.get("createdAt"), range.to()));

        CriteriaBuilder.Case<Long> plannedCase = cb.<Long>selectCase()
                .when(cb.equal(call.get("status"), Call.CallStatus.PLANNED), 1L);
        CriteriaBuilder.Case<Long> heldCase = cb.<Long>selectCase()
                .when(cb.equal(call.get("status"), Call.CallStatus.HELD), 1L);
        CriteriaBuilder.Case<Long> notHeldCase = cb.<Long>selectCase()
                .when(cb.equal(call.get("status"), Call.CallStatus.NOT_HELD), 1L);
        CriteriaBuilder.Case<Long> cancelledCase = cb.<Long>selectCase()
                .when(cb.equal(call.get("status"), Call.CallStatus.CANCELLED), 1L);

        q.multiselect(
                cb.sum(plannedCase.otherwise(0L)),
                cb.sum(heldCase.otherwise(0L)),
                cb.sum(notHeldCase.otherwise(0L)),
                cb.sum(cancelledCase.otherwise(0L)));
        q.where(predicates.toArray(new Predicate[0]));

        Object[] row = em.createQuery(q).getSingleResult();
        long planned = nullToZero(asLong(row[0]));
        long held = nullToZero(asLong(row[1]));
        long notHeld = nullToZero(asLong(row[2]));
        long cancelled = nullToZero(asLong(row[3]));
        long denominator = held + notHeld + cancelled;
        double heldRate = denominator > 0 ? held * 100.0 / denominator : 0.0;

        return CallStatusSummary.builder()
                .planned(planned)
                .held(held)
                .notHeld(notHeld)
                .cancelled(cancelled)
                .heldRate(heldRate)
                .build();
    }

    // ======================== AN-15 advanced operational analytics ========================

    /**
     * AN-15 A: count leads whose convertedAt falls in the selected [from, to)
     * window, inside the caller's resolved analytics scope. Conversion-EVENT
     * semantics, deliberately distinct from the created-window
     * {@link #getConversionByOwner}/{@code LeadMetrics.convertedLeads}.
     */
    public ConversionPeriodSummary getConversionDuringPeriod(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Lead> root = q.from(Lead.class);

        List<Predicate> predicates = basePredicates(root, cb, ctx);
        predicates.add(cb.isNotNull(root.get("convertedAt")));
        predicates.add(cb.greaterThanOrEqualTo(root.get("convertedAt"), range.from()));
        predicates.add(cb.lessThan(root.get("convertedAt"), range.to()));

        q.select(cb.count(root)).where(predicates.toArray(new Predicate[0]));
        return ConversionPeriodSummary.builder()
                .convertedDuringPeriod(nullToZero(em.createQuery(q).getSingleResult()))
                .build();
    }

    /**
     * AN-15 B: current-data breakout of the persisted {@code forecast_category}
     * enum for deals created in the selected period, grouped after the
     * authorized record set is defined. Deals with a null/blank category are a
     * first-class "Unspecified" bucket so the summed dealCount reconciles with
     * {@code summary.deals}. No probability or forecasting math is applied.
     */
    public List<ForecastCategoryRow> getPipelineByForecastCategory(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Deal> deal = q.from(Deal.class);
        Join<Deal, DealStage> stage = deal.join("stage");

        List<Predicate> predicates = basePredicates(deal, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(deal.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(deal.get("createdAt"), range.to()));

        Expression<BigDecimal> amount = deal.get("amount");
        CriteriaBuilder.Case<BigDecimal> pipelineCase = cb.<BigDecimal>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.OPEN), amount);
        CriteriaBuilder.Case<BigDecimal> wonAmountCase = cb.<BigDecimal>selectCase()
                .when(cb.equal(stage.get("recordCategory"), RecordCategory.CLOSED_WON), amount);

        Expression<Object> categoryKey = cb.<Object>coalesce(
                cb.function("upper", String.class, cb.<String>coalesce(deal.get("forecastCategory"), "")),
                "");

        q.multiselect(
                categoryKey,
                cb.count(deal),
                cb.coalesce(cb.sum(pipelineCase.otherwise(BigDecimal.ZERO)), BigDecimal.ZERO),
                cb.coalesce(cb.sum(wonAmountCase.otherwise(BigDecimal.ZERO)), BigDecimal.ZERO));
        q.where(predicates.toArray(new Predicate[0]));
        q.groupBy(categoryKey);
        q.orderBy(cb.desc(cb.count(deal)));

        List<Object[]> rows = em.createQuery(q).getResultList();
        List<ForecastCategoryRow> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String raw = (String) row[0];
            String label = raw == null || raw.isBlank() ? "UNSPECIFIED" : raw;
            result.add(ForecastCategoryRow.builder()
                    .category(label)
                    .dealCount(nullToZero(asLong(row[1])))
                    .pipelineValue(orZero((BigDecimal) row[2]))
                    .wonValue(orZero((BigDecimal) row[3]))
                    .build());
        }
        return result;
    }

    /**
     * AN-15 C: average age (created_at basis) and average time-in-current-stage
     * (stage_entered_at basis) for OPEN deals created in the selected period and
     * inside the authorized record set. Uses deterministic DB aggregation over
     * the same open-deal population as {@link #getDealAging}. Deals without
     * stage_entered_at are counted separately and excluded from the current-stage
     * average - never fabricated.
     */
    public CurrentStageAgeSummary getCurrentStageAge(AnalyticsContext ctx, AnalyticsDateRange range) {
        String scopeFilter = buildScopeFilter(ctx);
        String sql = """
                SELECT
                    COALESCE(AVG(EXTRACT(EPOCH FROM (now() - d.created_at)) / 86400.0), 0),
                    COALESCE(AVG(EXTRACT(EPOCH FROM (now() - d.stage_entered_at)) / 86400.0), 0),
                    COUNT(*) FILTER (WHERE d.stage_entered_at IS NOT NULL),
                    COUNT(*) FILTER (WHERE d.stage_entered_at IS NULL)
                FROM deals d
                WHERE d.deleted = false AND %s
                  AND d.created_at >= :from AND d.created_at < :to
                  AND d.stage_id IN (SELECT s.id FROM deal_stages s WHERE s.record_category = 'OPEN')
                """.formatted(scopeFilter);

        jakarta.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("from", range.from());
        query.setParameter("to", range.to());

        Object[] row = (Object[]) query.getSingleResult();
        return CurrentStageAgeSummary.builder()
                .avgDealAgeDays(asDouble(row[0]))
                .avgCurrentStageAgeDays(asDouble(row[1]))
                .openDealsWithStageEnteredAt(nullToZero(asLong(row[2])))
                .openDealsWithoutStageEnteredAt(nullToZero(asLong(row[3])))
                .build();
    }

    /**
     * AN-15 D: task completion/overdue rates plus meeting status distribution
     * for calls/meetings activity in the selected period, inside the caller's
     * resolved analytics scope. Denominator conventions are explicit and
     * documented on {@link ActivityRatesSummary}. All rates bounded to [0,100].
     */
    public ActivityRatesSummary getActivityRates(AnalyticsContext ctx, AnalyticsDateRange range) {
        // Task completion rate: completed(created+completed in range) / tasks(created in range)
        long tasksCreated = count(Task.class, ctx, range.from(), range.to());

        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> cqCompleted = cb.createQuery(Long.class);
        Root<Task> cRoot = cqCompleted.from(Task.class);
        List<Predicate> cPred = basePredicates(cRoot, cb, ctx);
        cPred.add(cb.greaterThanOrEqualTo(cRoot.get("createdAt"), range.from()));
        cPred.add(cb.lessThan(cRoot.get("createdAt"), range.to()));
        cPred.add(cb.equal(cRoot.get("status"), TaskStatus.COMPLETED));
        cPred.add(cb.greaterThanOrEqualTo(cRoot.get("completedAt"), range.from()));
        cPred.add(cb.lessThan(cRoot.get("completedAt"), range.to()));
        cqCompleted.select(cb.count(cRoot)).where(cPred.toArray(new Predicate[0]));
        long completed = nullToZero(em.createQuery(cqCompleted).getSingleResult());

        // Overdue rate: overdue(created in range, not closed, dueDate<now) / open(created in range, not closed)
        CriteriaQuery<Long> cqOverdue = cb.createQuery(Long.class);
        Root<Task> oRoot = cqOverdue.from(Task.class);
        List<Predicate> oPred = basePredicates(oRoot, cb, ctx);
        oPred.add(cb.greaterThanOrEqualTo(oRoot.get("createdAt"), range.from()));
        oPred.add(cb.lessThan(oRoot.get("createdAt"), range.to()));
        oPred.add(notClosed(oRoot, cb));
        oPred.add(cb.isNotNull(oRoot.get("dueDate")));
        oPred.add(cb.lessThan(oRoot.get("dueDate"), Instant.now()));
        cqOverdue.select(cb.count(oRoot)).where(oPred.toArray(new Predicate[0]));
        long overdue = nullToZero(em.createQuery(cqOverdue).getSingleResult());

        CriteriaQuery<Long> cqOpen = cb.createQuery(Long.class);
        Root<Task> opRoot = cqOpen.from(Task.class);
        List<Predicate> opPred = basePredicates(opRoot, cb, ctx);
        opPred.add(cb.greaterThanOrEqualTo(opRoot.get("createdAt"), range.from()));
        opPred.add(cb.lessThan(opRoot.get("createdAt"), range.to()));
        opPred.add(notClosed(opRoot, cb));
        cqOpen.select(cb.count(opRoot)).where(opPred.toArray(new Predicate[0]));
        long openTasks = nullToZero(em.createQuery(cqOpen).getSingleResult());

        double completionRate = tasksCreated > 0 ? completed * 100.0 / tasksCreated : 0.0;
        double overdueRate = openTasks > 0 ? overdue * 100.0 / openTasks : 0.0;

        ActivityRatesSummary.MeetingStatusSummary ms = meetingStatus(ctx, range);
        return ActivityRatesSummary.builder()
                .taskCompletionRate(completionRate)
                .taskOverdueRate(overdueRate)
                .meetingStatus(ms)
                .build();
    }

    /**
     * AN-15 D: call-duration metrics using ONLY authoritative durationMinutes.
     * Calls without captured duration are reported, never assumed zero.
     */
    public CallDurationSummary getCallDuration(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Call> call = q.from(Call.class);

        List<Predicate> predicates = basePredicates(call, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(call.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(call.get("createdAt"), range.to()));

        CriteriaBuilder.Case<Long> withDurCase = cb.<Long>selectCase()
                .when(cb.isNotNull(call.get("durationMinutes")), 1L);
        q.multiselect(
                cb.count(call),
                cb.sum(withDurCase.otherwise(0L)),
                cb.coalesce(cb.sum(call.<Integer>get("durationMinutes")), 0L));
        q.where(predicates.toArray(new Predicate[0]));

        Object[] row = em.createQuery(q).getSingleResult();
        long total = nullToZero(asLong(row[0]));
        long withDuration = nullToZero(asLong(row[1]));
        long totalMinutes = nullToZero(asLong(row[2]));
        double avg = withDuration > 0 ? totalMinutes * 1.0 / withDuration : 0.0;

        return CallDurationSummary.builder()
                .callsTotal(total)
                .callsWithDuration(withDuration)
                .callsWithoutDuration(Math.max(0, total - withDuration))
                .totalCallMinutes(totalMinutes)
                .averageCallDurationMinutes(avg)
                .build();
    }

    /**
     * AN-16 A1: lead source performance for leads created in the selected
     * period, grouped by the authoritative LeadSource (AN-12/AN-14 capture).
     * Created-window cohort semantics - convertedCount/conversionRate refer to
     * leads created in the period (is_converted), matching summary.newLeads
     * and leadMetrics. Leads with no (or unnamed) source fall into the stable
     * UNSPECIFIED bucket so the summed leadCount reconciles with
     * summary.leads. Grouping is derived only from the already-authorized lead
     * set (buildScopeFilter); no source filter is accepted.
     */
    public List<LeadSourcePerformanceRow> getLeadSourcePerformance(AnalyticsContext ctx, AnalyticsDateRange range) {
        String scopeFilter = buildQualifiedScopeFilter(ctx, "l");
        String sql = """
                SELECT COALESCE(NULLIF(ls.name, ''), 'UNSPECIFIED') AS source,
                       l.source_id,
                       COUNT(*) AS lead_count,
                       COUNT(*) FILTER (WHERE l.is_converted = true AND l.converted_at IS NOT NULL) AS converted_count
                FROM leads l
                LEFT JOIN lead_sources ls ON ls.id = l.source_id
                WHERE l.deleted = false AND %s
                  AND l.created_at >= :from AND l.created_at < :to
                GROUP BY COALESCE(NULLIF(ls.name, ''), 'UNSPECIFIED'), l.source_id
                ORDER BY lead_count DESC, source
                """.formatted(scopeFilter);

        jakarta.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("from", range.from());
        query.setParameter("to", range.to());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<LeadSourcePerformanceRow> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String source = (String) row[0];
            UUID sourceId = (UUID) row[1];
            long leadCount = nullToZero(asLong(row[2]));
            long convertedCount = nullToZero(asLong(row[3]));
            double rate = leadCount > 0 ? convertedCount * 100.0 / leadCount : 0.0;
            result.add(LeadSourcePerformanceRow.builder()
                    .sourceId(sourceId)
                    .source(source)
                    .leadCount(leadCount)
                    .convertedCount(convertedCount)
                    .conversionRate(rate)
                    .build());
        }
        return result;
    }

    /**
     * AN-16 B3: contacts created in the selected period grouped by their own
     * authorized account_id. Grouping is derived only from the already-scoped
     * contact set (buildScopeFilter) - an account id can never widen scope.
     * Contacts with no account are a first-class NO ACCOUNT bucket so the
     * summed contactCount reconciles with summary.contacts.
     */
    public List<ContactsPerAccountRow> getContactsPerAccount(AnalyticsContext ctx, AnalyticsDateRange range) {
        String scopeFilter = buildQualifiedScopeFilter(ctx, "c");
        String sql = """
                SELECT c.account_id,
                       COALESCE(NULLIF(a.name, ''), 'NO ACCOUNT') AS account_name,
                       COUNT(*) AS contact_count
                FROM contacts c
                LEFT JOIN accounts a ON a.id = c.account_id
                WHERE c.deleted = false AND %s
                  AND c.created_at >= :from AND c.created_at < :to
                GROUP BY c.account_id, COALESCE(NULLIF(a.name, ''), 'NO ACCOUNT')
                ORDER BY contact_count DESC, account_name
                """.formatted(scopeFilter);

        jakarta.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("from", range.from());
        query.setParameter("to", range.to());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<ContactsPerAccountRow> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            UUID accountId = (UUID) row[0];
            String accountName = (String) row[1];
            long contactCount = nullToZero(asLong(row[2]));
            result.add(ContactsPerAccountRow.builder()
                    .accountId(accountId)
                    .accountName(accountName)
                    .contactCount(contactCount)
                    .build());
        }
        return result;
    }

    /**
     * AN-17 B2: accounts created in the selected period grouped by their own
     * owner_user_id. Scope is applied to the accounts table itself via the
     * authoritve account-visibility model (identical to
     * AccountSpecifications.visibleToUser / RecordScopeGuard), so the group
     * dimension is derived only from accounts inside the caller's authorized
     * account set - an owner id can never widen scope. Accounts with no owner
     * fall into the UNASSIGNED bucket so SUM(accountCount) reconciles with the
     * authorized account population for the window.
     */
    public List<AccountsByOwnerRow> getAccountsByOwner(AnalyticsContext ctx, AnalyticsDateRange range) {
        String scopeFilter = buildQualifiedScopeFilter(ctx, "a");
        String sql = """
                SELECT a.owner_user_id,
                       COUNT(*) AS account_count,
                       COUNT(*) FILTER (WHERE a.is_active = true) AS active_count
                FROM accounts a
                WHERE a.deleted = false AND %s
                  AND a.created_at >= :from AND a.created_at < :to
                GROUP BY a.owner_user_id
                ORDER BY account_count DESC, a.owner_user_id
                """.formatted(scopeFilter);

        jakarta.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("from", range.from());
        query.setParameter("to", range.to());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<AccountsByOwnerRow> result = new ArrayList<>(rows.size());
        List<UUID> ownerIds = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            UUID ownerId = (UUID) row[0];
            if (ownerId != null) {
                ownerIds.add(ownerId);
            }
            result.add(AccountsByOwnerRow.builder()
                    .ownerUserId(ownerId)
                    .accountCount(nullToZero(asLong(row[1])))
                    .activeCount(nullToZero(asLong(row[2])))
                    .build());
        }
        Map<UUID, String> names = ownerDisplayNames(ownerIds);
        for (AccountsByOwnerRow row : result) {
            if (row.getOwnerUserId() != null) {
                row.setOwnerDisplayName(names.get(row.getOwnerUserId()));
            }
        }
        return result;
    }

    private ActivityRatesSummary.MeetingStatusSummary meetingStatus(AnalyticsContext ctx, AnalyticsDateRange range) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Meeting> m = q.from(Meeting.class);

        List<Predicate> predicates = basePredicates(m, cb, ctx);
        predicates.add(cb.greaterThanOrEqualTo(m.get("createdAt"), range.from()));
        predicates.add(cb.lessThan(m.get("createdAt"), range.to()));

        CriteriaBuilder.Case<Long> plannedCase = cb.<Long>selectCase()
                .when(cb.equal(m.get("status"), Meeting.MeetingStatus.PLANNED), 1L);
        CriteriaBuilder.Case<Long> heldCase = cb.<Long>selectCase()
                .when(cb.equal(m.get("status"), Meeting.MeetingStatus.HELD), 1L);
        CriteriaBuilder.Case<Long> notHeldCase = cb.<Long>selectCase()
                .when(cb.equal(m.get("status"), Meeting.MeetingStatus.NOT_HELD), 1L);
        CriteriaBuilder.Case<Long> cancelledCase = cb.<Long>selectCase()
                .when(cb.equal(m.get("status"), Meeting.MeetingStatus.CANCELLED), 1L);

        q.multiselect(cb.sum(plannedCase.otherwise(0L)), cb.sum(heldCase.otherwise(0L)),
                cb.sum(notHeldCase.otherwise(0L)), cb.sum(cancelledCase.otherwise(0L)));
        q.where(predicates.toArray(new Predicate[0]));

        Object[] row = em.createQuery(q).getSingleResult();
        long planned = nullToZero(asLong(row[0]));
        long held = nullToZero(asLong(row[1]));
        long notHeld = nullToZero(asLong(row[2]));
        long cancelled = nullToZero(asLong(row[3]));
        long denominator = held + notHeld + cancelled;
        double heldRate = denominator > 0 ? held * 100.0 / denominator : 0.0;

        return ActivityRatesSummary.MeetingStatusSummary.builder()
                .planned(planned)
                .held(held)
                .notHeld(notHeld)
                .cancelled(cancelled)
                .heldRate(heldRate)
                .build();
    }

    private Predicate notClosed(Root<Task> root, CriteriaBuilder cb) {
        return cb.or(cb.equal(root.get("isClosed"), false), cb.isNull(root.get("isClosed")));
    }

    private static double asDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    // ======================== Identity decoration ========================

    /**
     * Resolves owner display names (User.getDisplayName) for ids already
     * derived from the authorized aggregate set. One batched lookup, never N+1.
     */
    private Map<UUID, String> ownerDisplayNames(Collection<UUID> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return Map.of();
        }
        List<User> users = userRepository.findAllById(ownerIds);
        return users.stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
    }

    /**
     * Resolves account names for account ids already derived from the
     * authorized aggregate set. One batched lookup, never N+1.
     */
    private Map<UUID, String> accountNames(Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        List<Account> accounts = accountRepository.findAllById(accountIds);
        return accounts.stream().collect(Collectors.toMap(Account::getId, Account::getName));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
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
            case TEAM -> {
                // Same visibility as the CRM record scopes (RecordScopeGuard /
                // *Specifications.visibleToUser): records owned or created by the
                // caller, plus records owned by the caller's direct reports
                // (existing users.manager_id manager hierarchy).
                predicates.add(cb.equal(root.<UUID>get("tenantId"), ctx.tenantId()));
                List<UUID> team = permissionEvaluatorService.getTeamUserIds(ctx.userId(), ctx.tenantId());
                Predicate teamOwned = team.isEmpty()
                        ? cb.disjunction()
                        : root.<UUID>get("ownerId").in(team);
                predicates.add(cb.or(
                        cb.equal(root.<UUID>get("ownerId"), ctx.userId()),
                        cb.equal(root.<UUID>get("createdBy"), ctx.userId()),
                        teamOwned));
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
            case USER -> "tenant_id = '%s' AND (owner_user_id = '%s' OR created_by = '%s')"
                    .formatted(ctx.tenantId(), ctx.userId(), ctx.userId());
            case TEAM -> {
                List<UUID> team = permissionEvaluatorService.getTeamUserIds(ctx.userId(), ctx.tenantId());
                String teamIds = team.isEmpty()
                        ? "'00000000-0000-0000-0000-000000000000'"
                        : team.stream()
                                .map(id -> "'%s'".formatted(id))
                                .collect(Collectors.joining(","));
                yield "tenant_id = '%s' AND (owner_user_id = '%s' OR created_by = '%s' OR owner_user_id IN (%s))"
                        .formatted(ctx.tenantId(), ctx.userId(), ctx.userId(), teamIds);
            }
        };
    }

    /**
     * Alias-qualified variant of {@link #buildScopeFilter} for native queries
     * that join extra tables (accounts, lead_sources) carrying their own
     * tenant_id/owner_user_id/created_by columns, avoiding the
     * "column reference ... is ambiguous" error. The primary (scoped) table
     * alias is prepended to every scope column reference.
     */
    private String buildQualifiedScopeFilter(AnalyticsContext ctx, String alias) {
        String base = buildScopeFilter(ctx);
        return base
                .replace("tenant_id IN (", alias + ".tenant_id IN (")
                .replace("tenant_id = ", alias + ".tenant_id = ")
                .replace("owner_user_id = ", alias + ".owner_user_id = ")
                .replace("owner_user_id IN (", alias + ".owner_user_id IN (")
                .replace("created_by = ", alias + ".created_by = ");
    }
}