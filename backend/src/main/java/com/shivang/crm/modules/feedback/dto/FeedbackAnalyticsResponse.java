package com.shivang.crm.modules.feedback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L3.3-P4: live Voice-of-Customer metrics over the caller's visible Feedback.
 *
 * <p>A live projection, never stored: every field is computed from Feedback rows at
 * read time. There is no snapshot, no cache and no materialised table, so two
 * consecutive reads cannot disagree with the underlying data.
 *
 * <p>Only {@code NPS}, {@code CSAT} and {@code CES} rows participate, each inside
 * its own validated range. {@code GENERAL_FEEDBACK} and {@code COMPLAINT} carry no
 * score semantics and are excluded entirely — there is no complaint rate, no
 * sentiment and no resolution score here.
 *
 * <p>Nullability is deliberate and follows the {@code CallDurationSummary} principle
 * that a null/absent value is never interpreted as zero. An NPS of {@code 0} is a
 * meaningful score (every respondent a detractor); manufacturing it from zero
 * responses would conflate "no data" with "worst possible". So {@code score} and
 * {@code averageScore} are boxed and stay {@code null} when their response count is
 * zero, while counts and percentages are plain numbers that are {@code 0}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedbackAnalyticsResponse {

    private NpsMetrics nps;
    private CsatMetrics csat;
    private CesMetrics ces;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NpsMetrics {
        /** NPS responses in range and scope. */
        private long responseCount;
        /** Scores 9-10. */
        private long promoterCount;
        /** Scores 7-8. */
        private long passiveCount;
        /** Scores 0-6. */
        private long detractorCount;
        /** promoterCount / responseCount * 100, or 0 when there are no responses. */
        private double promoterPercentage;
        /** passiveCount / responseCount * 100, or 0 when there are no responses. */
        private double passivePercentage;
        /** detractorCount / responseCount * 100, or 0 when there are no responses. */
        private double detractorPercentage;
        /**
         * ((promoters - detractors) / responses) * 100, or {@code null} when there
         * are no responses.
         */
        private Double score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CsatMetrics {
        /** CSAT responses in range and scope. */
        private long responseCount;
        /** Mean score, or {@code null} when there are no responses. */
        private Double averageScore;
        private long score1Count;
        private long score2Count;
        private long score3Count;
        private long score4Count;
        private long score5Count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CesMetrics {
        /** CES responses in range and scope. */
        private long responseCount;
        /** Mean score, or {@code null} when there are no responses. */
        private Double averageScore;
        private long score1Count;
        private long score2Count;
        private long score3Count;
        private long score4Count;
        private long score5Count;
        private long score6Count;
        private long score7Count;
    }
}
