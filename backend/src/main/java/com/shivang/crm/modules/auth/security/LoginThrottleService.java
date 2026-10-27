package com.shivang.crm.modules.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.shivang.crm.shared.resilience.InfrastructureMetrics;
import com.shivang.crm.shared.resilience.RedisAvailabilityGuard;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bounded brute-force protection for {@code POST /api/v1/auth/login}.
 *
 * <p>Throttle key is {@code (identifier, client address)} and only the SHA-256 of
 * each part is stored, so the Redis keyspace never contains an email address or an
 * IP address in clear text.
 *
 * <p>Atomicity: the counter increment and the window expiry happen in one Lua
 * script, so two concurrent failures cannot both observe "first failure" and skip
 * the {@code PEXPIRE}.
 *
 * <p>Redis outage behaviour: the throttle does <em>not</em> fail open. If Redis is
 * unreachable the service switches to a bounded in-process counter for the duration
 * of the outage. That keeps the protection present without making login depend on a
 * second datastore, and it re-attempts Redis on every call so protection is restored
 * automatically once Redis recovers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginThrottleService {

    private static final String KEY_PREFIX = "login:fail:";

    /**
     * INCR, then set the expiry only on the first increment. Doing both in one
     * script is what makes the window correct under concurrency.
     */
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
          redis.call('PEXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisAvailabilityGuard redisAvailabilityGuard;
    private final InfrastructureMetrics metrics;

    @Value("${app.security.login.throttle.max-failed-attempts:5}")
    private int maxFailedAttempts = 5;

    @Value("${app.security.login.throttle.lock-duration-ms:900000}")
    private long lockDurationMs = 900_000L;

    /** Hard cap on the in-process fallback so a credential-stuffing run cannot grow the heap. */
    @Value("${app.security.login.throttle.max-fallback-keys:10000}")
    private int maxFallbackKeys = 10_000;

    private final Map<String, FallbackWindow> fallbackWindows = new ConcurrentHashMap<>();

    @PostConstruct
    void registerAvailabilityGauge() {
        metrics.registerRedisAvailabilityGauge("login-throttle", redisAvailabilityGuard::isAvailable);
    }

    public boolean isEnabled() {
        return maxFailedAttempts > 0;
    }

    /**
     * True when this identifier/address pair has exhausted its attempt budget inside
     * the current window.
     */
    public boolean isLocked(String identifier, String clientAddress) {
        if (!isEnabled()) {
            return false;
        }
        String key = keyFor(identifier, clientAddress);

        if (redisAvailabilityGuard.isAvailable()) {
            try {
                String raw = redisTemplate.opsForValue().get(key);
                redisAvailabilityGuard.recordSuccess();
                return raw != null && Long.parseLong(raw) >= maxFailedAttempts;
            } catch (RuntimeException ex) {
                redisAvailabilityGuard.recordFailure("login.throttle.read", ex);
            }
        }
        return isLockedInFallback(key);
    }

    /** Records a failed attempt; the window is created on the first failure. */
    public void recordFailure(String identifier, String clientAddress) {
        if (!isEnabled()) {
            return;
        }
        String key = keyFor(identifier, clientAddress);

        if (redisAvailabilityGuard.isAvailable()) {
            try {
                redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(Math.max(1L, lockDurationMs)));
                redisAvailabilityGuard.recordSuccess();
                return;
            } catch (RuntimeException ex) {
                redisAvailabilityGuard.recordFailure("login.throttle.write", ex);
            }
        }
        recordFallbackFailure(key);
    }

    /** Clears the failure state after a successful authentication. */
    public void recordSuccess(String identifier, String clientAddress) {
        if (!isEnabled()) {
            return;
        }
        String key = keyFor(identifier, clientAddress);
        fallbackWindows.remove(key);
        if (redisAvailabilityGuard.isAvailable()) {
            try {
                redisTemplate.delete(key);
                redisAvailabilityGuard.recordSuccess();
            } catch (RuntimeException ex) {
                redisAvailabilityGuard.recordFailure("login.throttle.delete", ex);
            }
        }
    }

    public void recordThrottled() {
        metrics.recordLoginThrottled();
    }

    // ── in-process fallback, used only while Redis is unreachable ────────────

    private void recordFallbackFailure(String key) {
        metrics.recordLoginThrottleBackendFallback();
        pruneFallback();
        long now = System.currentTimeMillis();
        fallbackWindows.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new FallbackWindow(1, now + lockDurationMs);
            }
            return new FallbackWindow(existing.attempts() + 1, existing.windowEndsAtMillis());
        });
    }

    private boolean isLockedInFallback(String key) {
        FallbackWindow window = fallbackWindows.get(key);
        if (window == null) {
            return false;
        }
        if (window.isExpired(System.currentTimeMillis())) {
            fallbackWindows.remove(key, window);
            return false;
        }
        return window.attempts() >= maxFailedAttempts;
    }

    private void pruneFallback() {
        if (fallbackWindows.size() < maxFallbackKeys) {
            return;
        }
        long now = System.currentTimeMillis();
        fallbackWindows.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        if (fallbackWindows.size() >= maxFallbackKeys) {
            // Still full: drop everything rather than grow without bound. This only
            // happens under a Redis outage with a large attacker key space.
            fallbackWindows.clear();
            log.warn("Login throttle fallback map hit its cap of {} keys and was reset; raise "
                    + "app.security.login.throttle.max-fallback-keys if this is expected", maxFallbackKeys);
        }
    }

    // ── key derivation ───────────────────────────────────────────────────────

    private String keyFor(String identifier, String clientAddress) {
        return KEY_PREFIX + sha256(identifier) + ":" + sha256(clientAddress);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    private record FallbackWindow(int attempts, long windowEndsAtMillis) {
        boolean isExpired(long now) {
            return now >= windowEndsAtMillis;
        }
    }

    /** Test seam: number of entries currently held by the in-process fallback. */
    int fallbackSize() {
        return fallbackWindows.size();
    }
}
