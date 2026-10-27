package com.shivang.crm.modules.workflow.config;

import java.time.Duration;
import java.util.List;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.dao.DataIntegrityViolationException;

import org.aopalliance.intercept.MethodInterceptor;

import com.shivang.crm.modules.workflow.service.WorkflowRuntimeException;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * Workflow event topology and consumer container configuration.
 *
 * <p>Delivery contract after this change:
 * <pre>
 *   transient downstream failure
 *        -> bounded in-process retry (exponential backoff)
 *        -> retries exhausted -> reject without requeue
 *        -> crm.workflow.events.dlx -> crm.workflow.events.dlq   (observable, not silent)
 *
 *   deterministic failure (business rule, malformed payload, integrity violation)
 *        -> AmqpRejectAndDontRequeueException, zero retries
 *        -> dead-lettered immediately
 * </pre>
 *
 * <p>The queue/exchange/binding beans are unchanged: the dead-letter path
 * already existed, it was simply never reached through a retry cycle.
 */
@Configuration
public class WorkflowRabbitConfig {

    public static final String WORKFLOW_QUEUE = "crm.workflow.events";

    // Poison messages rejected without requeue by WorkflowEventConsumer land here instead of being lost.
    public static final String WORKFLOW_DEAD_LETTER_EXCHANGE = "crm.workflow.events.dlx";
    public static final String WORKFLOW_DEAD_LETTER_QUEUE = "crm.workflow.events.dlq";
    public static final String WORKFLOW_DEAD_LETTER_ROUTING_KEY = "crm.workflow.events";

    /**
     * Deterministic failures. Retrying these can never succeed, so they bypass the
     * retry cycle entirely and are dead-lettered on the first attempt.
     */
    static final List<Class<? extends Throwable>> NON_RETRYABLE = List.of(
            BusinessException.class,
            WorkflowRuntimeException.class,
            IllegalArgumentException.class,
            AmqpRejectAndDontRequeueException.class,
            DataIntegrityViolationException.class);

    @Bean
    public Queue workflowEventQueue() {
        return QueueBuilder.durable(WORKFLOW_QUEUE)
            .withArgument("x-dead-letter-exchange", WORKFLOW_DEAD_LETTER_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", WORKFLOW_DEAD_LETTER_ROUTING_KEY)
            .build();
    }

    @Bean
    public Binding workflowEventBinding(Queue workflowEventQueue, TopicExchange crmEventsExchange) {
        return BindingBuilder.bind(workflowEventQueue)
            .to(crmEventsExchange)
            .with("crm.#");
    }

    @Bean
    public DirectExchange workflowDeadLetterExchange() {
        return new DirectExchange(WORKFLOW_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue workflowDeadLetterQueue() {
        return QueueBuilder.durable(WORKFLOW_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding workflowDeadLetterBinding(Queue workflowDeadLetterQueue, DirectExchange workflowDeadLetterExchange) {
        return BindingBuilder.bind(workflowDeadLetterQueue)
            .to(workflowDeadLetterExchange)
            .with(WORKFLOW_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MethodInterceptor workflowDeliveryRetryInterceptor(
            WorkflowMessageRecoverer recoverer,
            @Value("${app.rabbitmq.consumer.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.rabbitmq.consumer.retry.initial-interval-ms:500}") long initialIntervalMs,
            @Value("${app.rabbitmq.consumer.retry.multiplier:2.0}") double multiplier,
            @Value("${app.rabbitmq.consumer.retry.max-interval-ms:10000}") long maxIntervalMs) {

        RetryPolicy retryPolicy = buildRetryPolicy(maxAttempts, initialIntervalMs, multiplier, maxIntervalMs);

        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .recoverer(recoverer)
                .build();
    }

    /**
     * Visible for testing so the retry classification is asserted against the real
     * policy rather than a hand-rolled copy of it.
     */
    static RetryPolicy buildRetryPolicy(int maxAttempts, long initialIntervalMs, double multiplier, long maxIntervalMs) {
        int boundedAttempts = Math.max(1, maxAttempts);
        // maxRetries counts retries *after* the first delivery, so total deliveries = boundedAttempts.
        long maxRetries = boundedAttempts - 1L;

        return RetryPolicy.builder()
                .maxRetries(maxRetries)
                .delay(Duration.ofMillis(Math.max(0L, initialIntervalMs)))
                .multiplier(multiplier <= 0 ? 1.0d : multiplier)
                .maxDelay(Duration.ofMillis(Math.max(1L, maxIntervalMs)))
                .excludes(NON_RETRYABLE)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory workflowRabbitListenerContainerFactory(
            CachingConnectionFactory connectionFactory,
            MethodInterceptor workflowDeliveryRetryInterceptor,
            @Value("${app.rabbitmq.consumer.prefetch:50}") int prefetch,
            @Value("${app.rabbitmq.consumer.concurrency:1}") int concurrency,
            @Value("${app.rabbitmq.consumer.max-concurrency:4}") int maxConcurrency) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        // Bounded in-flight work per consumer thread. Previously prefetch defaulted to
        // 250 with a single consumer, which allowed a large unacked backlog.
        factory.setPrefetchCount(Math.max(1, prefetch));
        factory.setConcurrentConsumers(Math.max(1, concurrency));
        factory.setMaxConcurrentConsumers(Math.max(Math.max(1, concurrency), maxConcurrency));
        factory.setDefaultRequeueRejected(false);

        factory.setAdviceChain(workflowDeliveryRetryInterceptor);
        return factory;
    }
}
