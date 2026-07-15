package com.shivang.crm.modules.integration.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import com.shivang.crm.modules.integration.entity.ConnectorWebhookMapping;
import org.junit.jupiter.api.Test;

import java.util.List;

public class SellSparkMappingTest {

    private final JsonPathValueExtractor extractor = new JsonPathValueExtractor();
    private final NormalizedCallWebhookMapper mapper = new NormalizedCallWebhookMapper(extractor);

    @Test
    public void callConnectMapping() {
        Map<String, Object> payload = Map.of(
            "call_id", "c-123",
            "agent_id", "a-1",
            "caller_number", "+100",
            "callee_number", "+200",
            "direction", "OUT",
            "timestamp", "2026-07-08T10:00:00Z"
        );

        List<ConnectorWebhookMapping> mappings = List.of(
            ConnectorWebhookMapping.builder().sourcePath("$.call_id").targetScope("CANONICAL").targetPath("externalCallId").isRequired(true).build(),
            ConnectorWebhookMapping.builder().sourcePath("$.agent_id").targetScope("CANONICAL").targetPath("agentId").build(),
            ConnectorWebhookMapping.builder().sourcePath("$.caller_number").targetScope("CANONICAL").targetPath("callerNumber").build(),
            ConnectorWebhookMapping.builder().sourcePath("$.callee_number").targetScope("CANONICAL").targetPath("calleeNumber").build(),
            ConnectorWebhookMapping.builder().sourcePath("$.direction").targetScope("CANONICAL").targetPath("direction").build(),
            ConnectorWebhookMapping.builder().sourcePath("$.timestamp").targetScope("CANONICAL").targetPath("eventTimestamp").build()
        );

        NormalizedCallWebhookEvent evt = mapper.map(mappings, payload);
        assertNotNull(evt);
        assertEquals("c-123", evt.getExternalCallId());
        assertEquals("a-1", evt.getAgentId());
        assertEquals("+100", evt.getCallerNumber());
        assertEquals("+200", evt.getCalleeNumber());
    }

    @Test
    public void cdrMapping() {
        Map<String, Object> payload = Map.of(
            "call_id", "c-789",
            "agent_id", "a-9",
            "duration", "120",
            "status", "completed",
            "recording_url", "https://rec",
            "disposition", "no-answer",
            "timestamp", "2026-07-08T11:00:00Z"
        );

        List<ConnectorWebhookMapping> mappings = List.of(
            ConnectorWebhookMapping.builder().sourcePath("$.call_id").targetScope("CANONICAL").targetPath("externalCallId").isRequired(true).build(),
            ConnectorWebhookMapping.builder().sourcePath("$.agent_id").targetScope("CANONICAL").targetPath("agentId").build(),
            ConnectorWebhookMapping.builder().sourcePath("$.duration").targetScope("CANONICAL").targetPath("durationSeconds").build(),
            ConnectorWebhookMapping.builder().sourcePath("$.status").targetScope("CANONICAL").targetPath("providerStatus").build(),
            ConnectorWebhookMapping.builder().sourcePath("$.recording_url").targetScope("CANONICAL").targetPath("recordingUrl").build(),
            ConnectorWebhookMapping.builder().sourcePath("$.disposition").targetScope("CANONICAL").targetPath("disposition").build(),
            ConnectorWebhookMapping.builder().sourcePath("$.timestamp").targetScope("CANONICAL").targetPath("eventTimestamp").build()
        );

        NormalizedCallWebhookEvent evt = mapper.map(mappings, payload);
        assertNotNull(evt);
        assertEquals("c-789", evt.getExternalCallId());
        assertEquals(Integer.valueOf(120), evt.getDurationSeconds());
        assertEquals("completed", evt.getProviderStatus());
        assertEquals("https://rec", evt.getRecordingUrl());
    }
}
