package com.shivang.crm.modules.account.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.activity.entity.Activity;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactSpecifications;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.repository.DealSpecifications;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementSpecifications;
import com.shivang.crm.modules.feedback.entity.Feedback;
import com.shivang.crm.modules.feedback.repository.FeedbackSpecifications;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.repository.TicketSpecifications;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * L3.2-P3: Account-scoped unified timeline query.
 *
 * <p>The {@code activities} table has no {@code account_id} — {@code entity_id} is a
 * polymorphic column with no foreign key, so an activity can only be placed against
 * an Account by resolving the entity it belongs to. This query does that resolution
 * <em>inside the database</em>, as a set of correlated subqueries, which is what makes
 * it safe: an activity appears only when the caller can already see the record that
 * owns it.
 *
 * <p>Why subqueries rather than "fetch the visible ids, then {@code entityId IN (...)}":
 * the two-step version has to load every visible child id first, which is unbounded
 * work on a large Account and makes the reported total wrong as soon as the cap is
 * hit. Here the visible-id set is computed by the database and pagination applies to
 * the activities themselves.
 *
 * <p>Why the existing {@code visibleToUser} specifications rather than scope logic
 * written here: {@code RecordScopeGuard} semantics must not be restated again.
 * Each subquery reuses the exact specification its own domain service already
 * composes for its list endpoint, so a scope change lands in one place and the
 * timeline follows. All are subquery-safe — each builds predicates from the
 * subquery root alone and never touches the {@code CriteriaQuery} argument.
 *
 * <p>This is a Customer-360 query, not generic activity infrastructure, so it lives
 * here rather than in {@code ActivityRepository}: it couples Activity to the
 * customer domains on purpose, and that coupling should not be paid by every other
 * activity reader.
 */
@Repository
public class Customer360TimelineQueryRepository {

    /**
     * Entity types that legitimately form an Account's customer timeline, and why.
     *
     * <p>These are exactly the entities already aggregated by Customer 360, which is
     * the inclusion rule: a type belongs in the timeline when it is an Account-linked
     * record the 360 also shows in its own section.
     *
     * <p>Excluded on purpose:
     * <ul>
     *   <li>{@code LEAD} — a Lead has no account; it is the pre-customer record that
     *       <em>creates</em> one. Lead activities describe the lead, not the customer.</li>
     *   <li>{@code CALL} — a Call relates to arbitrary entities through the call-link
     *       infrastructure, not through an account column, so an activity on a call
     *       cannot be attributed to an Account without a second resolution hop.</li>
     *   <li>{@code OFFERING} — a catalog product with no account relationship at all;
     *       including it would leak tenant-wide catalog history into one customer's
     *       timeline.</li>
     * </ul>
     */
    public static final String ACCOUNT = "ACCOUNT";
    public static final String CONTACT = "CONTACT";
    public static final String DEAL = "DEAL";
    public static final String TICKET = "TICKET";
    public static final String ENTITLEMENT = "ENTITLEMENT";
    /**
     * L3.3-P3: feedback activities.
     *
     * <p>One entity type for every {@link com.shivang.crm.modules.feedback.entity.FeedbackType}.
     * A complaint is {@code FEEDBACK}, not {@code COMPLAINT}: it has no lifecycle of its
     * own, and inventing a per-type entity vocabulary here would fork the one the
     * feedback section already uses.
     */
    public static final String FEEDBACK = "FEEDBACK";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * The caller's effective read scope per child domain, plus the team membership
     * those scopes consult.
     *
     * <p>Scopes are resolved once, outside the query, because each of the four
     * {@code visibleToUser} specifications takes them as parameters. Every value here
     * comes from {@code PermissionEvaluatorService} or {@code RecordScopeGuard}; none
     * is inferred here.
     */
    public record TimelineVisibility(
            UUID userId,
            String contactScope,
            String dealScope,
            String ticketScope,
            String entitlementScope,
            String feedbackScope,
            List<UUID> teamUserIds) {
    }

    /**
     * Newest-first page of activities belonging to {@code accountId} and visible to
     * the caller.
     *
     * <p>Pagination is applied to the activity rows by the database via
     * {@code OFFSET}/{@code LIMIT}, so an Account with a long history costs a page,
     * not its whole timeline. {@code pageable} is expected to be already normalized.
     */
    public Page<Activity> findTimelineForAccount(
            UUID tenantId,
            UUID accountId,
            TimelineVisibility visibility,
            Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Activity> dataQuery = cb.createQuery(Activity.class);
        Root<Activity> dataRoot = dataQuery.from(Activity.class);
        dataQuery.select(dataRoot);
        dataQuery.where(predicate(cb, dataQuery, dataRoot, tenantId, accountId, visibility));
        // Secondary sort on id: createdAt is caller-supplied in part (imports, system
        // actors), so equal timestamps must still yield a stable order across pages.
        dataQuery.orderBy(cb.desc(dataRoot.get("createdAt")), cb.desc(dataRoot.get("id")));

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Activity> countRoot = countQuery.from(Activity.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(predicate(cb, countQuery, countRoot, tenantId, accountId, visibility));

        TypedQuery<Activity> paged = entityManager.createQuery(dataQuery)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize());
        TypedQuery<Long> counter = entityManager.createQuery(countQuery);

        return new PageImpl<>(paged.getResultList(), pageable, counter.getSingleResult());
    }

    /**
     * Tenant + not-deleted + "the entity this activity points at belongs to this
     * Account and is visible to the caller".
     *
     * <p>Used by both the data and the count query so a reported total can never
     * disagree with the rows behind it.
     */
    private Predicate predicate(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<Activity> activity,
            UUID tenantId,
            UUID accountId,
            TimelineVisibility visibility) {

        UUID userId = visibility.userId();
        List<UUID> teamUserIds = visibility.teamUserIds();

        return cb.and(
                // Tenant isolation applies to every branch, including the ACCOUNT one.
                cb.equal(activity.get("tenantId"), tenantId),
                // Soft delete is conventional in this codebase, never an ORM filter.
                cb.isFalse(activity.get("deleted")),
                cb.or(
                        // The Account's own activities. Account visibility is already
                        // asserted by the caller before this query runs.
                        cb.and(
                                cb.equal(activity.get("entityType"), ACCOUNT),
                                cb.equal(activity.get("entityId"), accountId)),

                        childBranch(cb, query, activity, CONTACT, Contact.class,
                                ContactSpecifications.buildSpecification(
                                        tenantId, accountId, null, null,
                                        visibility.contactScope(), userId, teamUserIds)),

                        childBranch(cb, query, activity, DEAL, Deal.class,
                                DealSpecifications.buildSpecification(
                                        tenantId, null, accountId, null, null, null, null, null, null, null,
                                        visibility.dealScope(), userId, teamUserIds)),

                        childBranch(cb, query, activity, TICKET, Ticket.class,
                                TicketSpecifications.byTenantId(tenantId)
                                        .and(TicketSpecifications.notDeleted())
                                        .and(TicketSpecifications.visibleToUser(
                                                visibility.ticketScope(), userId, teamUserIds))
                                        .and(TicketSpecifications.byAccountId(accountId))),

                        childBranch(cb, query, activity, ENTITLEMENT, CustomerEntitlement.class,
                                CustomerEntitlementSpecifications.buildSpecification(
                                        tenantId, accountId, null, null, null, null, null, null, null, null)
                                        .and(CustomerEntitlementSpecifications.visibleToUser(
                                                visibility.entitlementScope(), userId, teamUserIds))),

                        // L3.3-P3: feedback activities. The subquery's predicate is
                        // Feedback's own specification, so a feedback activity is
                        // admitted exactly when that feedback would appear in the
                        // Customer 360 feedback section.
                        childBranch(cb, query, activity, FEEDBACK, Feedback.class,
                                FeedbackSpecifications.byTenantId(tenantId)
                                        .and(FeedbackSpecifications.notDeleted())
                                        .and(FeedbackSpecifications.visibleToUser(
                                                visibility.feedbackScope(), userId, teamUserIds))
                                        .and(FeedbackSpecifications.byAccountId(accountId)))));
    }

    /**
     * One "entity_id belongs to a visible child of this Account" branch.
     *
     * <p>The subquery's {@code where} is the domain's own specification, so the
     * activity is admitted exactly when that record would appear in the matching
     * Customer 360 section.
     */
    private <E> Predicate childBranch(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<Activity> activity,
            String entityType,
            Class<E> childType,
            Specification<E> spec) {

        Subquery<UUID> visibleChildIds = query.subquery(UUID.class);
        Root<E> child = visibleChildIds.from(childType);
        visibleChildIds.select(child.get("id"));
        visibleChildIds.where(spec.toPredicate(child, query, cb));

        return cb.and(
                cb.equal(activity.get("entityType"), entityType),
                activity.get("entityId").in(visibleChildIds));
    }
}
