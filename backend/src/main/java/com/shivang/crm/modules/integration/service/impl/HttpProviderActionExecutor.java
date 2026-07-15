package com.shivang.crm.modules.integration.service.impl;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.shivang.crm.modules.integration.dto.ConnectorExecutionContext;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionResult;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ProviderActionDefinition;
import com.shivang.crm.modules.integration.service.ConnectorAuditSanitizer;
import com.shivang.crm.modules.integration.service.ProviderActionExecutor;

@Service
public class HttpProviderActionExecutor implements ProviderActionExecutor {

    private final RestClient restClient;
    private final ConnectorAuditSanitizer sanitizer;

    public HttpProviderActionExecutor(RestClient restClient, ConnectorAuditSanitizer sanitizer) {
        this.restClient = restClient;
        this.sanitizer = sanitizer;
    }

    @Override
    public ConnectorExecutionResult execute(ConnectorExecutionContext context,
                                            ProviderActionDefinition actionDefinition,
                                            ConnectorInstance connectorInstance,
                                            Map<String, Object> credentials) {
        long startedAt = System.currentTimeMillis();
        Map<String, Object> resolvedRequestBody = new LinkedHashMap<>();
        Map<String, Object> resolvedHeaders = new LinkedHashMap<>();
        try {
            String endpointTemplate = actionDefinition.getEndpointTemplate();
            String resolvedEndpoint = resolveTemplate(endpointTemplate, context);
            String absoluteUrl = resolveAbsoluteUrl(connectorInstance, resolvedEndpoint);

            Map<String, Object> requestTemplate = actionDefinition.getRequestTemplate() == null
                ? Map.of()
                : actionDefinition.getRequestTemplate();
            for (Map.Entry<String, Object> entry : requestTemplate.entrySet()) {
                resolvedRequestBody.put(entry.getKey(), resolveTemplate(String.valueOf(entry.getValue()), context));
            }

            Map<String, Object> headersTemplate = actionDefinition.getHeadersTemplate();
            if (headersTemplate != null) {
                for (Map.Entry<String, Object> entry : headersTemplate.entrySet()) {
                    resolvedHeaders.put(entry.getKey(), resolveTemplate(String.valueOf(entry.getValue()), context));
                }
            }

            if (connectorInstance.getConfig() != null && connectorInstance.getConfig().containsKey("headers")) {
                Object headersConfig = connectorInstance.getConfig().get("headers");
                if (headersConfig instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        resolvedHeaders.put(String.valueOf(entry.getKey()), resolveTemplate(String.valueOf(entry.getValue()), context));
                    }
                }
            }

            resolvedHeaders.putIfAbsent("Content-Type", MediaType.APPLICATION_JSON_VALUE);

            RestClient.RequestBodySpec requestSpec = restClient.post()
                .uri(URI.create(absoluteUrl))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    for (Map.Entry<String, Object> entry : resolvedHeaders.entrySet()) {
                        headers.set(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                });

            Map<String, Object> responseBody = requestSpec.body(resolvedRequestBody).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (responseBody == null) {
                responseBody = new LinkedHashMap<>();
            }
            return new ConnectorExecutionResult(
                true,
                200,
                sanitizeMap(responseBody),
                sanitizeHeaders(resolvedHeaders),
                sanitizeMap(resolvedRequestBody),
                null,
                System.currentTimeMillis() - startedAt
            );
        } catch (Exception ex) {
            return new ConnectorExecutionResult(
                false,
                0,
                Map.of(),
                sanitizeHeaders(resolvedHeaders),
                sanitizeMap(resolvedRequestBody),
                sanitizer.sanitizeString(ex.getMessage()),
                System.currentTimeMillis() - startedAt
            );
        }
    }

    private String resolveTemplate(String template, ConnectorExecutionContext context) {
        if (template == null || template.isBlank()) {
            return template;
        }
        Map<String, Object> fullContext = context.toTemplateContext();
        fullContext.put("credential", context.getCredential());
        fullContext.put("entity", context.getEntity());
        fullContext.put("input", context.getInput());
        fullContext.put("user", context.getUser());
        fullContext.put("tenant", context.getTenant());
        fullContext.put("requestMetadata", context.getRequestMetadata());
        return new SimpleTemplateResolver().resolve(template, fullContext);
    }

    private String resolveAbsoluteUrl(ConnectorInstance connectorInstance, String endpointTemplate) {
        if (endpointTemplate == null || endpointTemplate.isBlank()) {
            throw new IllegalArgumentException("Endpoint template is required");
        }
        String baseUrl = connectorInstance.getBaseUrl();
        if (endpointTemplate.startsWith("http://") || endpointTemplate.startsWith("https://")) {
            return endpointTemplate;
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl + endpointTemplate;
        }
        return endpointTemplate;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeHeaders(Map<String, Object> headers) {
        return (Map<String, Object>) sanitizer.sanitize(headers);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMap(Map<String, Object> values) {
        return (Map<String, Object>) sanitizer.sanitize(values);
    }

    private static RestClient buildRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
