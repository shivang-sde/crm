package com.shivang.crm.modules.acquisition.config;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "lead_ingestion_configs",
    indexes = {
        @Index(name = "idx_lead_ingestion_configs_tenant_id", columnList = "tenant_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_lead_ingestion_configs_public_key", columnNames = {"public_key"})
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class LeadIngestionConfig extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", length = 30, nullable = false)
    private LeadIngestionTransportType transportType;

    @Column(name = "public_key", length = 255)
    private String publicKey;

    @Column(name = "active", nullable = false, columnDefinition = "boolean default true")
    @Default
    private Boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;
}