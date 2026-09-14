package com.shivang.crm.modules.records.entity;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "record_display_configs",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_record_display_configs_type", columnNames = {"record_type_id"})
    },
    indexes = {
        @Index(name = "idx_record_display_configs_tenant", columnList = "tenant_id"),
        @Index(name = "idx_record_display_configs_type", columnList = "record_type_id")
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RecordDisplayConfig extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "record_type_id", nullable = false)
    private UUID recordTypeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> config;
}
