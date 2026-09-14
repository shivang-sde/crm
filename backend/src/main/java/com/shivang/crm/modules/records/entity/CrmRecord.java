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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "records",
    indexes = {
        @Index(name = "idx_records_tenant", columnList = "tenant_id"),
        @Index(name = "idx_records_tenant_type", columnList = "tenant_id, record_type_id"),
        @Index(name = "idx_records_type", columnList = "record_type_id"),
        @Index(name = "idx_records_created_at", columnList = "created_at")
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
public class CrmRecord extends TenantOwnedEntity {

    @Column(name = "record_type_id", nullable = false)
    private UUID recordTypeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> data;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
