package com.shivang.crm.modules.workflow.service;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.shivang.crm.modules.workflow.config.WorkflowRabbitConfig;
import com.shivang.crm.shared.event.CanonicalCrmEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowEventConsumer {

    private final ObjectMapper objectMapper;
    private final WorkflowTriggerService workflowTriggerService;

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
            log.error("Rejecting invalid canonical CRM event message", ex);
            channel.basicReject(deliveryTag, false);
            return;
        }

        workflowTriggerService.process(event);
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