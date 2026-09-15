package com.shivang.crm.modules.records.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordWebhookDeliveryDetailResponse {
    private Delivery delivery;
    private WebhookInfo webhook;
    private RecordTypeInfo recordType;
    private MappingInfo mappingProfile;
    private IdempotencyInfo idempotency;
    private RecordInfo record;
    private EventInfo event;
    private WorkflowInfo workflow;
    private FailureInfo failure;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Delivery {
        private UUID id;
        private String status;
        private String failureStage;
        private Integer responseStatus;
        private String errorCode;
        private String errorMessage;
        private String payloadHash;
        private String idempotencyKey;
        private Instant receivedAt;
        private Instant createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WebhookInfo {
        private UUID id;
        private String key;
        private String name;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecordTypeInfo {
        private UUID id;
        private String key;
        private String name;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MappingInfo {
        private UUID id;
        private String key;
        private String name;
        private String mode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class IdempotencyInfo {
        private Boolean applied;
        private String key;
        private String payloadHash;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecordInfo {
        private UUID id;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EventInfo {
        private UUID eventId;
        private String entityType;
        private String eventType;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WorkflowInfo {
        private Boolean triggered;
        private Integer executionCount;
        private List<ExecutionInfo> executions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExecutionInfo {
        private UUID id;
        private UUID workflowId;
        private String workflowName;
        private UUID workflowVersionId;
        private String status;
        private UUID triggerEventId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FailureInfo {
        private String stage;
        private String code;
        private String message;
    }
}
