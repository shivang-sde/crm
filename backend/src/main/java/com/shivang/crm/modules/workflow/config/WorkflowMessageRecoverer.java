package com.shivang.crm.modules.workflow.config;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

import com.shivang.crm.shared.resilience.InfrastructureMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Terminal handler for workflow event deliveries whose retries were exhausted.
 *
 * <p>Emits an alertable signal and then rejects without requeue, which routes
 * the message to {@code crm.workflow.events.dlx} / {@code crm.workflow.events.dlq}
 * (the queue already declares those arguments, so no new topology is created).
 *
 * <p>Logs structured metadata only. The body is never logged because canonical
 * CRM events carry tenant and record identifiers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowMessageRecoverer implements MessageRecoverer {

    private final RejectAndDontRequeueRecoverer delegate = new RejectAndDontRequeueRecoverer();

    private final InfrastructureMetrics metrics;

    @Override
    public void recover(Message message, Throwable cause) {
        String messageId = message == null || message.getMessageProperties() == null
                ? null
                : message.getMessageProperties().getMessageId();
        String receivedExchange = message == null || message.getMessageProperties() == null
                ? null
                : message.getMessageProperties().getReceivedExchange();
        String routingKey = message == null || message.getMessageProperties() == null
                ? null
                : message.getMessageProperties().getReceivedRoutingKey();

        metrics.recordRabbitDeadLettered(WorkflowRabbitConfig.WORKFLOW_QUEUE);

        log.error("Workflow event delivery exhausted its retries and will be dead-lettered. "
                        + "queue={}, receivedExchange={}, routingKey={}, messageId={}, errorType={}, error={}. "
                        + "The message is preserved in {} for manual inspection.",
                WorkflowRabbitConfig.WORKFLOW_QUEUE, receivedExchange, routingKey, messageId,
                cause == null ? "unknown" : cause.getClass().getSimpleName(),
                cause == null ? null : cause.getMessage(),
                WorkflowRabbitConfig.WORKFLOW_DEAD_LETTER_QUEUE);

        delegate.recover(message, cause);
    }
}
