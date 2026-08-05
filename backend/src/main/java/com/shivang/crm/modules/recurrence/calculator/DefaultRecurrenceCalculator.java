package com.shivang.crm.modules.recurrence.calculator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import org.springframework.stereotype.Component;

import com.shivang.crm.shared.enums.CustomFrequency;
import com.shivang.crm.shared.enums.MonthlyOverflowPolicy;
import com.shivang.crm.shared.enums.RepeatType;
import com.shivang.crm.shared.model.Recurrence;
import com.shivang.crm.shared.validation.RecurrenceValidator;


@Component
public class DefaultRecurrenceCalculator implements RecurrenceCalculator {

    @Override
    public Instant calculateNextOccurrence(Instant occurrence, Recurrence recurrence, String tenantTimezone, int currentOccurrenceCount) {
        if (occurrence == null || recurrence == null) {
            throw new IllegalArgumentException("Occurrence and recurrence are required");
        }

        RecurrenceValidator.validate(recurrence);

        ZoneId zoneId = resolveZoneId(recurrence.getTimezone(), tenantTimezone);
        ZonedDateTime current = occurrence.atZone(zoneId);
        ZonedDateTime next = advance(current, recurrence, zoneId);

        if (isPastEnd(next, recurrence, currentOccurrenceCount)) {
            return null;
        }

        return next.toInstant();
    }

    @Override
    public boolean hasMoreOccurrences(Instant occurrence, Recurrence recurrence, String tenantTimezone, int currentOccurrenceCount) {
        if (occurrence == null || recurrence == null) {
            return false;
        }

        Instant next = calculateNextOccurrence(occurrence, recurrence, tenantTimezone, currentOccurrenceCount);
        return next != null;
    }

    private ZonedDateTime advance(ZonedDateTime occurrence, Recurrence recurrence, ZoneId zoneId) {
        RepeatType repeatType = recurrence.getRepeatType();
        Integer interval = recurrence.getInterval();

        switch (repeatType) {
            case DAILY -> {
                return occurrence.plusDays(interval);
            }
            case WEEKLY -> {
                Set<java.time.DayOfWeek> days = recurrence.getDaysOfWeek();
                if (days == null || days.isEmpty()) {
                    return occurrence.plusWeeks(interval);
                }

                ZonedDateTime candidate = occurrence.plusDays(1);
                for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
                    if (days.contains(candidate.getDayOfWeek())) {
                        return candidate;
                    }
                    candidate = candidate.plusDays(1);
                }
                return occurrence.plusWeeks(interval);
            }
            case MONTHLY -> {
                return advanceMonthly(occurrence, recurrence, interval, zoneId);
            }
            case YEARLY -> {
                return occurrence.plusYears(interval);
            }
            case CUSTOM -> {
                return advanceByCustomFrequency(occurrence, recurrence, interval, zoneId);
            }
            default -> throw new IllegalArgumentException("Unsupported recurrence type: " + repeatType);
        }
    }

    private ZonedDateTime advanceMonthly(ZonedDateTime occurrence, Recurrence recurrence, Integer interval, ZoneId zoneId) {
        Integer dayOfMonth = recurrence.getDayOfMonth();
        int targetDay = dayOfMonth != null ? dayOfMonth : occurrence.getDayOfMonth();
        int monthIncrement = interval;

        ZonedDateTime candidate = occurrence.plusMonths(monthIncrement);
        int lastDayOfMonth = candidate.toLocalDate().lengthOfMonth();

        if (targetDay > lastDayOfMonth) {
            MonthlyOverflowPolicy policy = recurrence.getMonthlyOverflowPolicy();
            if (policy == MonthlyOverflowPolicy.SKIP) {
                return candidate.plusMonths(1);
            }
            targetDay = lastDayOfMonth;
        }

        return candidate.withDayOfMonth(targetDay).withHour(occurrence.getHour())
                .withMinute(occurrence.getMinute())
                .withSecond(occurrence.getSecond())
                .withNano(occurrence.getNano());
    }

    private ZonedDateTime advanceByCustomFrequency(ZonedDateTime occurrence, Recurrence recurrence, Integer interval, ZoneId zoneId) {
        CustomFrequency customFrequency = recurrence.getCustomFrequency();
        if (customFrequency == null) {
            return occurrence;
        }

        return switch (customFrequency) {
            case DAILY -> occurrence.plusDays(interval);
            case WEEKLY -> occurrence.plusWeeks(interval);
            case MONTHLY -> advanceMonthly(occurrence, recurrence, interval, zoneId);
            case YEARLY -> occurrence.plusYears(interval);
        };
    }

    private boolean isPastEnd(ZonedDateTime next, Recurrence recurrence, int currentOccurrenceCount) {
        if (recurrence.getEndType() == null) {
            return false;
        }

        return switch (recurrence.getEndType()) {
            case AFTER_N_TIMES -> {
                Integer endAfterCount = recurrence.getEndAfterCount();
                if (endAfterCount == null || endAfterCount < 1) {
                    yield false;
                }
                yield currentOccurrenceCount >= endAfterCount;
            }
            case ON_DATE -> recurrence.getEndDate() != null && !next.toInstant().isBefore(recurrence.getEndDate());
            case NEVER -> false;
        };
    }

    private ZoneId resolveZoneId(String recurrenceTimezone, String tenantTimezone) {
        if (recurrenceTimezone != null && !recurrenceTimezone.isBlank()) {
            return ZoneId.of(recurrenceTimezone);
        }
        if (tenantTimezone != null && !tenantTimezone.isBlank()) {
            return ZoneId.of(tenantTimezone);
        }
        return ZoneId.of("UTC");
    }
}
