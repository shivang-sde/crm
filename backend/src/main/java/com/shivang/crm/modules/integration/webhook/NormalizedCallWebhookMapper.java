package com.shivang.crm.modules.integration.webhook;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.integration.entity.ConnectorWebhookMapping;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class NormalizedCallWebhookMapper {

    private final JsonPathValueExtractor extractor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * SellSpark sends timestamps as "yyyy-MM-dd HH:mm:ss" without timezone.
     * Default to Asia/Kolkata (IST) for Indian operations.
     */
    private static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter SELLSPARK_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public NormalizedCallWebhookMapper(JsonPathValueExtractor extractor) {
        this.extractor = extractor;
    }

    public NormalizedCallWebhookEvent map(List<ConnectorWebhookMapping> mappings, Map<String, Object> rawPayload) {
        NormalizedCallWebhookEvent.NormalizedCallWebhookEventBuilder builder = NormalizedCallWebhookEvent.builder();
        Map<String, Object> mapped = new HashMap<>();
        JsonNode root = objectMapper.valueToTree(rawPayload == null ? Map.of() : rawPayload);

        for (ConnectorWebhookMapping m : mappings) {
            try {
                String value = extractor.extract(root, m.getSourcePath());
                if (value == null) {
                    if (Boolean.TRUE.equals(m.getIsRequired())) {
                        // required missing -> mark failure by returning null
                        return null;
                    }
                    value = m.getDefaultValue();
                }
                mapped.put(m.getTargetPath(), value);
                // apply to canonical fields
                if (m.getTargetScope() != null && m.getTargetScope().equals("CANONICAL")) {
                    applyCanonicalField(builder, m.getTargetPath(), value);
                }
            } catch (Exception e) {
                // ignore per-field errors unless required
                log.debug("Error extracting field {}: {}", m.getSourcePath(), e.getMessage());
            }
        }

        builder.rawPayload(rawPayload == null ? Map.of() : rawPayload);
        builder.mappedValues(mapped);
        return builder.build();
    }

    private void applyCanonicalField(NormalizedCallWebhookEvent.NormalizedCallWebhookEventBuilder builder,
                                     String targetPath, String value) {
        if (value == null) return;

        switch (targetPath) {
            case "externalCallId": builder.externalCallId(value); break;
            case "externalEventId": builder.externalEventId(value); break;
            case "correlationKey": builder.correlationKey(value); break;
            case "agentId": builder.agentId(value); break;
            case "agentNumber": builder.agentNumber(value); break;
            case "direction": builder.direction(value); break;
            case "callerNumber": builder.callerNumber(value); break;
            case "calleeNumber": builder.calleeNumber(value); break;
            case "providerStatus": builder.providerStatus(value); break;
            case "recordingUrl": builder.recordingUrl(value); break;
            case "disposition": builder.disposition(value); break;
            case "eventTimestamp": {
                Instant t = parseTimestamp(value);
                if (t != null) builder.eventTimestamp(t);
                break;
            }
            case "startedAt": {
                Instant t = parseTimestamp(value);
                if (t != null) builder.startedAt(t);
                break;
            }
            case "endedAt": {
                Instant t = parseTimestamp(value);
                if (t != null) builder.endedAt(t);
                break;
            }
            case "durationSeconds": {
                try { builder.durationSeconds(Integer.valueOf(value)); } catch (Exception e) {
                    log.debug("Failed to parse durationSeconds: {}", value);
                }
                break;
            }
            default: break;
        }
    }

    /**
     * Safe timestamp parser supporting:
     * 1. ISO instant (e.g. 2026-07-22T12:34:56Z)
     * 2. Epoch seconds (e.g. 1753189456)
     * 3. SellSpark local datetime: yyyy-MM-dd HH:mm:ss (interpreted as Asia/Kolkata)
     */
    static Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();

        // 1. Try ISO instant
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {}

        // 2. Try SellSpark local datetime format: yyyy-MM-dd HH:mm:ss
        try {
            LocalDateTime ldt = LocalDateTime.parse(trimmed, SELLSPARK_FORMAT);
            return ldt.atZone(DEFAULT_TIMEZONE).toInstant();
        } catch (DateTimeParseException ignored) {}

        // 3. Try epoch seconds
        try {
            long epoch = Long.parseLong(trimmed);
            return Instant.ofEpochSecond(epoch);
        } catch (NumberFormatException ignored) {}

        log.warn("Unable to parse timestamp value: {}", trimmed);
        return null;
    }
}
