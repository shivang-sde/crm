package com.shivang.crm.modules.operations.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.operations.config.OperatorToolsProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Minimal client for the single RabbitMQ management HTTP API endpoint the DLQ
 * inspector needs.
 *
 * <p>Why the management API and not a consumer: AMQP has no "browse a queue"
 * primitive. A {@code @RabbitListener} on the DLQ would consume messages, and
 * consuming-then-republishing to "inspect" them is exactly the destructive
 * workaround this subsystem must not do. The management API's queue
 * {@code get} endpoint is the documented non-destructive path: with
 * {@code ackmode=ack_requeue_true} the fetched messages are requeued.
 *
 * <p><b>Exactly one endpoint is ever called</b> - the queue {@code get} - under
 * the fixed configured base URL. There is no delete, purge, declare or policy
 * endpoint here, so even a bug in this class cannot remove messages or mutate
 * broker state. {@link #fetchAndRemove} exists only to make the destructive
 * acknowledgement mode an explicit, greppable choice; nothing calls it.
 *
 * <p>The base URL, vhost and credentials come from configuration only, never from
 * a request, so this cannot be turned into an SSRF or credential-leak primitive.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitManagementApiClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final OperatorToolsProperties properties;
    private final ObjectMapper objectMapper;

    /** One message as returned by {@code POST /api/queues/{vhost}/{name}/get}. */
    public record ManagementMessage(
            String payload,
            String payloadEncoding,
            boolean redelivered,
            String exchange,
            String routingKey,
            Map<String, Object> messageProperties) {

        public String messageId() {
            return string("message_id");
        }

        public String correlationId() {
            return string("correlation_id");
        }

        public String contentType() {
            return string("content_type");
        }

        public Long timestamp() {
            Object value = messageProperties == null ? null : messageProperties.get("timestamp");
            return value instanceof Number n ? n.longValue() : null;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> headers() {
            if (messageProperties == null) {
                return Map.of();
            }
            Object headers = messageProperties.get("headers");
            return headers instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        }

        private String string(String key) {
            Object value = messageProperties == null ? null : messageProperties.get(key);
            return value == null ? null : String.valueOf(value);
        }
    }

    /** Raised when the management API is not configured, not reachable, or refuses the credentials. */
    public static class ManagementApiUnavailableException extends RuntimeException {
        public ManagementApiUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        public ManagementApiUnavailableException(String message) {
            super(message);
        }
    }

    public boolean isAvailable() {
        return properties.isEnabled()
                && properties.getDlq().getManagementUrl() != null
                && !properties.getDlq().getManagementUrl().isBlank();
    }

    /**
     * Fetches up to {@code count} messages from {@code queue} using the
     * non-destructive requeue acknowledgement mode. Nothing is removed.
     */
    public List<ManagementMessage> peek(String queue, int count) {
        return get(queue, count, "ack_requeue_true");
    }

    /**
     * Fetches using {@code ack_requeue_false}, which marks messages for deletion.
     *
     * <p>Intentionally unused, and package-private rather than public so it cannot
     * become an accidental destructive call. The re-drive republishes a copy and
     * leaves the original in the dead letter, so no destructive acknowledgement
     * mode is ever required. Kept as an explicit, greppable record of the decision.
     */
    @SuppressWarnings("unused")
    List<ManagementMessage> fetchAndRemove(String queue, int count) {
        return get(queue, count, "ack_requeue_false");
    }

    private List<ManagementMessage> get(String queue, int count, String ackMode) {
        if (!isAvailable()) {
            throw new ManagementApiUnavailableException(
                    "RabbitMQ DLQ inspection is unavailable: app.operator-tools.enabled is false or "
                            + "app.operator-tools.dlq.management-url is not configured. Messages cannot be "
                            + "inspected without the RabbitMQ management API, and this service will not fall "
                            + "back to consuming the queue.");
        }

        String base = trimTrailingSlash(properties.getDlq().getManagementUrl());
        String vhost = properties.getDlq().getVhost() == null ? "/" : properties.getDlq().getVhost();
        URI uri = URI.create(base + "/api/queues/" + encodePathSegment(vhost)
                + "/" + encodePathSegment(queue) + "/get");

        String body = "{\"count\":%d,\"ackmode\":\"%s\",\"encoding\":\"auto\",\"truncate\":%d}"
                .formatted(Math.max(1, count), ackMode, 64 * 1024);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(Math.max(1000L, properties.getDlq().getReadTimeoutMs())))
                .header("content-type", "application/json")
                .header("authorization", basicAuthHeader())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500L, properties.getDlq().getConnectTimeoutMs())))
                .build()) {

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 404) {
                // Queue or vhost absent. Treated as "nothing to see" rather than an
                // error so the operator UI does not break on a fresh install.
                log.warn("RabbitMQ management API returned 404 for queue {}", queue);
                return List.of();
            }
            if (status == 401 || status == 403) {
                throw new ManagementApiUnavailableException(
                        "RabbitMQ management API rejected the operator tools credentials (HTTP " + status
                                + "). Check app.operator-tools.dlq.username / .password.");
            }
            if (status < 200 || status >= 300) {
                throw new ManagementApiUnavailableException(
                        "RabbitMQ management API returned HTTP " + status + " for queue " + queue);
            }
            return parseMessages(response.body());
        } catch (ManagementApiUnavailableException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ManagementApiUnavailableException("RabbitMQ management API call was interrupted", ex);
        } catch (IOException | RuntimeException ex) {
            // The response body is never included: it would carry message payloads.
            // Only the host is named.
            throw new ManagementApiUnavailableException(
                    "RabbitMQ management API at " + uri.getHost() + " is not reachable", ex);
        }
    }

    private List<ManagementMessage> parseMessages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(json);
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<ManagementMessage> messages = new ArrayList<>();
        for (JsonNode element : root) {
            messages.add(new ManagementMessage(
                    text(element, "payload"),
                    text(element, "payload_encoding"),
                    element.path("redelivered").asBoolean(false),
                    text(element, "exchange"),
                    text(element, "routing_key"),
                    readMap(element.path("properties"))));
        }
        return messages;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Map<String, Object> readMap(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    private String basicAuthHeader() {
        String user = properties.getDlq().getUsername();
        String password = properties.getDlq().getPassword();
        if (user == null || user.isBlank()) {
            return "";
        }
        String raw = user + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodePathSegment(String segment) {
        // URLEncoder targets form encoding, where a space becomes '+'. In a path
        // segment it must be %20, so that substitution has to be made explicit.
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trimTrailingSlash(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
