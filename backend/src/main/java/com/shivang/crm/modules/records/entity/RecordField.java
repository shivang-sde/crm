package com.shivang.crm.modules.records.entity;

import java.util.List;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "record_fields",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_record_fields_type_key", columnNames = {"record_type_id", "field_key"})
    },
    indexes = {
        @Index(name = "idx_record_fields_tenant", columnList = "tenant_id"),
        @Index(name = "idx_record_fields_type", columnList = "record_type_id"),
        @Index(name = "idx_record_fields_type_active", columnList = "record_type_id, is_active")
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RecordField extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "record_type_id", nullable = false)
    private UUID recordTypeId;

    @Column(name = "field_key", length = 100, nullable = false)
    private String fieldKey;

    @Column(name = "field_label", length = 200, nullable = false)
    private String fieldLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", length = 30, nullable = false)
    private RecordFieldType fieldType;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean isRequired = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json", columnDefinition = "jsonb")
    private List<String> optionsJson;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Column(name = "reference_entity_type", length = 30)
    private String referenceEntityType;
}
