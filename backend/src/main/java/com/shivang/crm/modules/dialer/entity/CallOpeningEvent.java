package com.shivang.crm.modules.dialer.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "call_opening_events")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CallOpeningEvent extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "call_id")
    private UUID callId;

    @Column(name = "external_call_id")
    private String externalCallId;

    @Column(name = "provider_key")
    private String providerKey;

    @Column(name = "trigger_key")
    private String triggerKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "instruction", columnDefinition = "jsonb")
    private java.util.Map<String, Object> instruction;

    @Column(name = "delivery_status")
    private String deliveryStatus;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
