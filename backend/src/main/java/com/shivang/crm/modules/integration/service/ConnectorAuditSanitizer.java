package com.shivang.crm.modules.integration.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component 
public class ConnectorAuditSanitizer {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "password",
        "apiKey",
        "api_key",
        "token",
        "accessToken",
        "refreshToken",
        "authorization",
        "secret",
        "webhookSecret",
        "credential"
    );

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
        return masked;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase();
        return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }
}
