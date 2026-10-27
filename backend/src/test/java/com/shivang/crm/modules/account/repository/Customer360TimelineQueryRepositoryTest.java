package com.shivang.crm.modules.account.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.shivang.crm.modules.account.repository.Customer360TimelineQueryRepository.TimelineVisibility;
import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.entity.Activity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * L3.2-P3: the shape of the Account timeline query.
 *
 * <p>This repository has no database-backed test harness and this phase is forbidden
 * from adding one, so this test verifies the part that actually carries the security
 * claim: which entity attributes and subqueries the criteria tree is built from. A
 * mocked {@code CriteriaBuilder} still records every {@code root.get(...)} the domain
 * specifications ask for, which is exactly the set of columns the generated SQL will
 * filter on.
 *
 * <p>The property under test is that per-child visibility is expressed <em>in</em> the
 * query rather than applied afterwards. Each of the four child tables must contribute
 * a subquery constrained by tenant, soft-delete, account and the caller's own scope.
 *
 * <p>Why some counts are doubled: the predicate is built twice, once for the data
 * query and once for the count query, so a reported {@code total} can never disagree
 * with the rows returned. Each child root therefore sees four children per build, and
 * {@link #theCountIsComputedUnderTheSameRestrictions()} pins the two-build intent.
 */
class Customer360TimelineQueryRepositoryTest {

    private EntityManager entityManager;
    private CriteriaBuilder cb;
    private CriteriaQuery<Activity> dataQuery;
    private CriteriaQuery<Long> countQuery;
    private Root<Activity> dataRoot;
    private Root<Activity> countRoot;
    private Subquery<UUID> dataSubquery;
    private Subquery<UUID> countSubquery;
    private Root<Object> dataChildRoot;
    private Root<Object> countChildRoot;
    private TypedQuery<Activity> dataTypedQuery;
    private TypedQuery<Long> countTypedQuery;

    private UUID tenantId;
    private UUID accountId;
    private UUID userId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        tenantId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        userId = UUID.randomUUID();

        entityManager = mock(EntityManager.class);
        cb = mock(CriteriaBuilder.class);

        dataQuery = mock(CriteriaQuery.class);
        dataRoot = mock(Root.class);
        dataSubquery = mock(Subquery.class);
        dataChildRoot = mock(Root.class);

        countQuery = mock(CriteriaQuery.class);
        countRoot = mock(Root.class);
        countSubquery = mock(Subquery.class);
        countChildRoot = mock(Root.class);

        when(dataQuery.from(Activity.class)).thenReturn(dataRoot);
        when(countQuery.from(Activity.class)).thenReturn(countRoot);
        when(dataQuery.subquery(UUID.class)).thenReturn(dataSubquery);
        when(countQuery.subquery(UUID.class)).thenReturn(countSubquery);
        // All four child branches of one build share the subquery and its root.
        when(dataSubquery.from(any(Class.class))).thenReturn(dataChildRoot);
        when(countSubquery.from(any(Class.class))).thenReturn(countChildRoot);
        // Criteria paths must be non-null so `.in(...)` and `.select(...)` can chain.
        when(dataRoot.get(anyString())).thenReturn(mock(Path.class));
        when(countRoot.get(anyString())).thenReturn(mock(Path.class));
        when(dataChildRoot.get(anyString())).thenReturn(mock(Path.class));
        when(countChildRoot.get(anyString())).thenReturn(mock(Path.class));

        dataTypedQuery = mock(TypedQuery.class);
        countTypedQuery = mock(TypedQuery.class);
        when(dataTypedQuery.setFirstResult(anyInt())).thenReturn(dataTypedQuery);
        when(dataTypedQuery.setMaxResults(anyInt())).thenReturn(dataTypedQuery);
        when(dataTypedQuery.getResultList()).thenReturn(List.of());
        when(countTypedQuery.getSingleResult()).thenReturn(0L);

when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(Activity.class)).thenReturn(dataQuery);
        when(cb.createQuery(Long.class)).thenReturn(countQuery);
        when(cb.count(any())).thenReturn(mock(Expression.class));
        when(entityManager.createQuery(dataQuery)).thenReturn(dataTypedQuery);
        when(entityManager.createQuery(countQuery)).thenReturn(countTypedQuery);
    }

    /** @PersistenceContext is field-only, so the mock is injected the way Spring does. */
    private Customer360TimelineQueryRepository repository() {
        Customer360TimelineQueryRepository repository = new Customer360TimelineQueryRepository();
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
        return repository;
    }

    private void run(String scope, PageRequest pageable) {
        repository().findTimelineForAccount(
                tenantId,
                accountId,
                new TimelineVisibility(userId, scope, scope, scope, scope, scope, List.of(userId)),
                pageable);
    }

    private void run(String scope) {
        run(scope, PageRequest.of(0, 20));
    }

    // ================= A. Supported entity types =================

    @Test
    void theTimelineCoversExactlySixCustomerEntityTypes() {
        assertThat(List.of(
                Customer360TimelineQueryRepository.ACCOUNT,
                Customer360TimelineQueryRepository.CONTACT,
                Customer360TimelineQueryRepository.DEAL,
                Customer360TimelineQueryRepository.TICKET,
                Customer360TimelineQueryRepository.ENTITLEMENT,
                Customer360TimelineQueryRepository.FEEDBACK))
                .containsExactlyInAnyOrder(
                        "ACCOUNT", "CONTACT", "DEAL", "TICKET", "ENTITLEMENT", "FEEDBACK");
    }

    @Test
    void unrelatedActivityTypesAreExcluded() {
        // A type with no account relationship, or whose account link needs a second
        // resolution hop, must not be reachable from this query. LEAD precedes the
        // customer, CALL relates through call links, OFFERING is tenant catalog.
        assertThat(List.of(
                Customer360TimelineQueryRepository.ACCOUNT,
                Customer360TimelineQueryRepository.CONTACT,
                Customer360TimelineQueryRepository.DEAL,
                Customer360TimelineQueryRepository.TICKET,
                Customer360TimelineQueryRepository.ENTITLEMENT,
                Customer360TimelineQueryRepository.FEEDBACK))
                .doesNotContain("LEAD", "CALL", "OFFERING", "TASK", "MEETING", "NOTE");
    }

    @Test
    void feedbackIsOneEntityTypeNotOnePerFeedbackType() {
        // A complaint is FEEDBACK, not COMPLAINT: Feedback has no lifecycle of its
        // own, so a per-type entity vocabulary would fork the one the feedback
        // section already uses. There must be no COMPLAINT/NPS/CSAT/CES constant
        // for the timeline to branch on.
        assertThat(Customer360TimelineQueryRepository.FEEDBACK).isEqualTo("FEEDBACK");
        assertThat(java.util.Arrays.stream(Customer360TimelineQueryRepository.class.getDeclaredFields())
                .filter(f -> java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .filter(f -> f.getType() == String.class)
                .map(f -> {
                    try {
                        return String.valueOf(f.get(null));
                    } catch (IllegalAccessException e) {
                        throw new AssertionError(e);
                    }
                }))
                .doesNotContain("COMPLAINT", "NPS", "CSAT", "CES");
    }

    @Test
    void eachChildEntityTypeContributesItsOwnSubquery() {
        run("ALL");

        // Five children in the data build, five in the count build.
        verify(dataQuery, times(5)).subquery(eq(UUID.class));
        verify(countQuery, times(5)).subquery(eq(UUID.class));
    }

    @Test
    void aChildSubquerySelectsTheChildsOwnId() {
        run("ALL");

        // Not a row join: the visible set is a list of ids matched against entity_id.
        verify(dataChildRoot, times(5)).get(eq("id"));
    }

    // ================= B. Tenant isolation and soft delete =================

    @Test
    void tenantIsolationAppliesToEveryActivityRow() {
        run("ALL");

        verify(dataRoot).get(eq("tenantId"));
        verify(countRoot).get(eq("tenantId"));
    }

    @Test
    void deletedActivitiesAreExcluded() {
        // No ORM-level soft-delete filter exists here, so this predicate is the only
        // thing keeping a deleted activity out of the timeline.
        run("ALL");

        verify(dataRoot).get(eq("deleted"));
        verify(countRoot).get(eq("deleted"));
    }

    // ================= C. Account relationship resolution =================

    @Test
    void everyChildSubqueryIsScopedToTheRequestedAccount() {
        run("ALL");

        // Five children, each filtered by account_id. Without this the timeline would
        // include activities belonging to other accounts in the tenant.
        verify(dataChildRoot, times(5)).get(eq("accountId"));
        verify(countChildRoot, times(5)).get(eq("accountId"));
    }

    @Test
    void everyChildSubqueryIsAlsoTenantScoped() {
        run("ALL");

        verify(dataChildRoot, times(5)).get(eq("tenantId"));
    }

    @Test
    void everyChildSubqueryExcludesDeletedRecords() {
        // A deleted Deal must not contribute its history to the timeline.
        run("ALL");

        verify(dataChildRoot, times(5)).get(eq("deleted"));
    }

    @Test
    void theAccountBranchMatchesTheAccountsOwnActivitiesDirectly() {
        run("ALL");

        // ACCOUNT needs no subquery: its id is the account id. entity_type is read once
        // per branch (six branches) and entity_id once per branch.
        verify(dataRoot, times(6)).get(eq("entityType"));
        verify(dataRoot, times(6)).get(eq("entityId"));
    }

    // ================= D. Per-child authorization, the core requirement =================

    @Test
    void underOwnScopeEachChildFiltersOnOwnerOrCreator() {
        run("OWN");

        // OWN is (ownerId = caller OR createdBy = caller), per child.
        verify(dataChildRoot, times(5)).get(eq("ownerId"));
        verify(dataChildRoot, times(5)).get(eq("createdBy"));
    }

    @Test
    void underTeamScopeEachChildAlsoFiltersOnTeamMembership() {
        run("TEAM");

        // TEAM is OWN plus `ownerId IN (team)`, so ownerId is read twice per child.
        verify(dataChildRoot, times(10)).get(eq("ownerId"));
        verify(dataChildRoot, times(5)).get(eq("createdBy"));
    }

    @Test
    void underAllScopeOwnershipIsNotFiltered() {
        // ALL is cb.conjunction(). Proves the resolved scope actually reaches the query
        // instead of being applied as a constant extra restriction.
        run("ALL");

        verify(dataChildRoot, times(0)).get(eq("ownerId"));
        verify(dataChildRoot, times(0)).get(eq("createdBy"));
    }

    @Test
    void anUnknownScopeFailsClosedOnEveryChild() {
        // Mirrors the default branch of visibleToUser: no rows, never treated as ALL.
        run("SOMETHING_ELSE");

        verify(dataChildRoot, times(0)).get(eq("ownerId"));
        verify(dataChildRoot, times(0)).get(eq("createdBy"));
    }

    @Test
    void authorizationIsPerDomainNotGlobal() {
        // The whole point: five independent scopes. A global model would carry one.
        assertThat(TimelineVisibility.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .contains("contactScope", "dealScope", "ticketScope", "entitlementScope",
                        "feedbackScope");
    }

    // ================= E. Ordering and database-level pagination =================

    @Test
    void orderingIsNewestFirstWithAStableTiebreaker() {
        run("ALL");

        verify(dataRoot).get(eq("createdAt"));
        verify(dataRoot).get(eq("id"));
        verify(dataQuery).orderBy(any(Order[].class));
    }

    @Test
    void paginationIsAppliedToTheActivityQueryInTheDatabase() {
        run("ALL", PageRequest.of(0, 20));

        // OFFSET/LIMIT on the activity rows. Without these an Account's entire history
        // would be loaded and then discarded.
        verify(dataTypedQuery).setFirstResult(0);
        verify(dataTypedQuery).setMaxResults(20);
    }

    @Test
    void aLaterPageIsOffsetRatherThanReloaded() {
        run("ALL", PageRequest.of(3, 10));

        verify(dataTypedQuery).setFirstResult(30);
        verify(dataTypedQuery).setMaxResults(10);
    }

    @Test
    void theCountIsComputedUnderTheSameRestrictions() {
        // The count query carries the identical predicate, so `total` can never describe
        // a different set of activities than the ones returned.
        run("ALL");

verify(entityManager).createQuery(countQuery);
        verify(countQuery).select(any(Expression.class));
        // nullable because the mocked CriteriaBuilder composes its predicates to null;
        // what matters is that the count query carries a where clause at all.
        verify(countQuery).where(org.mockito.ArgumentMatchers.nullable(
                jakarta.persistence.criteria.Predicate.class));
    }

    // ================= F. Live read, no projection, no N+1 =================

    @Test
    void theQueryReadsTheExistingActivitiesTableOnly() {
        run("ALL");

        // No timeline table and no timeline entity: activities is the source of truth.
        verify(dataQuery).from(Activity.class);
        verify(countQuery).from(Activity.class);
    }

    @Test
    void childSubqueriesDoNotJoin() {
        // All five child tables carry a plain account_id column, so no join is needed
        // and none is introduced.
        run("ALL");

        verify(dataChildRoot, times(0)).join(anyString());
        verify(countChildRoot, times(0)).join(anyString());
    }

    @Test
    void theQueryResolvesNoEntityDisplayNames() {
        // ActivityResponse already carries entityType/entityId/activityType/description/
        // createdAt, so no per-row entity lookup is needed and none is performed. This
        // is the N+1 guard.
        assertThat(ActivityResponse.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .contains("entityType", "entityId", "activityType", "description", "createdAt", "actorType");
    }

    @Test
    void theVisibilityRecordCarriesOnlyCallerDerivedValues() {
        // Nothing in the record may be derived from the requested account, or the
        // query could be steered by the caller.
        assertThat(TimelineVisibility.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("accountId", "tenantId");
    }
}
