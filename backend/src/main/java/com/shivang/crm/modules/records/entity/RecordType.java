package com.shivang.crm.modules.records.entity;

import java.util.UUID;

import com.shivang.crm.shared.base.TenantOwnedEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "record_types",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_record_types_tenant_key", columnNames = {"tenant_id", "type_key"}),
        @UniqueConstraint(name = "uq_record_types_tenant_name", columnNames = {"tenant_id", "name"})
    },
    indexes = {
        @Index(name = "idx_record_types_tenant", columnList = "tenant_id"),
        @Index(name = "idx_record_types_tenant_active", columnList = "tenant_id, is_active")
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
public class RecordType extends TenantOwnedEntity {

    @Column(name = "type_key", length = 100, nullable = false)
    private String key;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
