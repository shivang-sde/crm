package com.shivang.crm.modules.workflow.service;

import java.io.IOException;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.shivang.crm.modules.workflow.config.WorkflowRabbitConfig;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.resilience.InfrastructureMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowEventConsumer {

    private final ObjectMapper objectMapper;
    private final WorkflowTriggerService workflowTriggerService;
    private final InfrastructureMetrics metrics;

    @RabbitListener(
        queues = WorkflowRabbitConfig.WORKFLOW_QUEUE,
        containerFactory = "workflowRabbitListenerContainerFactory"
    )
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        CanonicalCrmEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), CanonicalCrmEvent.class);
            validate(event);
        } catch (JacksonException | IllegalArgumentException ex) {
            // Malformed payload: a permanent condition. Reject immediately so it is
            // dead-lettered without consuming the retry budget.
            log.error("Rejecting invalid canonical CRM event message", ex);
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            workflowTriggerService.process(event);
        } catch (BusinessException | WorkflowRuntimeException ex) {
            // Deterministic failure (invalid trigger filter, unparseable actor metadata,
            // business rule violation). Retrying cannot change the outcome, so skip the
            // retry cycle and dead-letter straight away.
            log.error("Canonical CRM event rejected by workflow trigger logic; dead-lettering without retry. "
                    + "eventId={}, entityType={}, eventType={}, errorType={}, error={}",
                    event.eventId(), event.entityType(), event.eventType(),
                    ex.getClass().getSimpleName(), ex.getMessage());
            throw new AmqpRejectAndDontRequeueException(
                    "Non-retryable workflow event processing failure", ex);
        } catch (RuntimeException ex) {
            // Transient downstream failure (database, lock contention, etc). The retry
            // advice retries a bounded number of times and then dead-letters. Count the
            // attempt so a sustained outage is visible even if a later delivery succeeds.
            metrics.recordRabbitRetry(WorkflowRabbitConfig.WORKFLOW_QUEUE);
            log.warn("Workflow event delivery failed and will be retried a bounded number of times before "
                            + "dead-lettering. eventId={}, errorType={}, error={}",
                    event.eventId(), ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }

        channel.basicAck(deliveryTag, false);
    }

    private void validate(CanonicalCrmEvent event) {
        if (event == null || event.eventId() == null || event.tenantId() == null
            || event.entityType() == null || event.entityType().isBlank()
            || event.entityId() == null || event.eventType() == null || event.eventType().isBlank()) {
            throw new IllegalArgumentException("Canonical CRM event identity is incomplete");
        }
    }
}
