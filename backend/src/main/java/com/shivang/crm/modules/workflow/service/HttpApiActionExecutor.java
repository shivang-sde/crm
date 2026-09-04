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
import tools.jackson.databind.JsonNode;

@Component
public class HttpApiActionExecutor implements WorkflowActionExecutor {

    private final WorkflowHttpApiService workflowHttpApiService;
    private final WorkflowValueResolver valueResolver;
    private final WorkflowExecutionIdentityResolver executionIdentityResolver;
    private final WorkflowCredentialService credentialService;
    private final ObjectMapper objectMapper;

    public HttpApiActionExecutor(
        WorkflowHttpApiService workflowHttpApiService,
        WorkflowValueResolver valueResolver,
        WorkflowExecutionIdentityResolver executionIdentityResolver,
        WorkflowCredentialService credentialService,
        ObjectMapper objectMapper
    ) {
        this.workflowHttpApiService = workflowHttpApiService;
        this.valueResolver = valueResolver;
        this.executionIdentityResolver = executionIdentityResolver;
        this.credentialService = credentialService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String actionType() {
        return "HTTP_API";
    }

    @Override
    public WorkflowActionExecutionResult execute(WorkflowExecutionContext context, Map<String, Object> configuration) {
        if (configuration == null) throw failure("WORKFLOW_HTTP_API_INVALID_CONFIG", "HTTP_API configuration is required");

        // ── Phase 6: deterministic authentication mode (NONE / SAVED_CONNECTION / CREDENTIAL) ──
        String authMode = resolveAuthenticationMode(configuration);
        // Validate mutual exclusivity to avoid ambiguous wins
        Object rawConnectionId = configuration.get("connectionId");
        Object rawCredSource = configuration.get("credentialSource") != null ? configuration.get("credentialSource") : configuration.get("executeAs");
        if ("SAVED_CONNECTION".equals(authMode) && rawCredSource != null && !String.valueOf(rawCredSource).isBlank()
            && !"WORKFLOW_USER".equalsIgnoreCase(String.valueOf(rawCredSource).trim())) {
            // For SAVED_CONNECTION, credentialSource should not be set (except default WORKFLOW_USER which is implicit)
            // We allow default but warn: ignore credentialSource when using saved connection
        }
        if ("CREDENTIAL".equals(authMode) && rawConnectionId != null && !String.valueOf(rawConnectionId).isBlank()) {
            throw failure("WORKFLOW_HTTP_API_INVALID_CONFIG", "Credential mode must not include a saved connection; choose one authentication mode");
        }
        if ("NONE".equals(authMode) && rawConnectionId != null && !String.valueOf(rawConnectionId).isBlank()) {
            throw failure("WORKFLOW_HTTP_API_INVALID_CONFIG", "No-authentication mode must not include a saved connection");
        }
        if (!"CREDENTIAL".equals(authMode) && rawCredSource != null && !String.valueOf(rawCredSource).isBlank()
            && !"SAVED_CONNECTION".equals(authMode)) {
            // Only CREDENTIAL and SAVED_CONNECTION may have executeAs; NONE should not need it
        }

        Map<String, Object> credentialMap = Map.of();
        UUID executionUserId = null;
        String credentialSourceForAudit = null;

        if ("CREDENTIAL".equals(authMode)) {
            // ── Phase 2 & 5: resolve credential source → executionUserId → credential map ──
            String credSourceRaw = configuration.get("credentialSource") != null ? String.valueOf(configuration.get("credentialSource")) : String.valueOf(configuration.get("executeAs"));
            String credentialSource = credSourceRaw == null || credSourceRaw.isBlank() ? "WORKFLOW_USER" : credSourceRaw.trim().toUpperCase();
            credentialSourceForAudit = credentialSource;
            if (!java.util.Set.of("WORKFLOW_USER", "RECORD_OWNER", "SPECIFIC_USER", "TENANT").contains(credentialSource)) {
                throw failure("WORKFLOW_HTTP_API_INVALID_CONFIG", "Credential source must be WORKFLOW_USER, RECORD_OWNER, SPECIFIC_USER or TENANT");
            }
            if ("TENANT".equals(credentialSource)) {
                executionUserId = null;
                var tenantCred = credentialService.findGenericCredential(context.getIdentity().tenantId(), null, "TENANT");
                if (tenantCred.isEmpty()) {
                    throw failure("WORKFLOW_HTTP_API_CREDENTIAL_NOT_CONFIGURED", "The workspace does not have credentials configured for this API");
                }
                credentialMap = tenantCred.get();
            } else {
                // Reuse existing identity resolver for user-based sources
                Map<String, Object> identityConfig = new java.util.LinkedHashMap<>();
                identityConfig.put("executeAs", credentialSource);
                // For SPECIFIC_USER, need executeAsUserId / credentialSourceUserId
                Object userIdRaw = configuration.get("credentialSourceUserId");
                if (userIdRaw == null) userIdRaw = configuration.get("executeAsUserId");
                if (userIdRaw != null) identityConfig.put("executeAsUserId", userIdRaw);
                // Allow templated SPECIFIC_USER like {{entity.ownerId}} — resolve via valueResolver before identity lookup
                if (userIdRaw instanceof String s && s.startsWith("{{") && s.endsWith("}}")) {
                    String path = s.substring(2, s.length() - 2).trim();
                    var resolvedVal = valueResolver.resolve(context, path);
                    if (!resolvedVal.found() || resolvedVal.value() == null) {
                        throw failure("WORKFLOW_HTTP_API_CREDENTIAL_NOT_CONFIGURED", "Specific user could not be resolved for credential lookup");
                    }
                    identityConfig.put("executeAsUserId", String.valueOf(resolvedVal.value()));
                }
                try {
                    executionUserId = executionIdentityResolver.resolveExecutionUser(context, identityConfig);
                } catch (WorkflowRuntimeException ex) {
                    // Map to business-meaningful credential error
                    if ("EXECUTION_USER_NOT_FOUND".equals(ex.getErrorCode()) || "EXECUTION_USER_TENANT_MISMATCH".equals(ex.getErrorCode())) {
                        throw failure("WORKFLOW_HTTP_API_CREDENTIAL_NOT_CONFIGURED", friendlyCredentialMissingMessage(credentialSource, ex.getMessage()));
                    }
                    throw ex;
                }
                var userCred = credentialService.findGenericCredential(context.getIdentity().tenantId(), executionUserId, "USER");
                if (userCred.isEmpty()) {
                    throw failure("WORKFLOW_HTTP_API_CREDENTIAL_NOT_CONFIGURED", friendlyCredentialMissingMessage(credentialSource, null));
                }
                credentialMap = userCred.get();
            }
            // Make credential available for {{credential.*}} resolution — execution-only, never persisted
            context.setCredentialContext(credentialMap);
        } else if ("SAVED_CONNECTION".equals(authMode)) {
            // Keep existing per-connection user-aware path; resolve executionUserId for transport
            Map<String, Object> identityConfig = new java.util.LinkedHashMap<>(configuration);
            // Fallback: if executeAs not set, default to WORKFLOW_USER (resolver does)
            try {
                executionUserId = executionIdentityResolver.resolveExecutionUser(context, identityConfig);
            } catch (WorkflowRuntimeException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new WorkflowRuntimeException("EXECUTION_USER_RESOLUTION_FAILED", "Failed to resolve execution user: " + ex.getMessage());
            }
            // No credential map for templating in this mode; transport will fetch via connection
        } else { // NONE
            executionUserId = context.getIdentity().actorId(); // for audit correlation, not credential
            // Ensure no credential context leaks from previous node
            context.clearCredentialContext();
        }

        // Credential context must be cleared even if later steps fail
        boolean credentialContextActive = "CREDENTIAL".equals(authMode) && !credentialMap.isEmpty();
        Map<String, Object> resolved;
        OutboundHttpMethod method;
        String url;
        UUID connectionId;
        Map<String, String> headers;
        OutboundHttpRequest request;
        OutboundHttpResult result;
        try {
            try {
                resolved = resolveMap(configuration, context);
            } catch (WorkflowRuntimeException ex) {
                if (credentialContextActive) context.clearCredentialContext();
                throw ex;
            }
            method = method(resolved.get("method"));
            url = requiredText(resolved.get("url"), "WORKFLOW_HTTP_API_URL_REQUIRED", "HTTP_API URL is required");
            connectionId = null;
            if ("SAVED_CONNECTION".equals(authMode)) {
                connectionId = optionalUuid(resolved.get("connectionId"));
                if (connectionId == null) {
                    if (credentialContextActive) context.clearCredentialContext();
                    throw failure("WORKFLOW_HTTP_API_INVALID_CONFIG", "Saved connection is required for this authentication mode");
                }
            } else {
                connectionId = null;
            }
            headers = headers(resolved.get("headers"));
            applyConfiguredIdempotencyHeader(resolved.get("idempotency"), headers, context);

            if (executionUserId == null && !"TENANT".equals(credentialSourceForAudit) && !"CREDENTIAL".equals(authMode)) {
                try {
                    if (executionUserId == null) executionUserId = context.getIdentity().actorId();
                } catch (Exception ignore) {}
            }

            request = new OutboundHttpRequest(
                context.getIdentity().tenantId(),
                context.getIdentity().actorId(),
                context.getExecution().getId(),
                context.getWorkflowNodeExecutionId(),
                method,
                url,
                queryParams(resolved.get("queryParams")),
                headers,
                resolved.get("body"),
                connectionId,
                executionUserId
            );

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
        } catch (WorkflowRuntimeException ex) {
            if (credentialContextActive) context.clearCredentialContext();
            throw ex;
        } catch (RuntimeException ex) {
            if (credentialContextActive) context.clearCredentialContext();
            throw ex;
        }

        Map<String, Object> output = new LinkedHashMap<>();
        try {
            output.put("success", result.success());
            output.put("statusCode", result.statusCode());
            JsonNode redactedResponse = result.response();
            // Mask any credential values that may have been echoed in response (extra safety, transport already does)
            if (redactedResponse != null && !credentialMap.isEmpty()) {
                redactedResponse = redactJsonNode(redactedResponse, credentialMap);
            }
            output.put("response", redactedResponse == null ? null : objectMapper.convertValue(redactedResponse, Object.class));
            output.put("correlationId", result.correlationId() == null ? null : result.correlationId().toString());
            // Redacted request snapshot for debugging — must not contain raw secrets
            try {
                Map<String, Object> requestSnapshot = new LinkedHashMap<>();
                requestSnapshot.put("method", method.name());
                requestSnapshot.put("url", redactString(url, credentialMap));
                // query and headers may contain credential values; redact each value
                Object rawQuery = resolved.get("queryParams");
                Object rawHeaders = headers;
                Object rawBody = resolved.get("body");
                requestSnapshot.put("query", redactObject(rawQuery, credentialMap));
                requestSnapshot.put("headers", redactObject(rawHeaders, credentialMap));
                requestSnapshot.put("body", redactObject(rawBody, credentialMap));
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
        } finally {
            // Always clear execution-only credential context
            context.clearCredentialContext();
        }
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

    private String resolveAuthenticationMode(Map<String, Object> configuration) {
        Object raw = configuration.get("authenticationMode");
        if (raw == null) raw = configuration.get("authMode");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            String m = String.valueOf(raw).trim().toUpperCase();
            if (java.util.Set.of("NONE", "SAVED_CONNECTION", "CREDENTIAL").contains(m)) return m;
            throw failure("WORKFLOW_HTTP_API_INVALID_CONFIG", "Authentication mode must be NONE, SAVED_CONNECTION or CREDENTIAL");
        }
        Object connId = configuration.get("connectionId");
        if (connId != null && !String.valueOf(connId).isBlank()) return "SAVED_CONNECTION";
        Object credSrc = configuration.get("credentialSource");
        if (credSrc == null) credSrc = configuration.get("executeAs");
        if (credSrc != null && !String.valueOf(credSrc).isBlank()) {
            String s = String.valueOf(credSrc).trim().toUpperCase();
            if (java.util.Set.of("WORKFLOW_USER", "RECORD_OWNER", "SPECIFIC_USER", "TENANT").contains(s)) return "CREDENTIAL";
        }
        return "NONE";
    }

    private String friendlyCredentialMissingMessage(String source, String details) {
        String s = source == null ? "WORKFLOW_USER" : source.toUpperCase();
        return switch (s) {
            case "RECORD_OWNER" -> "The record owner does not have credentials configured for this API";
            case "SPECIFIC_USER" -> "The selected user does not have credentials configured for this API";
            case "TENANT" -> "The workspace does not have credentials configured for this API";
            case "WORKFLOW_USER" -> "The workflow user does not have credentials configured for this API";
            default -> details != null ? details : "Credentials not configured for this API";
        };
    }

    private String redactString(String input, Map<String, Object> credentials) {
        if (input == null || credentials == null || credentials.isEmpty()) return input;
        String out = input;
        for (Object v : credentials.values()) {
            if (v == null) continue;
            String secret = String.valueOf(v);
            if (secret.length() < 4) continue;
            if (out.contains(secret)) out = out.replace(secret, "***");
        }
        return out;
    }

    private Object redactObject(Object input, Map<String, Object> credentials) {
        if (input == null || credentials == null || credentials.isEmpty()) return input;
        try {
            String json = objectMapper.writeValueAsString(input);
            String redacted = redactString(json, credentials);
            if (redacted.equals(json)) return input;
            // Try to parse back to original shape; fallback to string
            try {
                return objectMapper.readValue(redacted, Object.class);
            } catch (Exception e) {
                return redacted;
            }
        } catch (Exception e) {
            return input;
        }
    }

    private JsonNode redactJsonNode(JsonNode node, Map<String, Object> credentials) {
        if (node == null || credentials == null || credentials.isEmpty()) return node;
        try {
            String json = objectMapper.writeValueAsString(node);
            String redacted = redactString(json, credentials);
            if (redacted.equals(json)) return node;
            return objectMapper.readTree(redacted);
        } catch (Exception e) {
            return node;
        }
    }

    private boolean isCredentialField(String key) {
        // Retained for raw top-level key protection; primary defense is execution-only credential context + redaction
        String normalized = key.toLowerCase();
        return normalized.contains("password") || normalized.contains("apikey") || normalized.contains("api_key")
            || normalized.contains("bearer") || normalized.contains("authorization") || normalized.contains("clientsecret")
            || normalized.contains("client_secret") || normalized.equals("username");
    }

    private WorkflowRuntimeException failure(String code, String message) { return new WorkflowRuntimeException(code, message); }
}