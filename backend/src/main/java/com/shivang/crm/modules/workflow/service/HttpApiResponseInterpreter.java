package com.shivang.crm.modules.workflow.service;

import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Provider-agnostic, safe interpretation of application/business outcome
 * from an HTTP response body.
 *
 * <p>IMPORTANT: Do NOT treat arbitrary strings containing "error" as failure.
 * Only explicit machine-readable signals are considered.
 *
 * <p>Signals recognized (case-insensitive field names, top-level only):
 * <ul>
 *   <li>boolean: success=false / ok=false  => FAILURE; success=true / ok=true => SUCCESS</li>
 *   <li>status: "error"/"failed"/"failure" => FAILURE; "success"/"completed"/"ok"/"succeeded" => SUCCESS</li>
 *   <li>result: same as status</li>
 *   <li>code: structured enum-like failure codes (ends with _failed/_error etc. or contains fail/error)</li>
 *   <li>error / errors: non-empty structured error field => FAILURE</li>
 * </ul>
 *
 * <p>Precedence: FAILURE signals checked before SUCCESS signals. If neither, UNKNOWN.
 */
public final class HttpApiResponseInterpreter {

    private static final Set<String> FAILURE_STATUS_VALUES = Set.of("error", "failed", "failure");
    private static final Set<String> SUCCESS_STATUS_VALUES = Set.of("success", "completed", "ok", "succeeded", "successful");

    private HttpApiResponseInterpreter() {}

    public record Interpretation(HttpApiApplicationOutcome outcome, String reason, String userMessage) {}

    public static Interpretation interpret(JsonNode response) {
        if (response == null || response.isNull() || response.isMissingNode()) {
            return new Interpretation(HttpApiApplicationOutcome.UNKNOWN, "No response body", null);
        }
        // Plain text or array or non-object => cannot determine application outcome
        if (!response.isObject()) {
            return new Interpretation(HttpApiApplicationOutcome.UNKNOWN, "Non-object response", null);
        }

        // Extract user message for any outcome (best-effort human readable)
        String userMessage = extractUserMessage(response);

        // 1) Check explicit FAILURE signals first
        String failureReason = findFailureReason(response);
        if (failureReason != null) {
            return new Interpretation(HttpApiApplicationOutcome.FAILURE, failureReason, userMessage);
        }

        // 2) Check explicit SUCCESS signals
        String successReason = findSuccessReason(response);
        if (successReason != null) {
            return new Interpretation(HttpApiApplicationOutcome.SUCCESS, successReason, userMessage);
        }

        // 3) Check error object presence as FAILURE (structured error)
        if (hasStructuredError(response)) {
            return new Interpretation(HttpApiApplicationOutcome.FAILURE, "Response contains structured error field", userMessage);
        }

        return new Interpretation(HttpApiApplicationOutcome.UNKNOWN, "No explicit application outcome signal", userMessage);
    }

    // --- failure detection ---

    private static String findFailureReason(JsonNode node) {
        // boolean success:false / ok:false
        JsonNode successNode = getFieldIgnoreCase(node, "success");
        if (successNode != null && successNode.isBoolean() && !successNode.asBoolean()) {
            return "success field was false";
        }
        JsonNode okNode = getFieldIgnoreCase(node, "ok");
        if (okNode != null && okNode.isBoolean() && !okNode.asBoolean()) {
            return "ok field was false";
        }
        // status field
        JsonNode statusNode = getFieldIgnoreCase(node, "status");
        if (statusNode != null && statusNode.isString()) {
            String v = statusNode.asString().trim().toLowerCase(Locale.ROOT);
            if (FAILURE_STATUS_VALUES.contains(v)) {
                return "status field indicates failure: " + statusNode.asString();
            }
        }
        JsonNode resultNode = getFieldIgnoreCase(node, "result");
        if (resultNode != null && resultNode.isString()) {
            String v = resultNode.asString().trim().toLowerCase(Locale.ROOT);
            if (FAILURE_STATUS_VALUES.contains(v)) {
                return "result field indicates failure: " + resultNode.asString();
            }
        }
        JsonNode codeNode = getFieldIgnoreCase(node, "code");
        if (codeNode != null && codeNode.isString()) {
            String raw = codeNode.asString().trim();
            String lower = raw.toLowerCase(Locale.ROOT);
            if (isFailureCode(lower)) {
                return "code field indicates failure: " + raw;
            }
        }
        // Note: structured error is checked separately after success signals to keep precedence documented,
        // but we also check it as failure before success - moved to final check after success to preserve order
        // Actually we want failure error presence to be considered failure before success, so check here too
        if (hasStructuredError(node)) {
            return "Response contains structured error field";
        }
        return null;
    }

    private static String findSuccessReason(JsonNode node) {
        JsonNode successNode = getFieldIgnoreCase(node, "success");
        if (successNode != null && successNode.isBoolean() && successNode.asBoolean()) {
            return "success field was true";
        }
        JsonNode okNode = getFieldIgnoreCase(node, "ok");
        if (okNode != null && okNode.isBoolean() && okNode.asBoolean()) {
            return "ok field was true";
        }
        JsonNode statusNode = getFieldIgnoreCase(node, "status");
        if (statusNode != null && statusNode.isString()) {
            String v = statusNode.asString().trim().toLowerCase(Locale.ROOT);
            if (SUCCESS_STATUS_VALUES.contains(v)) {
                return "status field indicates success: " + statusNode.asString();
            }
        }
        JsonNode resultNode = getFieldIgnoreCase(node, "result");
        if (resultNode != null && resultNode.isString()) {
            String v = resultNode.asString().trim().toLowerCase(Locale.ROOT);
            if (SUCCESS_STATUS_VALUES.contains(v)) {
                return "result field indicates success: " + resultNode.asString();
            }
        }
        JsonNode codeNode = getFieldIgnoreCase(node, "code");
        if (codeNode != null && codeNode.isString()) {
            String raw = codeNode.asString().trim();
            String lower = raw.toLowerCase(Locale.ROOT);
            if (isSuccessCode(lower)) {
                return "code field indicates success: " + raw;
            }
        }
        return null;
    }

    private static boolean isFailureCode(String lower) {
        if (lower.isEmpty()) return false;
        // Exact failure tokens
        if (Set.of("error", "failed", "failure", "unauthorized", "forbidden", "auth_failed", "authentication_failed").contains(lower)) {
            return true;
        }
        // Suffix patterns for enum-like codes: *_failed, *_error, *_failure, *_denied, *_invalid
        if (lower.endsWith("_failed") || lower.endsWith("_error") || lower.endsWith("_failure") || lower.endsWith("_denied") || lower.endsWith("_invalid")) {
            return true;
        }
        // Contains patterns that are unambiguous for code field (not arbitrary message)
        // Code field is structured, so searching for fail/error substrings is safe here
        if (lower.contains("fail") || lower.contains("error")) {
            return true;
        }
        if (lower.contains("unauth") || lower.contains("forbidden") || lower.contains("denied")) {
            return true;
        }
        return false;
    }

    private static boolean isSuccessCode(String lower) {
        if (lower.isEmpty()) return false;
        return Set.of("success", "ok", "completed", "succeeded", "successful").contains(lower);
    }

    private static boolean hasStructuredError(JsonNode node) {
        // Check "error" field: present and non-empty
        JsonNode errorNode = getFieldIgnoreCase(node, "error");
        if (errorNode != null && !errorNode.isNull() && !errorNode.isMissingNode()) {
            if (errorNode.isString()) {
                String t = errorNode.asString().trim();
                if (!t.isEmpty()) return true;
            } else if (errorNode.isObject()) {
                if (errorNode.size() > 0) return true;
                // empty object not considered error
            } else if (errorNode.isArray()) {
                if (errorNode.size() > 0) return true;
            } else if (errorNode.isBoolean()) {
                if (errorNode.asBoolean()) return true;
            } else if (errorNode.isNumber()) {
                // non-zero number as error indicator
                if (errorNode.asDouble() != 0) return true;
            }
        }
        JsonNode errorsNode = getFieldIgnoreCase(node, "errors");
        if (errorsNode != null && !errorsNode.isNull() && !errorsNode.isMissingNode()) {
            if (errorsNode.isArray()) {
                if (errorsNode.size() > 0) return true;
            } else if (errorsNode.isObject()) {
                if (errorsNode.size() > 0) return true;
            } else if (errorsNode.isString()) {
                if (!errorsNode.asString().trim().isEmpty()) return true;
            }
        }
        return false;
    }

    private static String extractUserMessage(JsonNode node) {
        // Priority order for human-readable message
        String[] candidates = {"response", "message", "reason", "error", "errorMessage", "error_message", "detail", "description", "msg", "data"};
        for (String key : candidates) {
            JsonNode n = getFieldIgnoreCase(node, key);
            if (n != null && n.isString()) {
                String t = n.asString().trim();
                if (!t.isEmpty()) return t;
            }
            // If error is object with message inside, try to extract
            if (n != null && n.isObject()) {
                JsonNode innerMsg = getFieldIgnoreCase(n, "message");
                if (innerMsg != null && innerMsg.isString()) {
                    String t = innerMsg.asString().trim();
                    if (!t.isEmpty()) return t;
                }
                JsonNode innerReason = getFieldIgnoreCase(n, "reason");
                if (innerReason != null && innerReason.isString()) {
                    String t = innerReason.asString().trim();
                    if (!t.isEmpty()) return t;
                }
            }
        }
        return null;
    }

    private static JsonNode getFieldIgnoreCase(JsonNode node, String fieldName) {
        if (node == null || !node.isObject()) return null;
        String lower = fieldName.toLowerCase(Locale.ROOT);
        for (String key : node.propertyNames()) {
            if (key.toLowerCase(Locale.ROOT).equals(lower)) {
                return node.get(key);
            }
        }
        return null;
    }
}
