package com.shivang.crm.modules.acquisition.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "lead_ingestion_events",
    indexes = {
        @Index(name = "idx_lead_ingestion_events_tenant_config", columnList = "tenant_id, ingestion_config_id"),
        @Index(name = "idx_lead_ingestion_events_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_lead_ingestion_events_lead_id", columnList = "lead_id")
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class LeadIngestionEvent extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ingestion_config_id", nullable = false)
    private UUID ingestionConfigId;

    @Column(name = "external_event_id", length = 255)
    private String externalEventId;

    @Column(name = "idempotency_key", length = 1024)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    private Map<String, Object> headers;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    @Default
    private LeadIngestionEventStatus status = LeadIngestionEventStatus.RECEIVED;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}