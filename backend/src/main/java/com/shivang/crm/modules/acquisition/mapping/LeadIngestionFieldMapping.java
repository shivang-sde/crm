package com.shivang.crm.modules.acquisition.mapping;

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
    name = "lead_ingestion_field_mappings",
    indexes = {
        @Index(name = "idx_lead_ingestion_field_mappings_tenant_config", columnList = "tenant_id, ingestion_config_id")
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class LeadIngestionFieldMapping extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ingestion_config_id", nullable = false)
    private UUID ingestionConfigId;

    @Column(name = "source_path", length = 500, nullable = false)
    private String sourcePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30, nullable = false)
    private LeadIngestionTargetType targetType;

    @Column(name = "target_field", length = 100, nullable = false)
    private String targetField;

    @Enumerated(EnumType.STRING)
    @Column(name = "transform_type", length = 30, nullable = false)
    @Default
    private LeadIngestionTransformType transformType = LeadIngestionTransformType.NONE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transform_config", columnDefinition = "jsonb")
    private Map<String, Object> transformConfig;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Column(name = "required", nullable = false, columnDefinition = "boolean default false")
    @Default
    private Boolean required = false;

    @Column(name = "active", nullable = false, columnDefinition = "boolean default true")
    @Default
    private Boolean active = true;

    @Column(name = "display_order", nullable = false, columnDefinition = "integer default 0")
    @Default
    private Integer displayOrder = 0;
}