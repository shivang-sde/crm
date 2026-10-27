package com.shivang.crm.modules.ticket.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import lombok.Setter;

/**
 * L3.1-P4 SLA configuration.
 *
 * <p>Durations are configured in whole minutes and exposed as {@link Duration}.
 * Minutes rather than hours because a support SLA of "1 hour" is not a realistic
 * response target, and integer minutes avoid both the float arithmetic and the
 * unit ambiguity of storing a raw number.
 *
 * <p>These are application-wide defaults, not per-tenant or per-priority values.
 * The resulting deadlines are snapshotted onto each Ticket at creation, so
 * changing these numbers later never rewrites an existing Ticket's obligations —
 * that is the reason a snapshot is stored instead of a reference to live policy.
 */
@Component
@ConfigurationProperties(prefix = "app.tickets.sla")
@Setter
public class TicketSlaProperties {

    /** Response target: 4 hours. */
    private long responseMinutes = 240L;

    /** Resolution target: 24 hours. */
    private long resolutionMinutes = 1440L;

    private boolean breachScanEnabled = true;

    private long breachScanDelayMs = 60000L;

    @Bean
    public Clock slaClock() {
        return Clock.systemUTC();
    }

    public long getResponseMinutes() {
        return responseMinutes;
    }

    public long getResolutionMinutes() {
        return resolutionMinutes;
    }

    public Duration responseDuration() {
        return Duration.ofMinutes(responseMinutes);
    }

    public Duration resolutionDuration() {
        return Duration.ofMinutes(resolutionMinutes);
    }

    public boolean isBreachScanEnabled() {
        return breachScanEnabled;
    }

    public long getBreachScanDelayMs() {
        return breachScanDelayMs;
    }
}
