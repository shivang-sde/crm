package com.shivang.crm.modules.operations.service;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/**
 * Operator-facing, safe projection of a dead-lettered RabbitMQ message.
 *
 * <p>Contains no message body, no payload, no credential and no header value.
 * Every field is either an AMQP identifier, a RabbitMQ dead-letter fact, or one of
 * the declared scalar fields of a canonical CRM event.
 */
@Data
@Builder
public class DeadLetterView {

    /** Stable AMQP message id. The publisher sets it to the canonical event id. */
    private String messageId;

    /** Only present if the publisher set one. Never synthesised. */
    private String correlationId;

    /** Exchange the message currently sits behind, i.e. the dead-letter exchange. */
    private String exchange;

    /** Routing key the message currently sits behind. */
    private String routingKey;

    /** Queue the message currently sits behind. */
    private String queue;

    private String contentType;

    /** AMQP timestamp property, in epoch seconds. Null when the publisher set none. */
    private Long timestamp;

    private boolean redelivered;

    /** {@code x-crm-redrive-of} header, present only on a re-driven copy. */
    private String redriveOf;

    /** {@code x-death[0].count}. Not a broader retry counter. */
    private Integer deathCount;

    /** {@code x-death[0].reason}, e.g. "rejected". */
    private String deathReason;

    /** {@code x-death[0].exchange} - where the message was published before dying. */
    private String originalExchange;

    /** {@code x-death[0].routing-keys[0]} - the routing key to re-drive to. */
    private String originalRoutingKey;

    /** {@code x-death[0].queue} - the queue that dead-lettered it. */
    private String originalQueue;

    private String firstDeathExchange;
    private String firstDeathRoutingKey;
    private String firstDeathQueue;

    /** Declared scalar fields of the canonical event body. Never the body itself. */
    private String eventId;
    private String eventType;
    private String entityType;
    private String entityId;

    /**
     * Tenant ownership, taken only from the declared {@code tenantId} field of the
     * canonical event. Null when the body is not a canonical event, in which case
     * the message is visible at platform scope only.
     */
    private UUID tenantId;

    private String occurredAt;

    /** False once a successful re-drive has been recorded for this message id. */
    private boolean replayable;

    /**
     * replayable AND the resolved destination is inside the approved CRM topology.
     */
    private boolean redriveable;

    public static DeadLetterView unavailable(String reason) {
        return DeadLetterView.builder().replayable(false).redriveable(false).build();
    }
}
