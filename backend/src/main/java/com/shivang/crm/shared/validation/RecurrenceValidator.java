package com.shivang.crm.shared.validation;

import java.time.DateTimeException;
import java.time.ZoneId;

import com.shivang.crm.shared.enums.EndType;
import com.shivang.crm.shared.enums.RepeatType;
import com.shivang.crm.shared.model.Recurrence;

public final class RecurrenceValidator {

    private RecurrenceValidator() {
    }

    public static void validate(Recurrence recurrence) {
        if (recurrence == null) {
            throw new IllegalArgumentException("Recurrence cannot be null");
        }

        if (recurrence.getRepeatType() == null) {
            throw new IllegalArgumentException("repeatType is required");
        }

        Integer interval = recurrence.getInterval();
        if (interval == null || interval < 1) {
            throw new IllegalArgumentException("interval must be greater than or equal to 1");
        }

        if (recurrence.getRepeatType() == RepeatType.CUSTOM
                && recurrence.getCustomFrequency() == null) {
            throw new IllegalArgumentException("customFrequency is required when repeatType is CUSTOM");
        }

        Integer dayOfMonth = recurrence.getDayOfMonth();
        if (dayOfMonth != null && (dayOfMonth < 1 || dayOfMonth > 31)) {
            throw new IllegalArgumentException("dayOfMonth must be between 1 and 31");
        }

        if (recurrence.getEndType() == EndType.AFTER_N_TIMES
                && recurrence.getEndAfterCount() == null) {
            throw new IllegalArgumentException("endAfterCount is required when endType is AFTER_N_TIMES");
        }

        if (recurrence.getEndType() == EndType.ON_DATE
                && recurrence.getEndDate() == null) {
            throw new IllegalArgumentException("endDate is required when endType is ON_DATE");
        }

        if (recurrence.getTimezone() != null && !recurrence.getTimezone().isBlank()) {
            try {
                ZoneId.of(recurrence.getTimezone());
            } catch (DateTimeException ex) {
                throw new IllegalArgumentException("timezone must be a valid IANA timezone", ex);
            }
        }
    }
}
