package com.shivang.crm.shared.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RedisAvailabilityGuardTest {

    private RedisAvailabilityGuard guard;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        guard = new RedisAvailabilityGuard(new InfrastructureMetrics(registry));
        ReflectionTestUtils.setField(guard, "openDurationMs", 15_000L);
    }

    @Test
    void isAvailableBeforeAnyFailure() {
        assertThat(guard.isAvailable()).isTrue();
    }

    @Test
    void opensBypassWindowOnConnectionFailure() {
        guard.recordFailure("blacklist.read", new RedisConnectionFailureException("down"));

        assertThat(guard.isAvailable())
                .as("calls inside the window must not touch the socket")
                .isFalse();
    }

    @Test
    void secondFailureInsideWindowDoesNotEmitAnotherOutageSignal() {
        guard.recordFailure("blacklist.read", new RedisConnectionFailureException("down"));
        double afterFirst = registry.get("crm.resilience.circuit.opened").counter().count();
        guard.recordFailure("blacklist.read", new RedisConnectionFailureException("down"));
        guard.recordFailure("blacklist.read", new RedisConnectionFailureException("down"));

        assertThat(registry.get("crm.resilience.circuit.opened").counter().count())
                .as("one alert per outage, not one per request")
                .isEqualTo(afterFirst);
        assertThat(registry.get("crm.redis.degraded").counter().count()).isEqualTo(3.0d);
    }

    @Test
    void recoversAutomaticallyAfterTheWindowElapses() {
        guard.recordFailure("blacklist.read", new RedisConnectionFailureException("down"));
        // simulate the window elapsing
        ReflectionTestUtils.setField(guard, "unavailableSinceNanos", System.nanoTime() - Duration.ofSeconds(30).toNanos());

        assertThat(guard.isAvailable()).as("a real probe must be attempted after the window").isTrue();

        guard.recordSuccess();

        assertThat(guard.isAvailable()).isTrue();
        assertThat(registry.get("crm.resilience.circuit.closed").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void windowIsTimeBoundAndNotLatched() {
        guard.recordFailure("x", new RedisConnectionFailureException("down"));
        assertThat(guard.isAvailable()).isFalse();
        ReflectionTestUtils.setField(guard, "unavailableSinceNanos", System.nanoTime() - Duration.ofMillis(16_000).toNanos());
        assertThat(guard.isAvailable())
                .as("guard must never permanently disable Redis after a single failure")
                .isTrue();
    }

    @Test
    void classifiesConnectionAndSystemFailuresThroughTheCauseChain() {
        assertThat(RedisAvailabilityGuard.isConnectionFailure(
                new IllegalStateException("wrapped",
                        new RedisConnectionFailureException("down")))).isTrue();
        assertThat(RedisAvailabilityGuard.isConnectionFailure(
                new RuntimeException("unrelated"))).isFalse();
    }
}
