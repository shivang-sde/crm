package com.shivang.crm.modules.feedback.entity;

/**
 * L3.3-P1: the kind of customer signal a Feedback record carries.
 *
 * <p>The score scale is declared here rather than in the service so that the type
 * and its range cannot drift apart: {@link #isScoreRequired()} and the
 * {@code MIN}/{@code MAX} bounds are read from the same enum constant that the
 * entity persists.
 *
 * <p>Derived classifications (NPS promoter/detractor, CES effort band) are
 * deliberately NOT modelled. They are computed at read time and stored nowhere, for
 * the same reason {@code tickets} carries no {@code breach_reason}: the event that
 * produced the value already records it, and a stored derived column would need a
 * backfill the day a scale changed.
 */
public enum FeedbackType {

    /** Net Promoter Score, 0-10. Higher is better. */
    NPS(0, 10, true),

    /** Customer Satisfaction, 1-5. Higher is better. */
    CSAT(1, 5, true),

    /**
     * Customer Effort Score, 1-7. Higher means MORE effort, so the polarity is
     * inverted relative to CSAT: aggregating CES as though it were a satisfaction
     * score would silently invert the metric.
     */
    CES(1, 7, true),

    /** Unscored free-form customer feedback. */
    GENERAL_FEEDBACK(null, null, false),

    /**
     * An unscored customer complaint. P1 records the complaint as submitted; the
     * support lifecycle (SLA, assignment, escalation, resolution) belongs to the
     * optional Ticket correlation and to L3.3-P2, not to this row.
     */
    COMPLAINT(null, null, false);

    private final Integer minScore;
    private final Integer maxScore;
    private final boolean scoreRequired;

    FeedbackType(Integer minScore, Integer maxScore, boolean scoreRequired) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.scoreRequired = scoreRequired;
    }

    public Integer getMinScore() {
        return minScore;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    /** True when this type cannot be stored without a score. */
    public boolean isScoreRequired() {
        return scoreRequired;
    }

    /** True when this type has a defined numeric scale at all. */
    public boolean isScored() {
        return minScore != null;
    }

    /**
     * Whether {@code score} is acceptable for this type.
     *
     * <p>Never clamps: an out-of-range score is rejected so a mistyped 11 cannot
     * enter the data and quietly move an average.
     */
    public boolean acceptsScore(Integer score) {
        if (score == null) {
            return !scoreRequired;
        }
        return isScored() && score >= minScore && score <= maxScore;
    }

    /** Human-readable scale, used in validation messages. */
    public String describeScale() {
        return isScored() ? minScore + "-" + maxScore : "unscored";
    }
}
