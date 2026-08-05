package com.shivang.crm.modules.catalog.entity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.modules.catalog.enums.BillingInterval;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;
import com.shivang.crm.shared.base.TenantOwnedEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "offerings", indexes = {
        @Index(name = "idx_offering_tenant", columnList = "tenant_id"),
        @Index(name = "idx_offering_type", columnList = "tenant_id, offering_type"),
        @Index(name = "idx_offering_active", columnList = "tenant_id, is_active"),
        @Index(name = "idx_offering_owner", columnList = "tenant_id, owner_user_id"),
        @Index(name = "idx_offering_name", columnList = "tenant_id, name")
})
@AttributeOverride(name = "ownerId", column = @Column(name = "owner_user_id"))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Offering extends TenantOwnedEntity {

    @Column(length = 255, nullable = false)
    private String name;

    @Column(length = 100, nullable = false)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "offering_type", nullable = false, length = 40)
    private OfferingType offeringType;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 40)
    private BillingType billingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", length = 40)
    private BillingInterval billingInterval;

    @Column(name = "default_price", precision = 19, scale = 2)
    private BigDecimal defaultPrice;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "default_term_days")
    private Integer defaultTermDays;

    @Builder.Default
    private Boolean renewable = false;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_data", columnDefinition = "jsonb")
    private Map<String, Object> customData;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
