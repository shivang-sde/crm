package com.shivang.crm.modules.integration.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component 
public class ConnectorAuditSanitizer {

    /**
     * Substring deny list, matched case-insensitively against the whole key.
     *
     * <p>Extended by the operator recovery tooling (OPS-01/02) so that anything
     * added here is redacted everywhere a Map is sanitized, not only in the
     * connector audit path.
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "password",
        "passwd",
        "apiKey",
        "api_key",
        "apikey",
        "token",
        "accessToken",
        "access_token",
        "refreshToken",
        "refresh_token",
        "authorization",
        "bearer",
        "secret",
        "clientSecret",
        "client_secret",
        "privateKey",
        "private_key",
        "webhookSecret",
        "credential",
        "cookie",
        "jwt",
        "encryption",
        "signing"
    );

    /** Hard cap so a sanitized value can never become an unbounded log line. */
    private static final int MAX_SANITIZED_STRING_LENGTH = 512;

    public Object sanitize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, childValue) -> {
                String keyText = String.valueOf(key);
                if (isSensitiveKey(keyText)) {
                    sanitized.put(keyText, "********");
                } else {
                    sanitized.put(keyText, sanitize(childValue));
                }
            });
            return sanitized;
        }
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : list) {
                sanitized.add(sanitize(item));
            }
            return sanitized;
        }
        if (value instanceof String text) {
            return sanitizeString(text);
        }
        return value;
    }

    public String sanitizeString(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = value;
        for (String key : SENSITIVE_KEYS) {
            masked = masked.replaceAll("(?i)(\"|')?" + key + "(\"|')?\\s*[:=]\\s*([^,\"'}\s]+)", "$1$2=********");
        }
        if (masked.length() > MAX_SANITIZED_STRING_LENGTH) {
            return masked.substring(0, MAX_SANITIZED_STRING_LENGTH) + "...[truncated]";
        }
        return masked;
    }

    /**
     * Projects a map down to an explicit allow list of keys.
     *
     * <p>This is stronger than redaction: anything not named is dropped rather
     * than passed through with a chance of being missed. Used for operator views
     * over asynchronous message payloads, where the safe fields are known up
     * front and the body must never be echoed.
     */
    public Map<String, Object> projectAllowList(Map<String, ?> source, Set<String> allowedKeys) {
        Map<String, Object> projected = new LinkedHashMap<>();
        if (source == null) {
            return projected;
        }
        source.forEach((key, value) -> {
            String keyText = String.valueOf(key);
            if (allowedKeys.contains(keyText) || allowedKeys.contains(keyText.toLowerCase())) {
                projected.put(keyText, sanitize(value));
            }
        });
        return projected;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase();
        return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }
}
