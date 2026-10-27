package com.shivang.crm.modules.operations.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.integration.service.ConnectorAuditSanitizer;
import com.shivang.crm.modules.operations.config.OperatorToolsProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a raw dead-lettered AMQP message into a safe operator view.
 *
 * <p><b>What RabbitMQ actually provides</b> and is therefore used here:
 * <ul>
 *   <li>{@code x-death} - an array of tables, newest last, each with {@code count},
 *       {@code reason}, {@code exchange}, {@code queue}, {@code time} and
 *       {@code routing-keys} (a list).</li>
 *   <li>{@code x-first-death-exchange} / {@code x-first-death-queue} /
 *       {@code x-first-death-routing-key} - present because the publisher set no
 *       {@code expiration} and no {@code CC} header.</li>
 *   <li>The message properties: {@code message_id}, {@code correlation_id},
 *       {@code content_type}, {@code timestamp}.</li>
 * </ul>
 *
 * <p><b>What is not invented.</b> {@code correlation_id} is null in practice
 * because the publisher never sets it; it is reported as null rather than
 * synthesised. A retry attempt count beyond the single bounded retry cycle is not
 * tracked by RabbitMQ, so {@code deathCount} is the {@code x-death} count and
 * nothing more is claimed.
 *
 * <p><b>Tenant ownership</b> is taken only from the typed {@code tenantId} field
 * of a canonical CRM event body, whose schema is known and fixed
 * ({@code CanonicalCrmEvent}). A body that is not a canonical event, or that has
 * no tenant id, yields {@code null} and the message is then invisible to tenant
 * operators and visible only at platform scope. That is the fail-closed choice.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterMessageMapper {

    /** Header names RabbitMQ adds when it dead-letters a message. */
    static final String X_DEATH = "x-death";
    static final String X_FIRST_DEATH_EXCHANGE = "x-first-death-exchange";
    static final String X_FIRST_DEATH_ROUTING_KEY = "x-first-death-routing-key";
    static final String X_FIRST_DEATH_QUEUE = "x-first-death-queue";
    /** Added by this tooling on a re-driven copy, for lineage. */
    static final String X_CRM_REDRIVE_OF = "x-crm-redrive-of";

    /**
     * The only body fields ever lifted out of a dead letter. Everything else in the
     * payload - including the free-form metadata map - is dropped rather than
     * redacted, so there is no path by which a payload field reaches an API
     * response or an audit row.
     */
    private static final Set<String> SAFE_EVENT_FIELDS = Set.of(
            "eventId", "entityType", "eventType", "tenantId", "entityId", "occurredAt");

    private final OperatorToolsProperties properties;
    private final ConnectorAuditSanitizer sanitizer;

    public DeadLetterView toView(RabbitManagementApiClient.ManagementMessage message, boolean replayable) {
        Map<String, Object> headers = message.headers();

        DeathInfo death = readDeath(headers);
        EventIdentity event = readEventIdentity(message.payload());

        return DeadLetterView.builder()
                .messageId(message.messageId())
                .correlationId(message.correlationId())
                .exchange(message.exchange())
                .routingKey(message.routingKey())
                .contentType(message.contentType())
                .timestamp(message.timestamp())
                .redelivered(message.redelivered())
                .redriveOf(headerText(headers, X_CRM_REDRIVE_OF))
                .deathCount(death.count())
                .deathReason(death.reason())
                .originalExchange(death.exchange())
                .originalRoutingKey(death.routingKey())
                .originalQueue(death.queue())
                .firstDeathExchange(headerText(headers, X_FIRST_DEATH_EXCHANGE))
                .firstDeathRoutingKey(headerText(headers, X_FIRST_DEATH_ROUTING_KEY))
                .firstDeathQueue(headerText(headers, X_FIRST_DEATH_QUEUE))
                .eventId(event.eventId())
                .eventType(event.eventType())
                .entityType(event.entityType())
                .entityId(event.entityId())
                .tenantId(event.tenantId())
                .occurredAt(event.occurredAt())
                .replayable(replayable)
                .redriveable(replayable && isApprovedDestination(death))
                .build();
    }

    /**
     * Resolves the destination a re-drive must use: the original exchange and
     * routing key recorded by RabbitMQ at dead-letter time.
     *
     * <p>Falls back from {@code x-first-death-*} to {@code x-death[0]} and then
     * to the message's own exchange/routing key. No value is guessed.
     */
    public Destination resolveDestination(RabbitManagementApiClient.ManagementMessage message) {
        Map<String, Object> headers = message.headers();
        DeathInfo death = readDeath(headers);

        String exchange = firstNonBlank(headerText(headers, X_FIRST_DEATH_EXCHANGE), death.exchange());
        String routingKey = firstNonBlank(headerText(headers, X_FIRST_DEATH_ROUTING_KEY), death.routingKey());

        if (exchange == null) {
            exchange = message.exchange();
        }
        if (routingKey == null) {
            routingKey = message.routingKey();
        }
        return new Destination(exchange, routingKey);
    }

    /**
     * A re-drive destination must be inside the CRM event topology. This is the
     * check that stops a crafted dead letter from being used to publish to an
     * unrelated exchange.
     */
    public boolean isApprovedDestination(DeathInfo death) {
        String exchange = death.exchange() != null
                ? death.exchange()
                : com.shivang.crm.shared.event.CrmEventRabbitConfig.EXCHANGE_NAME;
        List<String> allowList = properties.getDlq().getAllowedExchanges();
        if (allowList == null || allowList.isEmpty()) {
            return com.shivang.crm.shared.event.CrmEventRabbitConfig.EXCHANGE_NAME.equals(exchange);
        }
        return allowList.contains(exchange);
    }

    public boolean isApprovedDestination(Destination destination) {
        if (destination == null || destination.exchange() == null) {
            return false;
        }
        List<String> allowList = properties.getDlq().getAllowedExchanges();
        if (allowList == null || allowList.isEmpty()) {
            return com.shivang.crm.shared.event.CrmEventRabbitConfig.EXCHANGE_NAME.equals(destination.exchange());
        }
        return allowList.contains(destination.exchange());
    }

    // ── x-death parsing ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private DeathInfo readDeath(Map<String, Object> headers) {
        Object raw = headers == null ? null : headers.get(X_DEATH);
        if (raw instanceof List<?> entries && !entries.isEmpty() && entries.get(0) instanceof Map<?, ?> first) {
            Map<String, Object> entry = (Map<String, Object>) first;
            return new DeathInfo(
                    toInt(entry.get("count")),
                    stringValue(entry.get("reason")),
                    stringValue(entry.get("exchange")),
                    stringValue(entry.get("queue")),
                    firstRoutingKey(entry.get("routing-keys")));
        }
        return new DeathInfo(null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static String firstRoutingKey(Object routingKeys) {
        if (routingKeys instanceof List<?> keys && !keys.isEmpty()) {
            Object first = keys.get(0);
            return first == null ? null : String.valueOf(first);
        }
        if (routingKeys instanceof String single) {
            return single;
        }
        return null;
    }

    private static Integer toInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static String headerText(Map<String, Object> headers, String key) {
        Object value = headers == null ? null : headers.get(key);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case byte[] bytes -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            case String text -> text;
            default -> String.valueOf(value);
        };
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // ── canonical event identity extraction ───────────────────────────────────

    /**
     * Reads only the declared scalar fields of a {@code CanonicalCrmEvent} body.
     *
     * <p>The {@code metadata} map and everything else are never touched. A body
     * that will not parse, or that is not an object, yields an identity with all
     * nulls, which makes the message platform-scope-only rather than
     * tenant-visible.
     */
    EventIdentity readEventIdentity(String payload) {
        if (payload == null || payload.isBlank()) {
            return EventIdentity.EMPTY;
        }
        try {
            var node = new tools.jackson.databind.ObjectMapper().readTree(payload);
            if (node == null || !node.isObject()) {
                return EventIdentity.EMPTY;
            }
            return new EventIdentity(
                    text(node, "eventId"),
                    text(node, "eventType"),
                    text(node, "entityType"),
                    text(node, "entityId"),
                    toUuid(text(node, "tenantId")),
                    text(node, "occurredAt"));
        } catch (Exception ex) {
            log.debug("Dead letter body is not a canonical CRM event; treating as platform scope only");
            return EventIdentity.EMPTY;
        }
    }

    private static String text(tools.jackson.databind.JsonNode node, String field) {
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static UUID toUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record Destination(String exchange, String routingKey) {
    }

    public record DeathInfo(Integer count, String reason, String exchange, String queue, String routingKey) {
        static final DeathInfo EMPTY = new DeathInfo(null, null, null, null, null);
    }

    public record EventIdentity(String eventId, String eventType, String entityType, String entityId,
            UUID tenantId, String occurredAt) {
        static final EventIdentity EMPTY = new EventIdentity(null, null, null, null, null, null);
    }

    /** Sanitised header map for the diagnostic endpoint, if one is ever added. */
    public Map<String, Object> safeHeaders(Map<String, Object> headers) {
        return sanitizer.projectAllowList(headers, SAFE_EVENT_FIELDS);
    }
}
