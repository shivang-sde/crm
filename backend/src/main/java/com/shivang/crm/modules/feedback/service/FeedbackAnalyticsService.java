package com.shivang.crm.modules.feedback.service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.analytics.AnalyticsDateRange;
import com.shivang.crm.modules.feedback.dto.FeedbackAnalyticsResponse;
import com.shivang.crm.modules.feedback.dto.FeedbackAnalyticsResponse.CsatMetrics;
import com.shivang.crm.modules.feedback.dto.FeedbackAnalyticsResponse.CesMetrics;
import com.shivang.crm.modules.feedback.dto.FeedbackAnalyticsResponse.NpsMetrics;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.repository.FeedbackAnalyticsRepository;
import com.shivang.crm.modules.feedback.repository.FeedbackAnalyticsRepository.ScoreBucket;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.3-P4: live Voice-of-Customer metrics.
 *
 * <p>Kept separate from {@link FeedbackService} so the submission/read boundary
 * stays readable: that class owns identity, validation and persistence; this class
 * owns the one concern of turning visible rows into metrics.
 *
 * <p>Scope resolution deliberately mirrors {@code FeedbackService.list} rather than
 * imposing its own rule: {@code requireScope} on {@code feedback:read}, team
 * membership only when the scope is TEAM. An analytics read over rows the caller
 * could not list individually would be an authorization bypass, so the same scope
 * that gates the list gates the aggregates — inside the database query, not after
 * it.
 *
 * <p>Date handling reuses {@code AnalyticsDateRange}: ISO-8601 instants,
 * {@code [from, to)} so consecutive ranges never double-count a boundary row, a
 * 30-day default when neither bound is given, and {@code INVALID_DATE_RANGE} for
 * malformed or inverted input.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackAnalyticsService {

    private final FeedbackAnalyticsRepository analyticsRepository;
    private final RecordScopeGuard recordScopeGuard;

    /**
     * NPS, CSAT and CES over the caller's visible feedback in {@code [from, to)}.
     *
     * @param rawFrom ISO-8601 instant or null
     * @param rawTo ISO-8601 instant or null
     * @param accountId optional canonical-customer filter; never an email, never a
     *        contact — Account remains the aggregation identity
     */
    @Transactional(readOnly = true)
    public FeedbackAnalyticsResponse analytics(
            UUID tenantId, String rawFrom, String rawTo, UUID accountId) {

        UUID userId = currentUserId();
        String scope = recordScopeGuard.requireScope(tenantId, userId, "feedback", "read");
        List<UUID> teamUserIds = "TEAM".equals(scope)
                ? recordScopeGuard.teamMemberIds(tenantId, userId)
                : List.of();

        AnalyticsDateRange range = AnalyticsDateRange.resolve(rawFrom, rawTo);

        List<ScoreBucket> buckets = analyticsRepository.countByTypeAndScore(
                tenantId, userId, scope, teamUserIds,
                range.from(), range.to(), accountId);

        log.debug("VoC analytics for tenant {} account {}: {} score buckets in [{}, {})",
                tenantId, accountId, buckets.size(), range.from(), range.to());

        return FeedbackAnalyticsResponse.builder()
                .nps(nps(buckets))
                .csat(csat(buckets))
                .ces(ces(buckets))
                .build();
    }

    private NpsMetrics nps(List<ScoreBucket> buckets) {
        Map<Integer, Long> byScore = scoresOf(buckets, FeedbackType.NPS);
        long promoters = sumRange(byScore, 9, 10);
        long passives = sumRange(byScore, 7, 8);
        long detractors = sumRange(byScore, 0, 6);
        long total = promoters + passives + detractors;

        return NpsMetrics.builder()
                .responseCount(total)
                .promoterCount(promoters)
                .passiveCount(passives)
                .detractorCount(detractors)
                .promoterPercentage(percentage(promoters, total))
                .passivePercentage(percentage(passives, total))
                .detractorPercentage(percentage(detractors, total))
                // Null, not 0: an NPS of 0 means "every respondent a detractor",
                // which must not be manufactured from zero responses.
                .score(total == 0 ? null : ((promoters - detractors) * 100.0) / total)
                .build();
    }

    private CsatMetrics csat(List<ScoreBucket> buckets) {
        Map<Integer, Long> byScore = scoresOf(buckets, FeedbackType.CSAT);
        long total = sumRange(byScore, 1, 5);

        return CsatMetrics.builder()
                .responseCount(total)
                .averageScore(total == 0 ? null : weightedMean(byScore, 1, 5, total))
                .score1Count(byScore.getOrDefault(1, 0L))
                .score2Count(byScore.getOrDefault(2, 0L))
                .score3Count(byScore.getOrDefault(3, 0L))
                .score4Count(byScore.getOrDefault(4, 0L))
                .score5Count(byScore.getOrDefault(5, 0L))
                .build();
    }

    private CesMetrics ces(List<ScoreBucket> buckets) {
        Map<Integer, Long> byScore = scoresOf(buckets, FeedbackType.CES);
        long total = sumRange(byScore, 1, 7);

        // Never reversed or normalised: higher CES means more customer effort, per
        // the established product definition. An effort average that hid that
        // polarity would silently invert the metric.
        return CesMetrics.builder()
                .responseCount(total)
                .averageScore(total == 0 ? null : weightedMean(byScore, 1, 7, total))
                .score1Count(byScore.getOrDefault(1, 0L))
                .score2Count(byScore.getOrDefault(2, 0L))
                .score3Count(byScore.getOrDefault(3, 0L))
                .score4Count(byScore.getOrDefault(4, 0L))
                .score5Count(byScore.getOrDefault(5, 0L))
                .score6Count(byScore.getOrDefault(6, 0L))
                .score7Count(byScore.getOrDefault(7, 0L))
                .build();
    }

    /** Score-to-count map for one type; other types' rows never reach the callers. */
    private Map<Integer, Long> scoresOf(List<ScoreBucket> buckets, FeedbackType type) {
        Map<Integer, Long> byScore = new HashMap<>();
        for (ScoreBucket bucket : buckets) {
            if (bucket.type() == type && bucket.score() != null) {
                byScore.merge(bucket.score(), bucket.count(), Long::sum);
            }
        }
        return byScore;
    }

    private long sumRange(Map<Integer, Long> byScore, int from, int to) {
        long sum = 0;
        for (int score = from; score <= to; score++) {
            sum += byScore.getOrDefault(score, 0L);
        }
        return sum;
    }

    private double weightedMean(Map<Integer, Long> byScore, int from, int to, long total) {
        long weighted = 0;
        for (int score = from; score <= to; score++) {
            weighted += (long) score * byScore.getOrDefault(score, 0L);
        }
        return ((double) weighted) / total;
    }

    /** Shares are 0 when there is nothing to share; only scores stay null. */
    private double percentage(long part, long total) {
        return total == 0 ? 0.0 : (part * 100.0) / total;
    }

    private static UUID currentUserId() {
        return com.shivang.crm.util.UserUtil.currentUserId();
    }
}
