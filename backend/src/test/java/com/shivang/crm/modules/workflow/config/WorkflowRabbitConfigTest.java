package com.shivang.crm.modules.workflow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;

import com.shivang.crm.modules.workflow.service.WorkflowRuntimeException;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.resilience.InfrastructureMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class WorkflowRabbitConfigTest {

    private final WorkflowRabbitConfig config = new WorkflowRabbitConfig();

    private RetryPolicy policy(int maxAttempts) {
        return WorkflowRabbitConfig.buildRetryPolicy(maxAttempts, 500L, 2.0d, 10_000L);
    }

    @Test
    void transientDownstreamFailuresAreRetried() {
        assertThat(attemptsFor(policy(3), new DataAccessResourceFailureException("db down")))
                .isEqualTo(3);
        assertThat(attemptsFor(policy(3), new CannotAcquireLockException("row locked")))
                .isEqualTo(3);
    }

    @Test
    void deterministicFailuresAreNotRetried() {
        assertThat(attemptsFor(policy(3), new BusinessException("X", "business rule"))).isEqualTo(1);
        assertThat(attemptsFor(policy(3), new WorkflowRuntimeException("WORKFLOW_ACTOR_INVALID", "bad actor")))
                .isEqualTo(1);
        assertThat(attemptsFor(policy(3), new IllegalArgumentException("malformed payload"))).isEqualTo(1);
        assertThat(attemptsFor(policy(3),
                new org.springframework.dao.DataIntegrityViolationException("unique"))).isEqualTo(1);
    }

    @Test
    void deterministicFailuresAreExcludedThroughTheCauseChain() {
        assertThat(attemptsFor(policy(3),
                new IllegalStateException("wrapper", new BusinessException("X", "inner business rule"))))
                .as("a business failure wrapped in a transport exception must still skip the retry cycle")
                .isEqualTo(1);
    }

    @Test
    void retryBudgetIsBounded() {
        assertThat(attemptsFor(policy(3), new RuntimeException("x")))
                .as("max-attempts=3 means one delivery plus two retries, then dead-letter")
                .isEqualTo(3);
        assertThat(attemptsFor(policy(6), new RuntimeException("x"))).isEqualTo(6);
    }

    @Test
    void singleAttemptMeansNoRetriesAtAll() {
        assertThat(attemptsFor(policy(1), new RuntimeException("x"))).isEqualTo(1);
    }

    @Test
    void retryBudgetIsConfigurable() {
        assertThat(attemptsFor(policy(1), new RuntimeException("x"))).isEqualTo(1);
        assertThat(attemptsFor(policy(5), new RuntimeException("x"))).isEqualTo(5);
    }

    @Test
    void nonPositiveConfigurationIsClampedToASingleAttempt() {
        assertThat(attemptsFor(WorkflowRabbitConfig.buildRetryPolicy(0, 500L, 2.0d, 10_000L),
                new RuntimeException("x"))).isEqualTo(1);
    }

    @Test
    void backoffIsConfigured() {
        var backOff = policy(5).getBackOff();

        assertThat(backOff).as("a bounded exponential backoff must be configured").isNotNull();
        assertThat(backOff.start().nextBackOff())
                .as("the first wait must be positive so a failing dependency is not hammered")
                .isPositive();
    }

    /**
     * {@code RetryPolicy} is stateless; the attempt counter lives in the execution
     * state that {@link RetryTemplate} manages. This is the same template the AMQP
     * retry interceptor uses internally, so this is the real bounded-retry behaviour.
     */
    private static int attemptsFor(RetryPolicy policy, Throwable failure) {
        RetryTemplate template = new RetryTemplate(policy);
        int[] attempts = {0};
        assertThatCode(() -> template.execute(() -> {
            attempts[0]++;
            throw failure;
        })).isInstanceOf(RetryException.class);
        return attempts[0];
    }

    @Test
    void deadLetterTopologyIsUnchanged() {
        assertThat(config.workflowEventQueue().getName()).isEqualTo(WorkflowRabbitConfig.WORKFLOW_QUEUE);
        assertThat(config.workflowDeadLetterQueue().getName())
                .isEqualTo(WorkflowRabbitConfig.WORKFLOW_DEAD_LETTER_QUEUE);
        assertThat(config.workflowEventQueue().getArguments())
                .containsEntry("x-dead-letter-exchange", WorkflowRabbitConfig.WORKFLOW_DEAD_LETTER_EXCHANGE);
        assertThat(config.workflowEventQueue().getArguments())
                .containsEntry("x-dead-letter-routing-key", WorkflowRabbitConfig.WORKFLOW_DEAD_LETTER_ROUTING_KEY);
    }

    @Test
    void bindingStillCatchesEveryCanonicalEvent() {
        assertThat(config.workflowEventBinding(config.workflowEventQueue(),
                new org.springframework.amqp.core.TopicExchange("crm.events", true, false))
                .getRoutingKey()).isEqualTo("crm.#");
    }

    @Test
    void interceptorIsAnAdviceSoItCanJoinTheContainerChain() {
        assertThat(config.workflowDeliveryRetryInterceptor(
                new WorkflowMessageRecoverer(new InfrastructureMetrics(new SimpleMeterRegistry())),
                3, 500L, 2.0d, 10_000L))
                .isInstanceOf(org.aopalliance.intercept.MethodInterceptor.class);
    }
}
