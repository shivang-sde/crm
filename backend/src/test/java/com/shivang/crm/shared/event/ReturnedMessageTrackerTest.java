package com.shivang.crm.shared.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.ReturnedMessage;

import com.shivang.crm.shared.resilience.InfrastructureMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ReturnedMessageTrackerTest {

    private ReturnedMessageTracker tracker;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        tracker = new ReturnedMessageTracker(new InfrastructureMetrics(registry));
    }

    @Test
    void unroutableMessageIsReportedToTheOutboxPublisher() {
        tracker.onReturned(returned("evt-1", "crm.LEAD.CREATED"));

        assertThat(tracker.wasReturned("evt-1"))
                .as("a confirmed but unroutable publish must not be reported as delivered")
                .isTrue();
    }

    @Test
    void readingTheResultConsumesIt() {
        tracker.onReturned(returned("evt-1", "crm.LEAD.CREATED"));

        assertThat(tracker.wasReturned("evt-1")).isTrue();
        assertThat(tracker.wasReturned("evt-1"))
                .as("the tracker is single-shot so a later event cannot reuse the verdict")
                .isFalse();
    }

    @Test
    void unrelatedMessageIdsAreNotReported() {
        tracker.onReturned(returned("evt-1", "crm.LEAD.CREATED"));

        assertThat(tracker.wasReturned("evt-2")).isFalse();
        assertThat(tracker.wasReturned(null)).isFalse();
    }

    @Test
    void returnIsCountedWithExchangeAndReplyCodeForAlerting() {
        tracker.onReturned(returned("evt-1", "crm.LEAD.CREATED"));

        assertThat(registry.get("crm.rabbitmq.publisher.returned").counter().count()).isEqualTo(1.0d);
        assertThat(registry.find("crm.rabbitmq.publisher.returned.by")
                .tag("exchange", "crm.events")
                .tag("replyCode", "312")
                .counter().count()).isEqualTo(1.0d);
    }

    @Test
    void messageWithoutIdDoesNotBreakTheCallback() {
        Message noId = MessageBuilder.withBody("{}".getBytes()).build();

        tracker.onReturned(new ReturnedMessage(noId, 312, "NO_ROUTE", "crm.events", "crm.LEAD.CREATED"));

        assertThat(tracker.wasReturned(null)).isFalse();
    }

    @Test
    void trackerIsBounded() {
        for (int i = 0; i < 10_050; i++) {
            tracker.onReturned(returned("evt-" + i, "crm.LEAD.CREATED"));
        }

        // Still functional and no unbounded growth beyond the cap.
        tracker.onReturned(returned("final", "crm.LEAD.CREATED"));
        assertThat(tracker.wasReturned("final")).isTrue();
    }

    private static ReturnedMessage returned(String messageId, String routingKey) {
        Message message = MessageBuilder.withBody("{\"tenantId\":\"x\"}".getBytes())
                .setMessageId(messageId)
                .build();
        return new ReturnedMessage(message, 312, "NO_ROUTE", "crm.events", routingKey);
    }
}
