package com.shivang.crm.modules.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.feedback.dto.FeedbackAnalyticsResponse;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.repository.FeedbackAnalyticsRepository;
import com.shivang.crm.modules.feedback.repository.FeedbackAnalyticsRepository.ScoreBucket;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.exception.PermissionDeniedException;

/**
 * L3.3-P4: metric assembly over pre-aggregated score buckets.
 *
 * <p>The repository returns {@code (type, score) -> count} rows; this test pins the
 * math that turns them into NPS, CSAT and CES. Database filtering (tenant, scope,
 * range, account) belongs to {@code FeedbackAnalyticsRepositoryTest} — here the
 * buckets are canned and the assertions are about classification, averages and
 * empty-set behaviour.
 */
class FeedbackAnalyticsServiceTest {

    private FeedbackAnalyticsRepository analyticsRepository;
    private RecordScopeGuard recordScopeGuard;
    private FeedbackAnalyticsService service;

    private UUID tenantId;
    private UUID callerId;

    @BeforeEach
    void setup() {
        analyticsRepository = mock(FeedbackAnalyticsRepository.class);
        recordScopeGuard = mock(RecordScopeGuard.class);
        service = new FeedbackAnalyticsService(analyticsRepository, recordScopeGuard);

        tenantId = UUID.randomUUID();
        callerId = UUID.randomUUID();

        when(recordScopeGuard.requireScope(eq(tenantId), any(UUID.class), eq("feedback"), eq("read")))
                .thenReturn("ALL");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(callerId.toString(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void buckets(ScoreBucket... buckets) {
        when(analyticsRepository.countByTypeAndScore(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(buckets));
    }

    private static ScoreBucket bucket(FeedbackType type, int score, long count) {
        return new ScoreBucket(type, score, count);
    }

    private FeedbackAnalyticsResponse analytics() {
        return service.analytics(tenantId, null, null, null);
    }

    // ================= NPS =================

    @Test
    void npsClassifiesDetractorsPassivesAndPromoters() {
        buckets(
                bucket(FeedbackType.NPS, 0, 1),
                bucket(FeedbackType.NPS, 6, 1),
                bucket(FeedbackType.NPS, 7, 1),
                bucket(FeedbackType.NPS, 8, 1),
                bucket(FeedbackType.NPS, 9, 1),
                bucket(FeedbackType.NPS, 10, 1));

        var nps = analytics().getNps();

        assertThat(nps.getResponseCount()).isEqualTo(6);
        assertThat(nps.getDetractorCount()).isEqualTo(2);
        assertThat(nps.getPassiveCount()).isEqualTo(2);
        assertThat(nps.getPromoterCount()).isEqualTo(2);
        // (2 - 2) / 6 * 100
        assertThat(nps.getScore()).isCloseTo(0.0, within(0.0001));
    }

    @Test
    void npsBoundaryScoresFallInTheRightSegment() {
        buckets(
                bucket(FeedbackType.NPS, 6, 3),
                bucket(FeedbackType.NPS, 7, 5),
                bucket(FeedbackType.NPS, 8, 7),
                bucket(FeedbackType.NPS, 9, 11));

        var nps = analytics().getNps();

        assertThat(nps.getDetractorCount()).isEqualTo(3);
        assertThat(nps.getPassiveCount()).isEqualTo(12);
        assertThat(nps.getPromoterCount()).isEqualTo(11);
        assertThat(nps.getResponseCount()).isEqualTo(26);
        // (11 - 3) / 26 * 100
        assertThat(nps.getScore()).isCloseTo(800.0 / 26, within(0.0001));
    }

    @Test
    void npsAllDetractorsIsMinusOneHundredNotNull() {
        buckets(bucket(FeedbackType.NPS, 0, 4));

        var nps = analytics().getNps();

        assertThat(nps.getScore()).isCloseTo(-100.0, within(0.0001));
    }

    @Test
    void npsAllPromotersIsOneHundred() {
        buckets(bucket(FeedbackType.NPS, 10, 3));

        assertThat(analytics().getNps().getScore()).isCloseTo(100.0, within(0.0001));
    }

    @Test
    void npsAllPassivesIsZeroButNotNull() {
        // 0.0 with responses is a real score; null without is "no data". The two
        // must never be confused.
        buckets(bucket(FeedbackType.NPS, 7, 2), bucket(FeedbackType.NPS, 8, 2));

        var nps = analytics().getNps();

        assertThat(nps.getScore()).isNotNull();
        assertThat(nps.getScore()).isCloseTo(0.0, within(0.0001));
    }

    @Test
    void npsWithNoResponsesIsNullNotZero() {
        buckets();

        var nps = analytics().getNps();

        assertThat(nps.getResponseCount()).isZero();
        assertThat(nps.getScore()).isNull();
        assertThat(nps.getPromoterPercentage()).isEqualTo(0.0);
        assertThat(nps.getPassivePercentage()).isEqualTo(0.0);
        assertThat(nps.getDetractorPercentage()).isEqualTo(0.0);
    }

    @Test
    void npsPercentagesSumToOneHundred() {
        buckets(
                bucket(FeedbackType.NPS, 2, 1),
                bucket(FeedbackType.NPS, 7, 1),
                bucket(FeedbackType.NPS, 10, 2));

        var nps = analytics().getNps();

        assertThat(nps.getDetractorPercentage()).isCloseTo(25.0, within(0.0001));
        assertThat(nps.getPassivePercentage()).isCloseTo(25.0, within(0.0001));
        assertThat(nps.getPromoterPercentage()).isCloseTo(50.0, within(0.0001));
    }

    // ================= CSAT =================

    @Test
    void csatAverageAndBuckets() {
        buckets(
                bucket(FeedbackType.CSAT, 1, 1),
                bucket(FeedbackType.CSAT, 2, 1),
                bucket(FeedbackType.CSAT, 3, 1),
                bucket(FeedbackType.CSAT, 4, 1),
                bucket(FeedbackType.CSAT, 5, 2));

        var csat = analytics().getCsat();

        assertThat(csat.getResponseCount()).isEqualTo(6);
        // (1 + 2 + 3 + 4 + 5 + 5) / 6
        assertThat(csat.getAverageScore()).isCloseTo(20.0 / 6, within(0.0001));
        assertThat(csat.getScore1Count()).isEqualTo(1);
        assertThat(csat.getScore2Count()).isEqualTo(1);
        assertThat(csat.getScore3Count()).isEqualTo(1);
        assertThat(csat.getScore4Count()).isEqualTo(1);
        assertThat(csat.getScore5Count()).isEqualTo(2);
    }

    @Test
    void csatBoundariesOneAndFive() {
        buckets(bucket(FeedbackType.CSAT, 1, 2), bucket(FeedbackType.CSAT, 5, 2));

        var csat = analytics().getCsat();

        assertThat(csat.getResponseCount()).isEqualTo(4);
        assertThat(csat.getAverageScore()).isCloseTo(3.0, within(0.0001));
    }

    @Test
    void csatWithNoResponsesIsNullNotZero() {
        buckets(bucket(FeedbackType.NPS, 10, 1));

        var csat = analytics().getCsat();

        assertThat(csat.getResponseCount()).isZero();
        assertThat(csat.getAverageScore()).isNull();
    }

    // ================= CES =================

    @Test
    void cesAverageAndBuckets() {
        buckets(
                bucket(FeedbackType.CES, 1, 2),
                bucket(FeedbackType.CES, 4, 1),
                bucket(FeedbackType.CES, 7, 1));

        var ces = analytics().getCes();

        assertThat(ces.getResponseCount()).isEqualTo(4);
        // (1 + 1 + 4 + 7) / 4 — never reversed: higher means more effort.
        assertThat(ces.getAverageScore()).isCloseTo(13.0 / 4, within(0.0001));
        assertThat(ces.getScore1Count()).isEqualTo(2);
        assertThat(ces.getScore4Count()).isEqualTo(1);
        assertThat(ces.getScore7Count()).isEqualTo(1);
        assertThat(ces.getScore2Count()).isZero();
        assertThat(ces.getScore3Count()).isZero();
        assertThat(ces.getScore5Count()).isZero();
        assertThat(ces.getScore6Count()).isZero();
    }

    @Test
    void cesBoundariesOneAndSeven() {
        buckets(bucket(FeedbackType.CES, 1, 1), bucket(FeedbackType.CES, 7, 1));

        var ces = analytics().getCes();

        assertThat(ces.getResponseCount()).isEqualTo(2);
        assertThat(ces.getAverageScore()).isCloseTo(4.0, within(0.0001));
    }

    @Test
    void cesWithNoResponsesIsNullNotZero() {
        buckets();

        var ces = analytics().getCes();

        assertThat(ces.getResponseCount()).isZero();
        assertThat(ces.getAverageScore()).isNull();
    }

    // ================= Cross-type isolation =================

    @Test
    void typesNeverLeakIntoEachOther() {
        buckets(
                bucket(FeedbackType.NPS, 10, 1),
                bucket(FeedbackType.CSAT, 1, 1),
                bucket(FeedbackType.CES, 7, 1),
                bucket(FeedbackType.GENERAL_FEEDBACK, 0, 99),
                bucket(FeedbackType.COMPLAINT, 0, 99));

        var response = analytics();

        assertThat(response.getNps().getResponseCount()).isEqualTo(1);
        assertThat(response.getCsat().getResponseCount()).isEqualTo(1);
        assertThat(response.getCes().getResponseCount()).isEqualTo(1);
    }

    @Test
    void unscoredTypesCannotMoveAnAverageEvenIfPresent() {
        // Defensive: the repository filters to scored ranges, but even a leaked
        // GENERAL_FEEDBACK bucket must not shift any metric.
        buckets(bucket(FeedbackType.GENERAL_FEEDBACK, 5, 10));

        var response = analytics();

        assertThat(response.getNps().getResponseCount()).isZero();
        assertThat(response.getCsat().getResponseCount()).isZero();
        assertThat(response.getCes().getResponseCount()).isZero();
        assertThat(response.getNps().getScore()).isNull();
        assertThat(response.getCsat().getAverageScore()).isNull();
        assertThat(response.getCes().getAverageScore()).isNull();
    }

    // ================= Scope =================

    @Test
    void theFeedbackReadScopeGatesTheAggregates() {
        service.analytics(tenantId, null, null, null);

        verify(recordScopeGuard).requireScope(eq(tenantId), any(UUID.class), eq("feedback"), eq("read"));
    }

    @Test
    void teamMembershipIsResolvedOnlyForTeamScope() {
        when(recordScopeGuard.requireScope(eq(tenantId), any(UUID.class), eq("feedback"), eq("read")))
                .thenReturn("TEAM");
        when(recordScopeGuard.teamMemberIds(eq(tenantId), any(UUID.class)))
                .thenReturn(List.of(UUID.randomUUID()));

        service.analytics(tenantId, null, null, null);

        verify(recordScopeGuard).teamMemberIds(eq(tenantId), any(UUID.class));
    }

    @Test
    void teamMembershipIsNotLookedUpOtherwise() {
        service.analytics(tenantId, null, null, null);

        verify(recordScopeGuard, org.mockito.Mockito.never())
                .teamMemberIds(any(), any());
    }

    @Test
    void aMissingFeedbackScopeFailsBeforeAnyQuery() {
        when(recordScopeGuard.requireScope(eq(tenantId), any(UUID.class), eq("feedback"), eq("read")))
                .thenThrow(new PermissionDeniedException("SCOPE_DENIED", "no feedback scope"));

        assertThat(catchThrowable(() -> service.analytics(tenantId, null, null, null)))
                .isInstanceOf(PermissionDeniedException.class);

        verify(analyticsRepository, org.mockito.Mockito.never())
                .countByTypeAndScore(any(), any(), any(), any(), any(), any(), any());
    }

    // ================= Filters reach the query =================

    @Test
    void dateBoundsAndAccountReachTheRepository() {
        UUID accountId = UUID.randomUUID();

        service.analytics(tenantId, "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", accountId);

        ArgumentCaptor<java.time.Instant> from = ArgumentCaptor.forClass(java.time.Instant.class);
        ArgumentCaptor<java.time.Instant> to = ArgumentCaptor.forClass(java.time.Instant.class);
        verify(analyticsRepository).countByTypeAndScore(
                eq(tenantId), any(), any(), any(), from.capture(), to.capture(), eq(accountId));
        assertThat(from.getValue()).isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(to.getValue()).isEqualTo(java.time.Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    void absentBoundsDefaultToTheAnalyticsRange() {
        service.analytics(tenantId, null, null, null);

        ArgumentCaptor<java.time.Instant> from = ArgumentCaptor.forClass(java.time.Instant.class);
        ArgumentCaptor<java.time.Instant> to = ArgumentCaptor.forClass(java.time.Instant.class);
        verify(analyticsRepository).countByTypeAndScore(
                any(), any(), any(), any(), from.capture(), to.capture(), any());
        assertThat(from.getValue()).isNotNull();
        assertThat(to.getValue()).isNotNull();
        assertThat(from.getValue().isBefore(to.getValue())).isTrue();
    }

    @Test
    void anInvertedRangeIsRejected() {
        assertThat(catchThrowable(() -> service.analytics(
                        tenantId, "2026-02-01T00:00:00Z", "2026-01-01T00:00:00Z", null)))
                .isInstanceOf(com.shivang.crm.shared.exception.BusinessException.class);
    }
}
