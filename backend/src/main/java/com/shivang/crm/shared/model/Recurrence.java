package com.shivang.crm.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

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
    private CustomFrequency customFrequency;
    private Integer interval;
    private EndType endType;
    private Integer endAfterCount;
    private Instant endDate;
}

enum RepeatType {
    DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM
}

enum CustomFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

enum EndType {
    NEVER, AFTER_N_TIMES, ON_DATE
}

