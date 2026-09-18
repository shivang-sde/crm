package com.shivang.crm.modules.records.entity;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.TenantOwnedEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
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
@Table(name = "record_mapping_profiles",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_mapping_profiles_tenant_key", columnNames = {"tenant_id", "mapping_key"})
    },
    indexes = {
        @Index(name = "idx_mapping_profiles_tenant", columnList = "tenant_id"),
        @Index(name = "idx_mapping_profiles_tenant_type", columnList = "tenant_id, record_type_id"),
        @Index(name = "idx_;mapping_profiles_type", columnList = "record_type_id")
    }
)
@AttributeOverrides({
    @AttributeOverride(name = "ownerId", column = @Column(name = "owner_user_id"))
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RecordMappingProfile extends TenantOwnedEntity {

    @Column(name = "record_type_id", nullable = false)
    private UUID recordTypeId;

    @Column(name = "mapping_key", length = 100, nullable = false)
    private String mappingKey;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private MappingProfileMode mode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> configuration = Map.of();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

}
