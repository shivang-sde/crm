package com.shivang.crm.shared.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

import com.shivang.crm.shared.enums.CustomFrequency;
import com.shivang.crm.shared.enums.EndType;
import com.shivang.crm.shared.enums.RepeatType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Recurrence model for scheduling recurring tasks, calls, and meetings.
 * Stored as JSONB in database entities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recurrence {

    private RepeatType repeatType;

    /**
     * Every N units.
     *
     * Examples:
     * interval=2 and frequency=WEEKLY means every 2 weeks.
     */
    @Builder.Default
    private Integer interval = 1;

    private CustomFrequency customFrequency;

    /**
     * Used for weekly recurrence.
     * Example: MONDAY, WEDNESDAY, FRIDAY.
     */
    private Set<DayOfWeek> daysOfWeek;

    /**
     * Optional day of month, such as 15.
     */
    private Integer dayOfMonth;

    /**
     * Protects monthly recurrence for dates such as the 31st.
     */
    private MonthlyOverflowPolicy monthlyOverflowPolicy;

    private EndType endType;
    private Integer endAfterCount;
    private Instant endDate;

    /**
     * IANA timezone, for example Asia/Kolkata.
     */
    private String timezone;

    public enum MonthlyOverflowPolicy {
        SKIP,
        LAST_DAY_OF_MONTH
    }
}

