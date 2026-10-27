package com.shivang.crm.modules.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import com.shivang.crm.shared.resilience.InfrastructureMetrics;
import com.shivang.crm.shared.resilience.RedisAvailabilityGuard;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class LoginThrottleServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String IP = "203.0.113.7";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final RedisAvailabilityGuard guard = new RedisAvailabilityGuard(
            new InfrastructureMetrics(new SimpleMeterRegistry()));

    private LoginThrottleService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        ReflectionTestUtils.setField(guard, "openDurationMs", 15_000L);
        service = new LoginThrottleService(redisTemplate, guard, new InfrastructureMetrics(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(service, "lockDurationMs", 900_000L);
    }

    @Test
    void firstFailuresBelowThresholdAreNotLocked() {
        when(valueOps.get(anyString())).thenReturn("4");

        assertThat(service.isLocked(EMAIL, IP)).isFalse();
    }

    @Test
    void reachingTheThresholdLocks() {
        when(valueOps.get(anyString())).thenReturn("5");

        assertThat(service.isLocked(EMAIL, IP)).isTrue();
    }

    @Test
    void aboveThresholdStaysLocked() {
        when(valueOps.get(anyString())).thenReturn("99");

        assertThat(service.isLocked(EMAIL, IP)).isTrue();
    }

    @Test
    void failureCounterUsesAnAtomicScriptWithTheWindowAsArgument() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);

        service.recordFailure(EMAIL, IP);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), captor.capture());
        assertThat(captor.getValue())
                .as("INCR + PEXPIRE must be one script so concurrent failures cannot skip the expiry")
                .isEqualTo("900000");
    }

    @Test
    void successClearsTheFailureState() {
        service.recordSuccess(EMAIL, IP);

        verify(redisTemplate).delete(anyString());
    }

    @Test
    void differentIpGetsADifferentKey() {
        service.recordFailure(EMAIL, "203.0.113.7");
        service.recordFailure(EMAIL, "198.51.100.1");

        var captor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), captor.capture(), anyString());

        assertThat(new java.util.HashSet<>(captor.getAllValues()).size())
                .as("the client address must be part of the throttle key")
                .isEqualTo(2);
    }

    @Test
    void differentIdentifierGetsADifferentKey() {
        service.recordFailure("a@example.com", IP);
        service.recordFailure("b@example.com", IP);

        var captor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), captor.capture(), anyString());

        assertThat(new java.util.HashSet<>(captor.getAllValues()).size()).isEqualTo(2);
    }

    @Test
    void keyDoesNotLeakEmailOrIpInClearText() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);

        service.recordFailure(EMAIL, IP);

        var captor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(redisTemplate).execute(any(RedisScript.class), captor.capture(), anyString());
        String key = String.valueOf(captor.getValue().get(0));
        assertThat(key).doesNotContain(EMAIL).doesNotContain(IP).startsWith("login:fail:");
    }

    @Test
    void redisDown_doesNotDisableProtection() {
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        for (int i = 0; i < 5; i++) {
            assertThat(service.isLocked(EMAIL, IP)).isFalse();
            service.recordFailure(EMAIL, IP);
        }

        assertThat(service.isLocked(EMAIL, IP))
                .as("throttle must not fail open when Redis is down")
                .isTrue();
    }

    @Test
    void redisDown_loginEndpointStillUsableForUnrelatedIdentifiers() {
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        for (int i = 0; i < 10; i++) {
            service.recordFailure(EMAIL, IP);
        }

        assertThat(service.isLocked("someone-else@example.com", IP))
                .as("one attacker's lockout must not take out every other login")
                .isFalse();
    }

    @Test
    void redisRecoversAndProtectionReturnsToTheSharedBackend() {
        when(valueOps.get(anyString())).thenReturn("5");
        assertThat(service.isLocked(EMAIL, IP)).isTrue();

        verify(valueOps, times(1)).get(anyString());
    }

    @Test
    void fallbackWindowIsBounded() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));
        ReflectionTestUtils.setField(service, "maxFallbackKeys", 10);

        for (int i = 0; i < 500; i++) {
            service.recordFailure("user" + i + "@example.com", IP);
        }

        assertThat(service.fallbackSize()).isLessThanOrEqualTo(10);
    }

    @Test
    void disabledThrottleIsANoOp() {
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 0);

        assertThat(service.isLocked(EMAIL, IP)).isFalse();
        service.recordFailure(EMAIL, IP);
        service.recordSuccess(EMAIL, IP);

        verify(valueOps, times(0)).get(anyString());
    }
}
