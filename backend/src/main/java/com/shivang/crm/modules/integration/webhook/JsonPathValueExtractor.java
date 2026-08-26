package com.shivang.crm.modules.integration.webhook;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

@Component
public class JsonPathValueExtractor {

    /**
     * Very small JSONPath-like extractor for simple paths like $.a.b or $.data.call.id
     */
    public String extract(JsonNode root, String path) {
        JsonNode node = extractNode(root, path);
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        return node.toString();
    }

    public JsonNode extractNode(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) return null;
        String p = path.trim();
        if (p.startsWith("$.")) p = p.substring(2);
        if (p.startsWith("$")) p = p.substring(1);
        String[] parts = p.split("\\.");
        JsonNode node = root;
        for (String part : parts) {
            if (node == null) return null;
            if (part.isEmpty()) continue;
            node = node.get(part);
        }
        return node;
    }
}
