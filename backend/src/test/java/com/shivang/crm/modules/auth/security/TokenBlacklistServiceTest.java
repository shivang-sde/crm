package com.shivang.crm.modules.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.shivang.crm.shared.resilience.InfrastructureMetrics;
import com.shivang.crm.shared.resilience.RedisAvailabilityGuard;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class TokenBlacklistServiceTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final RedisAvailabilityGuard guard = new RedisAvailabilityGuard(
            new InfrastructureMetrics(new SimpleMeterRegistry()));

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        ReflectionTestUtils.setField(guard, "openDurationMs", 15_000L);
        service = new TokenBlacklistService(redisTemplate, guard);
    }

    @Test
    void healthyRedis_reportsBlacklistedToken() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.TRUE);

        assertThat(service.isBlacklisted("jwt-token")).isTrue();
    }

    @Test
    void healthyRedis_reportsNonBlacklistedToken() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.FALSE);

        assertThat(service.isBlacklisted("jwt-token")).isFalse();
    }

    @Test
    void redisDown_bypassMode_doesNotThrowAndDoesNotLockEveryoneOut() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        ReflectionTestUtils.setField(service, "failureMode", "BYPASS");

        assertThat(service.isBlacklisted("jwt-token"))
                .as("BYPASS trades a bounded revocation gap for availability")
                .isFalse();
    }

    @Test
    void redisDown_denyMode_failsClosed() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        ReflectionTestUtils.setField(service, "failureMode", "DENY");

        assertThat(service.isBlacklisted("jwt-token"))
                .as("DENY is the strict, fully-unavailable-during-outage option")
                .isTrue();
    }

    @Test
    void redisDown_doesNotPropagateAsServerError() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        ReflectionTestUtils.setField(service, "failureMode", "BYPASS");

        // The original defect: an uncaught RedisConnectionFailureException escaped
        // JwtAuthenticationFilter and surfaced as HTTP 500 for every authenticated request.
        assertThatCode(() -> service.isBlacklisted("t")).doesNotThrowAnyException();
    }

    @Test
    void bypassWindowShortCircuitsSubsequentCalls() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        ReflectionTestUtils.setField(service, "failureMode", "BYPASS");

        service.isBlacklisted("a");
        int callsAfterFirst = redisInvocations();

        service.isBlacklisted("b");
        service.isBlacklisted("c");

        assertThat(redisInvocations())
                .as("inside the window Redis must not be called again, so there is no latency and no log storm")
                .isEqualTo(callsAfterFirst);
    }

    @Test
    void recoversAfterRedisReturns() {
        // Redis failed, the bypass window elapsed, and Redis is answering again.
        guard.recordFailure("blacklist.read", new RedisConnectionFailureException("down"));
        ReflectionTestUtils.setField(guard, "unavailableSinceNanos", System.nanoTime() - 20_000_000_000L);
        when(redisTemplate.hasKey(anyString())).thenReturn(Boolean.TRUE);
        ReflectionTestUtils.setField(service, "failureMode", "BYPASS");

        assertThat(service.isBlacklisted("t"))
                .as("once the window elapses the real answer is used again, not a bypass verdict")
                .isTrue();
        assertThat(guard.isAvailable()).isTrue();
    }

    @Test
    void logoutWriteIsBestEffortAndNeverThrows() {
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(valueOps).set(anyString(), anyString(), any(java.time.Duration.class));

        assertThatCode(() -> service.blacklist("t", 600)).doesNotThrowAnyException();
    }

    @Test
    void logoutWriteIsSkippedForNonPositiveExpiry() {
        service.blacklist("t", 0);

        verify(valueOps, never()).set(anyString(), anyString(), any(java.time.Duration.class));
    }

    private int redisInvocations() {
        return org.mockito.Mockito.mockingDetails(redisTemplate).getInvocations().size();
    }
}
