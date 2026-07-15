package com.shivang.crm.modules.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ConnectorAuditSanitizerTest {

    @Test
    void masksSensitiveFieldsRecursively() {
        ConnectorAuditSanitizer sanitizer = new ConnectorAuditSanitizer();

        Map<String, Object> payload = Map.of(
            "userId", "agent1",
            "password", "secret-pass",
            "nested", Map.of(
                "accessToken", "abc123",
                "number", "9876543210"
            )
        );

        Object sanitized = sanitizer.sanitize(payload);

        assertThat(sanitized).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) sanitized;
        assertThat(map.get("password")).isEqualTo("********");
        assertThat(map.get("nested")).isInstanceOf(Map.class);
        Map<?, ?> nested = (Map<?, ?>) map.get("nested");
        assertThat(nested.get("accessToken")).isEqualTo("********");
        assertThat(nested.get("number")).isEqualTo("9876543210");
    }
}
