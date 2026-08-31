package com.shivang.crm.modules.workflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.integration.outbound.OutboundHttpMethod;
import com.shivang.crm.modules.integration.outbound.OutboundHttpRequest;
import com.shivang.crm.modules.integration.outbound.OutboundHttpResult;

import tools.jackson.databind.ObjectMapper;

@Component
public class HttpApiActionExecutor implements WorkflowActionExecutor {

    private final WorkflowHttpApiService workflowHttpApiService;
    private final WorkflowValueResolver valueResolver;
    private final ObjectMapper objectMapper;

    public HttpApiActionExecutor(
        WorkflowHttpApiService workflowHttpApiService,
        WorkflowValueResolver valueResolver,
        ObjectMapper objectMapper
    ) {
        this.workflowHttpApiService = workflowHttpApiService;
        this.valueResolver = valueResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public String actionType() {
        return "HTTP_API";
    }

    @Override
    public WorkflowActionExecutionResult execute(WorkflowExecutionContext context, Map<String, Object> configuration) {
        if (configuration == null) throw failure("WORKFLOW_HTTP_API_INVALID_CONFIG", "HTTP_API configuration is required");

        Map<String, Object> resolved = resolveMap(configuration, context);
        OutboundHttpMethod method = method(resolved.get("method"));
        String url = requiredText(resolved.get("url"), "WORKFLOW_HTTP_API_URL_REQUIRED", "HTTP_API URL is required");
        UUID connectionId = optionalUuid(resolved.get("connectionId"));
        Map<String, String> headers = headers(resolved.get("headers"));
        applyConfiguredIdempotencyHeader(resolved.get("idempotency"), headers, context);

        OutboundHttpRequest request = new OutboundHttpRequest(
            context.getIdentity().tenantId(),
            context.getIdentity().actorId(),
            context.getExecution().getId(),
            context.getWorkflowNodeExecutionId(),
            method,
            url,
            queryParams(resolved.get("queryParams")),
            headers,
            resolved.get("body"),
            connectionId
        );

        OutboundHttpResult result;
        try {
            result = workflowHttpApiService.execute(
                context.getIdentity().tenantId(),
                context.getIdentity().actorId(),
                context.getExecution().getId(),
                context.getWorkflowNodeExecutionId(),
                request
            );
        } catch (WorkflowRuntimeException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw failure("WORKFLOW_HTTP_API_EXECUTION_FAILED", "HTTP_API execution failed");
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", result.success());
        output.put("statusCode", result.statusCode());
        // Convert the parsed JSON tree to plain Java types so downstream
        // CONDITION/BRANCH nodes can traverse it via nodeOutputs.<key>.response...
        output.put("response", result.response() == null ? null : objectMapper.convertValue(result.response(), Object.class));
        output.put("correlationId", result.correlationId() == null ? null : result.correlationId().toString());
        // Minimal safe request snapshot for debugging (method/url/query/headers/body already redacted via resolveMap)
        try {
            Map<String, Object> requestSnapshot = new LinkedHashMap<>();
            requestSnapshot.put("method", method.name());
            requestSnapshot.put("url", url);
            requestSnapshot.put("query", resolved.get("queryParams") == null ? Map.of() : resolved.get("queryParams"));
            requestSnapshot.put("headers", headers);
            requestSnapshot.put("body", resolved.get("body"));
            output.put("request", requestSnapshot);
        } catch (Exception ignore) {
            // request snapshot is best-effort debugging aid; never fail execution
        }
        if (!result.success()) {
            output.put("errorCode", result.errorCode());
            output.put("errorMessage", result.errorMessage());
            throw failure(result.errorCode() == null ? "WORKFLOW_HTTP_API_EXECUTION_FAILED" : result.errorCode(),
                result.errorMessage() == null ? "HTTP_API execution failed" : result.errorMessage());
        }
        return WorkflowActionExecutionResult.completed(output);
    }

    private Map<String, Object> resolveMap(Map<String, Object> input, WorkflowExecutionContext context) {
        Map<String, Object> output = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (isCredentialField(key)) throw failure("WORKFLOW_HTTP_API_INVALID_CONFIG", "Raw credentials are not allowed");
            output.put(key, resolveValue(value, context));
        });
        return output;
    }

    private Object resolveValue(Object value, WorkflowExecutionContext context) {
        if (value instanceof String text && text.startsWith("{{") && text.endsWith("}}")) {
            WorkflowResolvedValue resolved = valueResolver.resolve(context, text.substring(2, text.length() - 2).trim());
            if (!resolved.found()) throw failure("WORKFLOW_HTTP_API_VALUE_RESOLUTION_FAILED", "HTTP_API value could not be resolved");
            return resolved.value();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, item) -> nested.put(String.valueOf(key), resolveValue(item, context)));
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> nested = new ArrayList<>();
            list.forEach(item -> nested.add(resolveValue(item, context)));
            return nested;
        }
        return value;
    }

    private OutboundHttpMethod method(Object value) {
        try { return OutboundHttpMethod.valueOf(String.valueOf(value).trim().toUpperCase()); }
        catch (Exception ex) { throw failure("WORKFLOW_HTTP_API_INVALID_METHOD", "Unsupported HTTP method"); }
    }

    private String requiredText(Object value, String code, String message) {
        if (value == null || String.valueOf(value).isBlank()) throw failure(code, message);
        return String.valueOf(value);
    }

    private UUID optionalUuid(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return UUID.fromString(String.valueOf(value)); }
        catch (Exception ex) { throw failure("WORKFLOW_HTTP_API_INVALID_CONNECTION", "connectionId must be a valid UUID"); }
    }

    private Map<String, List<String>> queryParams(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw failure("WORKFLOW_HTTP_API_INVALID_CONFIG", "queryParams must be an object");
        Map<String, List<String>> params = new LinkedHashMap<>();
        map.forEach((key, item) -> params.put(String.valueOf(key), item instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : List.of(String.valueOf(item))));
        return params;
    }

    private Map<String, String> headers(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw failure("WORKFLOW_HTTP_API_INVALID_CONFIG", "headers must be an object");
        Map<String, String> headers = new LinkedHashMap<>();
        map.forEach((key, item) -> headers.put(String.valueOf(key), String.valueOf(item)));
        return headers;
    }

    private void applyConfiguredIdempotencyHeader(Object value, Map<String, String> headers, WorkflowExecutionContext context) {
        if (!(value instanceof Map<?, ?> config) || !Boolean.parseBoolean(String.valueOf(config.get("enabled")))) {
            return;
        }
        String headerName = config.get("headerName") == null ? "Idempotency-Key" : String.valueOf(config.get("headerName"));
        headers.put(headerName, context.getExecution().getId() + ":" + context.getWorkflowNodeExecutionId());
    }

    private boolean isCredentialField(String key) {
        String normalized = key.toLowerCase();
        return normalized.contains("password") || normalized.contains("apikey") || normalized.contains("api_key")
            || normalized.contains("bearer") || normalized.contains("authorization") || normalized.contains("clientsecret")
            || normalized.contains("client_secret") || normalized.equals("username");
    }

    private WorkflowRuntimeException failure(String code, String message) { return new WorkflowRuntimeException(code, message); }
}