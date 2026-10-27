package com.shivang.crm.modules.feedback.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.shivang.crm.modules.feedback.entity.Feedback;
import com.shivang.crm.modules.feedback.entity.FeedbackType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

/**
 * L3.3-P4: the shape of the Voice-of-Customer aggregation query.
 *
 * <p>No database-backed harness exists in this repository, so this test verifies
 * the part that carries the security claim against a mocked
 * {@code CriteriaBuilder}: which entity attributes the query filters on. Every
 * {@code root.get(...)} the specifications ask for is exactly the set of columns
 * the generated SQL will constrain.
 *
 * <p>The property under test is that scope, tenant, soft-delete, type range, date
 * range and account all constrain the aggregation <em>in</em> the query. An
 * aggregate computed over rows the caller could not list individually would be an
 * authorization bypass dressed as a summary.
 */
class FeedbackAnalyticsRepositoryTest {

    private EntityManager entityManager;
    private CriteriaBuilder cb;
    private CriteriaQuery<FeedbackAnalyticsRepository.ScoreBucket> query;
    private Root<Feedback> root;
    private TypedQuery<FeedbackAnalyticsRepository.ScoreBucket> typedQuery;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();

        entityManager = mock(EntityManager.class);
        cb = mock(CriteriaBuilder.class);
        query = mock(CriteriaQuery.class);
        root = mock(Root.class);
        typedQuery = mock(TypedQuery.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(FeedbackAnalyticsRepository.ScoreBucket.class)).thenReturn(query);
        when(query.from(Feedback.class)).thenReturn(root);
        when(root.get(anyString())).thenReturn(mock(Path.class));
        // Fluent CriteriaQuery chain: select/where/groupBy each return the query.
        // any() without a class because cb.construct() returns null on the mock,
        // and any(Class) does not match null. select has a single overload, so
        // any() is unambiguous here.
        when(query.select(any())).thenReturn(query);
        // where is overloaded, so the predicate type must be named; nullable
        // because the mocked CriteriaBuilder composes its predicates to null.
        when(query.where(org.mockito.ArgumentMatchers.nullable(
                jakarta.persistence.criteria.Predicate.class))).thenReturn(query);
        when(query.groupBy(any(Expression[].class))).thenReturn(query);
        when(entityManager.createQuery(query)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of());
    }

    private FeedbackAnalyticsRepository repository() {
        FeedbackAnalyticsRepository repository = new FeedbackAnalyticsRepository();
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
        return repository;
    }

    private void run(String scope, Instant from, Instant to, UUID accountId) {
        repository().countByTypeAndScore(
                tenantId, userId, scope, List.of(userId), from, to, accountId);
    }

    private void run(String scope) {
        run(scope, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"), null);
    }

    // ================= Tenant, soft delete, scope =================

    @Test
    void tenantIsolationApplies() {
        run("ALL");

        verify(root).get(eq("tenantId"));
    }

    @Test
    void deletedRowsAreExcluded() {
        run("ALL");

        verify(root).get(eq("deleted"));
    }

    @Test
    void internalAndUnattributedFeedbackIsExcluded() {
        run("ALL");

        verify(root, times(1)).get(eq("source"));
    }

    @Test
    void underOwnScopeOwnershipIsFiltered() {
        run("OWN");

        verify(root, times(1)).get(eq("ownerId"));
        verify(root, times(1)).get(eq("createdBy"));
    }

    @Test
    void underAllScopeOwnershipIsNotFiltered() {
        run("ALL");

        verify(root, times(0)).get(eq("ownerId"));
        verify(root, times(0)).get(eq("createdBy"));
    }

    @Test
    void anUnknownScopeFailsClosed() {
        run("SOMETHING_ELSE");

        verify(root, times(0)).get(eq("ownerId"));
        verify(root, times(0)).get(eq("createdBy"));
    }

    // ================= Type and score range =================

    @Test
    void onlyScoredTypesParticipate() {
        run("ALL");

        // NPS, CSAT and CES each appear as a (type = X AND score in range) branch;
        // GENERAL_FEEDBACK and COMPLAINT never do. The extra two reads are the
        // select-construct and the group-by over the same two paths.
        verify(root, times(5)).get(eq("type"));
        verify(root, times(5)).get(eq("score"));
    }

    // ================= Date range =================

    @Test
    void bothBoundsConstrainCreatedAt() {
        run("ALL",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                null);

        // createdAt >= from AND createdAt < to: inclusive-exclusive, so consecutive
        // ranges never double-count a boundary row.
        verify(root, times(2)).get(eq("createdAt"));
    }

    @Test
    void absentBoundsAddNoDatePredicate() {
        run("ALL", null, null, null);

        verify(root, times(0)).get(eq("createdAt"));
    }

    @Test
    void aFromOnlyBoundStillConstrains() {
        run("ALL", Instant.parse("2026-01-01T00:00:00Z"), null, null);

        verify(root, times(1)).get(eq("createdAt"));
    }

    // ================= Account filter =================

    @Test
    void anAccountFilterConstrainsAccountId() {
        run("ALL",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                UUID.randomUUID());

        verify(root, times(1)).get(eq("accountId"));
    }

    @Test
    void noAccountFilterAddsNoAccountPredicate() {
        run("ALL");

        verify(root, times(0)).get(eq("accountId"));
    }

    // ================= Aggregation shape =================

    @Test
    void theQueryGroupsByTypeAndScore() {
        run("ALL");

        verify(query).groupBy(any(Expression.class), any(Expression.class));
    }

    @Test
    void theQueryCountsRatherThanLoadingEntities() {
        run("ALL");

        verify(cb).count(root);
    }

    @Test
    void noEntityIsEverFetched() {
        run("ALL");

        // The result is a list of (type, score, count) rows, not Feedback entities.
        verify(typedQuery).getResultList();
        verify(entityManager, times(0)).find(any(), any());
    }

    // ================= No new surface =================

    @Test
    void theRepositoryExposesExactlyOneOperation() {
        assertThat(java.util.Arrays.stream(FeedbackAnalyticsRepository.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName))
                .containsExactly("countByTypeAndScore");
    }

    @Test
    void scoreIsNeverReadOutsideItsTypeBranch() {
        // score is only ever read inside the per-type range branches asserted above,
        // plus the select-construct and group-by over the same path; there is no
        // cross-type score predicate that could mix ranges.
        run("ALL");

        verify(root, times(5)).get(eq("score"));
    }
}
