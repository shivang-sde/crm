package com.shivang.crm.modules.deal.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.BaseEntity;

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
@Table(name = "deal_line_items", indexes = {
        @Index(name = "idx_deal_line_item_tenant", columnList = "tenant_id"),
        @Index(name = "idx_deal_line_item_deal", columnList = "tenant_id, deal_id"),
        @Index(name = "idx_deal_line_item_offering", columnList = "tenant_id, offering_id"),
        @Index(name = "idx_deal_line_item_service_end", columnList = "tenant_id, service_end_date")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DealLineItem extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    @Column(name = "offering_id", nullable = false)
    private UUID offeringId;

    @Column(name = "item_name", length = 255)
    private String itemName;

    @Column(name = "item_code", length = 100)
    private String itemCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_amount", precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "line_total", precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "service_start_date")
    private LocalDate serviceStartDate;

    @Column(name = "service_end_date")
    private LocalDate serviceEndDate;

    @Column
    private Boolean renewable;

    @Column(name = "renewal_notice_days")
    private Integer renewalNoticeDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_data", columnDefinition = "jsonb")
    private Map<String, Object> customData;


    @Column(name = "updated_by")
    private UUID updatedBy;
}
