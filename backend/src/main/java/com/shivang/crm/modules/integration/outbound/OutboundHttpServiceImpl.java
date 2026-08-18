package com.shivang.crm.modules.integration.outbound;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.integration.entity.ConnectorCredential;
import com.shivang.crm.modules.integration.service.ConnectorCredentialService;

@Service
public class OutboundHttpServiceImpl implements OutboundHttpService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final OutboundHttpConnectionRepository connectionRepository;
    private final ConnectorCredentialService credentialService;
    private final OutboundHttpSecurityPolicy securityPolicy;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Value("${app.outbound-http.max-request-bytes:1048576}")
    private int maxRequestBytes;

    @Value("${app.outbound-http.max-response-bytes:1048576}")
    private int maxResponseBytes;

    public OutboundHttpServiceImpl(
        OutboundHttpConnectionRepository connectionRepository,
        ConnectorCredentialService credentialService,
        OutboundHttpSecurityPolicy securityPolicy,
        ObjectMapper objectMapper,
        @Value("${app.outbound-http.connect-timeout-ms:5000}") int connectTimeoutMs,
        @Value("${app.outbound-http.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        this.connectionRepository = connectionRepository;
        this.credentialService = credentialService;
        this.securityPolicy = securityPolicy;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public OutboundHttpResult execute(OutboundHttpRequest request) {
        UUID correlationId = UUID.randomUUID();
        Instant started = Instant.now();
        try {
            validateRequest(request);
            OutboundHttpConnection connection = resolveConnection(request);
            Map<String, Object> credentials = resolveCredentials(request, connection);
            URI uri = buildUri(request);
            byte[] body = request.body() == null ? new byte[0] : objectMapper.writeValueAsBytes(request.body());
            if (body.length > maxRequestBytes) {
                throw new IllegalArgumentException("Outbound request body exceeds configured limit");
            }

            HttpHeaders headers = new HttpHeaders();
            if (request.headers() != null) {
                request.headers().forEach((name, value) -> {
                    if (isBlockedHeader(name)) throw new IllegalArgumentException("Header is controlled by the transport or connection");
                    headers.set(name, value);
                });
            }
            applyAuthentication(headers, connection, credentials);
            headers.set("X-Correlation-ID", correlationId.toString());
            if (body.length > 0) headers.set("Content-Type", "application/json");

            HttpMethod httpMethod = HttpMethod.valueOf(request.method().name());
            ResponseData response = restClient.method(httpMethod)
                .uri(uri)
                .headers(target -> target.putAll(headers))
                .body(body.length == 0 ? new byte[0] : body)
                .exchange((ignoredRequest, clientResponse) -> new ResponseData(
                    clientResponse.getStatusCode().value(),
                    readBounded(clientResponse.getBody())
                ));

            JsonNode responseBody = parseResponse(response.body());
            return new OutboundHttpResult(
                response.statusCode() >= 200 && response.statusCode() < 300,
                response.statusCode(),
                responseBody,
                Duration.between(started, Instant.now()).toMillis(),
                correlationId,
                null,
                null
            );
        } catch (Exception ex) {
            return new OutboundHttpResult(
                false,
                0,
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
                Duration.between(started, Instant.now()).toMillis(),
                correlationId,
                "OUTBOUND_HTTP_FAILED",
                ex.getMessage() == null ? "Outbound HTTP request failed" : ex.getMessage()
            );
        }
    }

    private void validateRequest(OutboundHttpRequest request) {
        if (request == null || request.tenantId() == null || request.actorId() == null
            || request.method() == null || request.url() == null || request.url().isBlank()) {
            throw new IllegalArgumentException("Outbound HTTP request is incomplete");
        }
    }

    private URI buildUri(OutboundHttpRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(securityPolicy.validate(request.url()));
        if (request.queryParams() != null) {
            request.queryParams().forEach((key, values) -> values.forEach(value -> builder.queryParam(key, value)));
        }
        return builder.build().encode().toUri();
    }

    private OutboundHttpConnection resolveConnection(OutboundHttpRequest request) {
        if (request.connectionId() == null) return null;
        return connectionRepository
            .findByIdAndTenantIdAndActiveTrueAndDeletedFalse(request.connectionId(), request.tenantId())
            .orElseThrow(() -> new IllegalArgumentException("Outbound HTTP connection not found"));
    }

    private Map<String, Object> resolveCredentials(OutboundHttpRequest request, OutboundHttpConnection connection) {
        if (connection == null) return Map.of();
        String authType = connection.getAuthType() == null ? "NONE" : connection.getAuthType().toUpperCase();
        if ("NONE".equals(authType)) {
            if (connection.getCredentialId() != null) throw new IllegalArgumentException("NONE authentication cannot reference a credential");
            return Map.of();
        }
        if ("OAUTH2".equals(authType) || "CUSTOM".equals(authType)) throw new IllegalArgumentException("Outbound authentication type is not supported");
        if (connection.getCredentialId() == null) throw new IllegalArgumentException("Outbound authentication requires a credential");
        ConnectorCredential credential = credentialService.findById(request.tenantId(), connection.getCredentialId())
            .filter(item -> Boolean.TRUE.equals(item.getIsActive()) && !Boolean.TRUE.equals(item.getDeleted()))
            .orElseThrow(() -> new IllegalArgumentException("Outbound HTTP credential not found"));
        try {
            return objectMapper.readValue(credentialService.decryptValue(credential), MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Outbound HTTP credential could not be resolved");
        }
    }

    private void applyAuthentication(HttpHeaders headers, OutboundHttpConnection connection, Map<String, Object> credentials) {
        if (connection == null) return;
        String authType = connection.getAuthType() == null ? "NONE" : connection.getAuthType().toUpperCase();
        switch (authType) {
            case "NONE" -> { }
            case "API_KEY" -> headers.set("X-API-Key", requiredCredential(credentials, "apiKey"));
            case "BEARER" -> headers.setBearerAuth(requiredCredential(credentials, "token"));
            case "BASIC_AUTH" -> headers.setBasicAuth(requiredCredential(credentials, "username"), requiredCredential(credentials, "password"));
            case "CUSTOM" -> throw new IllegalArgumentException("CUSTOM authentication requires a future explicit policy");
            case "OAUTH2" -> throw new IllegalArgumentException("OAUTH2 authentication is not implemented yet");
            default -> throw new IllegalArgumentException("Unsupported outbound HTTP authentication type");
        }
    }

    private String requiredCredential(Map<String, Object> credentials, String key) {
        Object value = credentials.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException("Outbound credential is invalid");
        return String.valueOf(value);
    }

    private boolean isBlockedHeader(String name) {
        String normalized = name.toLowerCase();
        return Set.of("authorization", "cookie", "proxy-authorization", "host", "content-length", "transfer-encoding",
            "connection", "proxy-connection", "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto")
            .contains(normalized);
    }

    private byte[] readBounded(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while (input != null && (read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maxResponseBytes) throw new IllegalArgumentException("Outbound response exceeds configured limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private JsonNode parseResponse(byte[] body) {
        if (body == null || body.length == 0) return objectMapper.nullNode();
        try { return objectMapper.readTree(body); }
        catch (Exception ignored) { return objectMapper.getNodeFactory().textNode(new String(body, java.nio.charset.StandardCharsets.UTF_8)); }
    }

    private record ResponseData(int statusCode, byte[] body) { }
} 