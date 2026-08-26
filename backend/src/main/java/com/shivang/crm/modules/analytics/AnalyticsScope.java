package com.shivang.crm.modules.analytics;

/**
 * Supported analytics perspectives. The rank encodes the authority ordering
 * used to validate a caller-requested scope against the scope derived from
 * the authenticated identity.
 */
public enum AnalyticsScope {
    USER(0),
    TENANT(1),
    RESELLER(2),
    PLATFORM(3);

    private final int rank;

    AnalyticsScope(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    public static AnalyticsScope fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown analytics scope: " + value);
        }
    }
}
