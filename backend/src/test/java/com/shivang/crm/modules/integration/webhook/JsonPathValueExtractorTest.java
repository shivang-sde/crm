package com.shivang.crm.modules.integration.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.Map;

public class JsonPathValueExtractorTest {

    private final JsonPathValueExtractor extractor = new JsonPathValueExtractor();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void simplePath() throws Exception {
        Map<String, Object> payload = Map.of("call_id", "abc123", "foo", "bar");
        var node = mapper.valueToTree(payload);
        String val = extractor.extract(node, "$.call_id");
        assertEquals("abc123", val);
    }

    @Test
    public void nestedPath() throws Exception {
        Map<String, Object> inner = Map.of("id", "x1");
        Map<String, Object> payload = Map.of("data", Map.of("call", inner));
        var node = mapper.valueToTree(payload);
        String val = extractor.extract(node, "$.data.call.id");
        assertEquals("x1", val);
        assertNull(extractor.extract(node, "$.data.call.missing"));
    }
}
