package com.shivang.crm.modules.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AN-15 D: activity rate summary for the selected period and inside the
 * caller's resolved analytics scope.
 *
 *   taskCompletionRate = completedTasks / tasksCreated * 100
 *       completedTasks = tasks created in the period AND completed within it
 *                        (same created-window definition as
 *                        AnalyticsSummaryResponse.ActivityMetrics.completedTasks)
 *       tasksCreated    = all tasks created in the period (summary.tasks)
 *   taskOverdueRate    = overdueTasks / openTasks * 100
 *       overdueTasks   = tasks created in the period, not closed, dueDate < now
 *       openTasks      = tasks created in the period, not closed
 *   meetingStatus      = PLANNED/HELD/NOT_HELD/CANCELLED counts and
 *                        heldRate = held / (held + notHeld + cancelled) * 100,
 *                        0 on a zero denominator (excludes planned).
 *
 * All rates are bounded to [0,100]; a zero denominator yields 0.0 (never NaN).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivityRatesSummary {

    /** completedTasks / tasksCreated * 100 (created-window convention). */
    private double taskCompletionRate;

    /** overdueTasks / openTasks * 100. */
    private double taskOverdueRate;

    /** Meeting status distribution and held rate. */
    private MeetingStatusSummary meetingStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MeetingStatusSummary {
        private long planned;
        private long held;
        private long notHeld;
        private long cancelled;
        private double heldRate;
    }
}
