package com.shivang.crm.modules.records.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "record_webhook_deliveries",
    indexes = {
        @Index(name = "idx_webhook_deliveries_tenant_webhook", columnList = "tenant_id, webhook_id"),
        @Index(name = "idx_webhook_deliveries_tenant_key", columnList = "tenant_id, webhook_id, idempotency_key")
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RecordWebhookDelivery extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "webhook_id", nullable = false)
    private UUID webhookId;

    @Column(name = "webhook_key", length = 100, nullable = false)
    private String webhookKey;

    @Column(name = "idempotency_key", length = 1024, nullable = false)
    private String idempotencyKey;

    @Column(name = "payload_hash", length = 64, nullable = false)
    private String payloadHash;

    @Column(length = 30, nullable = false)
    private String status;

    @Column(name = "record_id")
    private UUID recordId;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private Map<String, Object> responseBody;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "record_type_id")
    private UUID recordTypeId;

    @Column(name = "mapping_profile_id")
    private UUID mappingProfileId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "failure_stage", length = 30)
    private String failureStage;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();
}
