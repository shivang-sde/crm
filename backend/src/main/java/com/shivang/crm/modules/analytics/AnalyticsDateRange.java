package com.shivang.crm.modules.analytics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.shivang.crm.shared.exception.BusinessException;

/**
 * Small validated [from, to) range over the project's existing UTC Instant
 * convention. Bounds are inclusive-exclusive so consecutive ranges do not
 * double count records created exactly at a boundary.
 */
public record AnalyticsDateRange(Instant from, Instant to) {

    private static final long DEFAULT_DAYS = 30;
    private static final long MAX_DAYS = 366;

    public static AnalyticsDateRange resolve(String rawFrom, String rawTo) {
        Instant to = parse(rawTo, "to");
        Instant from = parse(rawFrom, "from");

        if (to == null && from == null) {
            to = Instant.now();
            from = to.minus(DEFAULT_DAYS, ChronoUnit.DAYS);
        } else if (to == null) {
            to = Instant.now();
        } else if (from == null) {
            from = to.minus(DEFAULT_DAYS, ChronoUnit.DAYS);
        }

        if (!from.isBefore(to)) {
            throw new BusinessException("INVALID_DATE_RANGE", "'from' must be before 'to'");
        }
        if (from.until(to, ChronoUnit.DAYS) > MAX_DAYS) {
            throw new BusinessException("INVALID_DATE_RANGE",
                    "Date range must not exceed " + MAX_DAYS + " days");
        }
        return new AnalyticsDateRange(from, to);
    }

    private static Instant parse(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception e) {
            throw new BusinessException("INVALID_DATE_RANGE",
                    "'" + name + "' must be an ISO-8601 instant (e.g. 2026-01-01T00:00:00Z)");
        }
    }
}
