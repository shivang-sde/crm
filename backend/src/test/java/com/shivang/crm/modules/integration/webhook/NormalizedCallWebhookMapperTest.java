package com.shivang.crm.modules.integration.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.integration.entity.ConnectorWebhookMapping;

class NormalizedCallWebhookMapperTest {

    private final JsonPathValueExtractor extractor = new JsonPathValueExtractor();
    private final NormalizedCallWebhookMapper mapper = new NormalizedCallWebhookMapper(extractor);

    @Test
    void testTimestampParsing() {
        // ISO instant
        Instant t1 = NormalizedCallWebhookMapper.parseTimestamp("2026-07-22T12:34:56Z");
        assertThat(t1).isNotNull();

        // Epoch seconds
        Instant t2 = NormalizedCallWebhookMapper.parseTimestamp("1753189456");
        assertThat(t2).isNotNull();

        // SellSpark yyyy-MM-dd HH:mm:ss format
        Instant t3 = NormalizedCallWebhookMapper.parseTimestamp("2026-07-22 18:04:56");
        assertThat(t3).isNotNull();
    }

    @Test
    void testMapNewFieldsAndTimestamps() {
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = UUID.randomUUID();

        List<ConnectorWebhookMapping> mappings = List.of(
            ConnectorWebhookMapping.builder()
                .tenantId(tenantId).connectorInstanceId(connectorId).triggerKey("cdr")
                .sourcePath("$.lead_id").targetScope("CANONICAL").targetPath("correlationKey").build(),
            ConnectorWebhookMapping.builder()
                .tenantId(tenantId).connectorInstanceId(connectorId).triggerKey("cdr")
                .sourcePath("$.agent_no").targetScope("CANONICAL").targetPath("agentNumber").build(),
            ConnectorWebhookMapping.builder()
                .tenantId(tenantId).connectorInstanceId(connectorId).triggerKey("cdr")
                .sourcePath("$.start_time").targetScope("CANONICAL").targetPath("startedAt").build(),
            ConnectorWebhookMapping.builder()
                .tenantId(tenantId).connectorInstanceId(connectorId).triggerKey("cdr")
                .sourcePath("$.end_time").targetScope("CANONICAL").targetPath("endedAt").build(),
            ConnectorWebhookMapping.builder()
                .tenantId(tenantId).connectorInstanceId(connectorId).triggerKey("cdr")
                .sourcePath("$.call_duration").targetScope("CANONICAL").targetPath("durationSeconds").build()
        );

        Map<String, Object> payload = Map.of(
            "lead_id", "test-corr-key",
            "agent_no", "1001",
            "start_time", "2026-07-22 10:00:00",
            "end_time", "2026-07-22 10:05:30",
            "call_duration", "330"
        );

        NormalizedCallWebhookEvent event = mapper.map(mappings, payload);

        assertThat(event).isNotNull();
        assertThat(event.getCorrelationKey()).isEqualTo("test-corr-key");
        assertThat(event.getAgentNumber()).isEqualTo("1001");
        assertThat(event.getDurationSeconds()).isEqualTo(330);
        assertThat(event.getStartedAt()).isNotNull();
        assertThat(event.getEndedAt()).isNotNull();
    }
}
