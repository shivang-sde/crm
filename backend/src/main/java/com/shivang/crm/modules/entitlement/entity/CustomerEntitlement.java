package com.shivang.crm.modules.entitlement.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.TenantOwnedEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "customer_entitlements", indexes = {
        @Index(name = "idx_entitlement_tenant", columnList = "tenant_id"),
        @Index(name = "idx_entitlement_account", columnList = "tenant_id, account_id"),
        @Index(name = "idx_entitlement_contact", columnList = "tenant_id, contact_id"),
        @Index(name = "idx_entitlement_offering", columnList = "tenant_id, offering_id"),
        @Index(name = "idx_entitlement_deal", columnList = "tenant_id, deal_id"),
        @Index(name = "idx_entitlement_status", columnList = "tenant_id, status"),
        @Index(name = "idx_entitlement_end_date", columnList = "tenant_id, end_date"),
        @Index(name = "idx_entitlement_status_end", columnList = "tenant_id, status, end_date"),
        @Index(name = "idx_entitlement_owner", columnList = "tenant_id, owner_user_id")
})
@AttributeOverride(name = "ownerId", column = @Column(name = "owner_user_id"))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEntitlement extends TenantOwnedEntity {

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "offering_id")
    private UUID offeringId;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "deal_line_item_id")
    private UUID dealLineItemId;

    @Column(length = 255)
    private String name;

    @Column(length = 100)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EntitlementStatus status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "agreed_price", precision = 19, scale = 2)
    private BigDecimal agreedPrice;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column
    private Boolean renewable;

    @Column(name = "auto_renew")
    private Boolean autoRenew;

    @Column(name = "renewal_notice_days")
    private Integer renewalNoticeDays;

    @Column(name = "renewal_due_date")
    private LocalDate renewalDueDate;

    @Column(name = "renewed_from_entitlement_id")
    private UUID renewedFromEntitlementId;

    @Column(name = "renewed_to_entitlement_id")
    private UUID renewedToEntitlementId;

    @Column(name = "renewal_deal_id")
    private UUID renewalDealId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_data", columnDefinition = "jsonb")
    private Map<String, Object> customData;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
