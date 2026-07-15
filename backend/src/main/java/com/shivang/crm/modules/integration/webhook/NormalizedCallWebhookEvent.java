package com.shivang.crm.modules.integration.webhook;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NormalizedCallWebhookEvent {
    private String externalCallId;
    private String externalEventId;
    private String agentId;
    private String direction;
    private String callerNumber;
    private String calleeNumber;
    private String providerStatus;
    private Integer durationSeconds;
    private String recordingUrl;
    private String disposition;
    private Instant startedAt;
    private Instant endedAt;
    private Instant eventTimestamp;
    private Map<String, Object> rawPayload;
    private Map<String, Object> mappedValues;
}
