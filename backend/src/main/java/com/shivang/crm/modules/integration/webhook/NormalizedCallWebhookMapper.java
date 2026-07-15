package com.shivang.crm.modules.integration.webhook;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.integration.entity.ConnectorWebhookMapping;

import org.springframework.stereotype.Component;

@Component
public class NormalizedCallWebhookMapper {

    private final JsonPathValueExtractor extractor;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                    switch (m.getTargetPath()) {
                        case "externalCallId": builder.externalCallId(value); break;
                        case "externalEventId": builder.externalEventId(value); break;
                        case "agentId": builder.agentId(value); break;
                        case "direction": builder.direction(value); break;
                        case "callerNumber": builder.callerNumber(value); break;
                        case "calleeNumber": builder.calleeNumber(value); break;
                        case "providerStatus": builder.providerStatus(value); break;
                        case "recordingUrl": builder.recordingUrl(value); break;
                        case "disposition": builder.disposition(value); break;
                        case "eventTimestamp": {
                            try {
                                Instant t = Instant.parse(value);
                                builder.eventTimestamp(t);
                            } catch (DateTimeParseException ex) {
                                // try epoch
                                try { builder.eventTimestamp(Instant.ofEpochSecond(Long.parseLong(value))); } catch (Exception e) {}
                            }
                            break;
                        }
                        case "durationSeconds": {
                            try { builder.durationSeconds(Integer.valueOf(value)); } catch (Exception e) {}
                            break;
                        }
                        default: break;
                    }
                }
            } catch (Exception e) {
                // ignore per-field errors unless required
            }
        }

        builder.rawPayload(rawPayload == null ? Map.of() : rawPayload);
        builder.mappedValues(mapped);
        return builder.build();
    }
}
