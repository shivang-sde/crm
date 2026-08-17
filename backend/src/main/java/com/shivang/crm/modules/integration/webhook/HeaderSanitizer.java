package com.shivang.crm.modules.integration.webhook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class HeaderSanitizer {

    private static final List<String> SENSITIVE = List.of(
        "authorization", "proxy-authorization", "cookie", "set-cookie",
        "x-api-key", "api-key", "token", "secret", "signature",
        "x-signature", "x-sellspark-signature", "x-hub-signature-256"
    );

    public Map<String, Object> sanitize(Map<String, List<String>> headers) {
        Map<String, Object> out = new HashMap<>();
        if (headers == null) return out;
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            String key = e.getKey();
            if (key == null) continue;
            String lower = key.toLowerCase();
            if (SENSITIVE.contains(lower)) {
                out.put(lower, "***");
            } else {
                out.put(lower, e.getValue());
            }
        }
        return out;
    }
}
