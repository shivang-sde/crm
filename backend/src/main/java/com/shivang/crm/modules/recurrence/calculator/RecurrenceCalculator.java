package com.shivang.crm.modules.recurrence.calculator;

import java.time.Instant;

import com.shivang.crm.shared.model.Recurrence;

public interface RecurrenceCalculator {

    Instant calculateNextOccurrence(Instant occurrence, Recurrence recurrence, String tenantTimezone, int currentOccurrenceCount);

    boolean hasMoreOccurrences(Instant occurrence, Recurrence recurrence, String tenantTimezone, int currentOccurrenceCount);
}
