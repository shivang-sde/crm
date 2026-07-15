package com.shivang.crm.modules.integration.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "connector_webhook_events")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorWebhookEvent extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_instance_id", nullable = false)
    private ConnectorInstance connectorInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_execution_id")
    private ConnectorExecution connectorExecution;

    @Column(name = "external_event_id", length = 255)
    private String externalEventId;

    @Column(name = "external_reference_id", length = 255)
    private String externalReferenceId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "verification_status", nullable = false, length = 50)
    private String verificationStatus;

    @Column(name = "processing_status", nullable = false, length = 50)
    private String processingStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", columnDefinition = "jsonb")
    private Map<String, Object> eventPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_headers", columnDefinition = "jsonb")
    private Map<String, Object> eventHeaders;

    @Column(name = "idempotency_key", length = 1024)
    private String idempotencyKey;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
