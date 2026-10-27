package com.shivang.crm.modules.auth.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.shivang.crm.shared.resilience.RedisAvailabilityGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed token blacklist for logout invalidation.
 * When a user logs out, their access token is blacklisted in Redis
 * with a TTL matching the token's remaining lifetime.
 *
 * <p><b>Redis outage behaviour.</b> This blacklist is a <em>revocation</em> set,
 * not the authentication decision: the JWT signature and expiry are verified
 * without Redis by {@code JwtAuthenticationFilter}. So when Redis is
 * unreachable the default is {@code BYPASS} — a token that was explicitly
 * logged out keeps working until its natural expiry (bounded by
 * {@code jwt.access-token-expiry-ms}), instead of rejecting 100% of traffic.
 *
 * <p>Set {@code app.security.token-blacklist.redis-failure-mode=DENY} to
 * invert this if the deployment requires strict fail-closed revocation. That
 * trade-off (total unavailability vs. a narrower revocation gap) is a policy
 * decision, so it is configuration rather than a hardcoded choice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:";

    private enum FailureMode {
        BYPASS,
        DENY
    }

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisAvailabilityGuard redisAvailabilityGuard;

    @Value("${app.security.token-blacklist.redis-failure-mode:BYPASS}")
    private String failureMode = "BYPASS";

    public void blacklist(String token, long expirySeconds) {
        if (expirySeconds <= 0) {
            return;
        }
        if (!redisAvailabilityGuard.isAvailable()) {
            redisAvailabilityGuard.recordFailure("blacklist.write", new IllegalStateException("bypass window open"));
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + token,
                    "revoked",
                    Duration.ofSeconds(expirySeconds)
            );
            redisAvailabilityGuard.recordSuccess();
        } catch (RuntimeException ex) {
            // Revocation is best effort at this point: the token is already being
            // discarded by the client and the refresh token is revoked in the DB.
            redisAvailabilityGuard.recordFailure("blacklist.write", ex);
        }
    }

    public boolean isBlacklisted(String token) {
        if (!redisAvailabilityGuard.isAvailable()) {
            redisAvailabilityGuard.recordFailure("blacklist.read", new IllegalStateException("bypass window open"));
            return failClosed();
        }
        try {
            boolean present = Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
            redisAvailabilityGuard.recordSuccess();
            return present;
        } catch (RuntimeException ex) {
            redisAvailabilityGuard.recordFailure("blacklist.read", ex);
            return failClosed();
        }
    }

    private boolean failClosed() {
        return FailureMode.DENY.name().equalsIgnoreCase(failureMode);
    }
}
