package com.shivang.crm.shared.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

import com.shivang.crm.shared.enums.CustomFrequency;
import com.shivang.crm.shared.enums.EndType;
import com.shivang.crm.shared.enums.MonthlyOverflowPolicy;
import com.shivang.crm.shared.enums.RepeatType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Recurrence rule for tasks, calls, and meetings.
 *
 * This class stores configuration only.
 * Runtime recurrence state belongs in RecurrenceSchedule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recurrence {

    private RepeatType repeatType;

    /**
     * Repeat every N units.
     *
     * Examples:
     * 1 = every week
     * 2 = every two weeks
     */
    @Builder.Default
    private Integer interval = 1;

    /**
     * Required only when repeatType is CUSTOM.
     */
    private CustomFrequency customFrequency;

    /**
     * Used for weekly recurrence.
     *
     * If empty, the weekday of the original occurrence is used.
     */
    private Set<DayOfWeek> daysOfWeek;

    /**
     * Used for monthly recurrence.
     *
     * Valid range: 1-31.
     * If null, the original occurrence day is used.
     */
    private Integer dayOfMonth;

    /**
     * Defines behavior when dayOfMonth does not exist
     * in the target month.
     */
    @Builder.Default
    private MonthlyOverflowPolicy monthlyOverflowPolicy =
            MonthlyOverflowPolicy.LAST_DAY_OF_MONTH;

    @Builder.Default
    private EndType endType = EndType.NEVER;

    /**
     * Required when endType is AFTER_N_TIMES.
     *
     * The initial occurrence counts as occurrence number one.
     */
    private Integer endAfterCount;

    /**
     * Required when endType is ON_DATE.
     *
     * An occurrence equal to endDate is allowed.
     * An occurrence after endDate is rejected.
     */
    private Instant endDate;

    /**
     * IANA timezone, for example Asia/Kolkata.
     *
     * Fallback order:
     * recurrence timezone -> tenant timezone -> UTC.
     */
    private String timezone;
}