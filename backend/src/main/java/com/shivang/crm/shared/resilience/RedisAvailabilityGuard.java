package com.shivang.crm.shared.resilience;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Small, self-healing bypass for Redis.
 *
 * <p>Purpose: a Redis outage must not turn every authenticated request into a
 * 5xx, and it must not produce one WARN line per request. This is deliberately
 * <em>not</em> a general resilience framework — it is a single open-window
 * timer with the following contract:
 *
 * <ul>
 *   <li>After a connection failure the dependency is treated as unavailable for
 *       {@code app.resilience.redis.open-duration-ms}; calls inside that window
 *       short-circuit without touching the socket.</li>
 *   <li>Once the window elapses the next call probes for real. Success closes
 *       the window; failure re-opens it. There is no latching and no manual
 *       reset, so a recovered Redis is picked up automatically.</li>
 *   <li>State is a single volatile timestamp, so there is no lock and no
 *       unbounded map.</li>
 * </ul>
 *
 * <p>This guard only classifies <em>availability</em>. It deliberately does not
 * decide whether a given operation may fail open — that decision belongs to the
 * caller, because only the caller knows whether the operation is security
 * critical.
 */
@Component
@Slf4j
public class RedisAvailabilityGuard {

    private static final String DEPENDENCY = "redis";

    private final InfrastructureMetrics metrics;

    /** 0 means "no recorded failure", i.e. healthy. */
    private volatile long unavailableSinceNanos;

    @Value("${app.resilience.redis.open-duration-ms:15000}")
    private long openDurationMs = 15_000L;

    public RedisAvailabilityGuard(InfrastructureMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * True when a real Redis call should be attempted. False means the bypass
     * window is open and the caller must not touch Redis.
     */
    public boolean isAvailable() {
        long since = unavailableSinceNanos;
        if (since == 0L) {
            return true;
        }
        return System.nanoTime() - since >= Duration.ofMillis(openDurationMs).toNanos();
    }

    public void recordSuccess() {
        if (unavailableSinceNanos != 0L) {
            log.info("Redis availability restored; dependent operations resume using Redis");
            metrics.recordCircuitClosed(DEPENDENCY);
            unavailableSinceNanos = 0L;
        }
    }

    public void recordFailure(String operation, Throwable cause) {
        boolean wasAvailable = unavailableSinceNanos == 0L;
        unavailableSinceNanos = System.nanoTime();
        metrics.recordRedisDegraded(operation, cause);
        if (wasAvailable) {
            log.error("Redis unreachable during operation='{}'; Redis-backed calls will bypass Redis for up to {}ms. "
                    + "JWT signature verification is unaffected, so this degrades revocation/throttle checks "
                    + "rather than authentication itself.", operation, openDurationMs, cause);
            metrics.recordCircuitOpened(DEPENDENCY);
        }
    }

    /** True when {@code cause} indicates Redis could not be reached. */
    public static boolean isConnectionFailure(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof RedisConnectionFailureException || t instanceof RedisSystemException) {
                return true;
            }
        }
        return false;
    }
}
