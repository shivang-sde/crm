package com.shivang.crm.modules.recurrence.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.shivang.crm.shared.enums.EndType;
import com.shivang.crm.shared.enums.RepeatType;
import com.shivang.crm.shared.model.Recurrence;

class DefaultRecurrenceCalculatorTest {

    private final DefaultRecurrenceCalculator calculator = new DefaultRecurrenceCalculator();

    @Test
    void weeklyRecurrenceShouldAdvanceToNextSelectedDayWithinSameWeek() {
        Recurrence recurrence = Recurrence.builder()
                .repeatType(RepeatType.WEEKLY)
                .interval(1)
                .daysOfWeek(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
                .build();

        Instant occurrence = Instant.parse("2024-01-01T09:00:00Z");

        Instant next = calculator.calculateNextOccurrence(occurrence, recurrence, "UTC", 1);

        assertEquals(Instant.parse("2024-01-03T09:00:00Z"), next);
    }

    @Test
    void afterNTimesShouldHonorCurrentOccurrenceCount() {
        Recurrence recurrence = Recurrence.builder()
                .repeatType(RepeatType.DAILY)
                .interval(1)
                .endType(EndType.AFTER_N_TIMES)
                .endAfterCount(2)
                .build();

        Instant occurrence = Instant.parse("2024-01-01T09:00:00Z");

        assertEquals(Instant.parse("2024-01-02T09:00:00Z"), calculator.calculateNextOccurrence(occurrence, recurrence, "UTC", 1));
        assertNull(calculator.calculateNextOccurrence(occurrence, recurrence, "UTC", 2));
    }
}
