package com.shivang.crm.modules.deal.entity;

import java.util.UUID;

import com.shivang.crm.shared.base.BaseEntity;

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
@Table(name = "deal_stages",
    indexes = {
        @Index(name = "idx_deal_stages_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_deal_stages_is_default", columnList = "tenant_id, is_default"),
        @Index(name = "idx_deal_stages_display_order", columnList = "tenant_id, display_order"),
        @Index(name = "idx_deal_stages_record_category", columnList = "tenant_id, record_category")
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DealStage extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 20)
    private String color;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_closed")
    @Builder.Default
    private Boolean isClosed = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_category", length = 30, nullable = false)
    @Builder.Default
    private RecordCategory recordCategory = RecordCategory.OPEN;

    @Column(name = "default_probability")
    @Builder.Default
    private Integer defaultProbability = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_forecast_category", length = 30)
    @Builder.Default
    private ForecastCategory defaultForecastCategory = ForecastCategory.PIPELINE;

    public boolean isWonStage() {
        return RecordCategory.CLOSED_WON.equals(recordCategory);
    }

    public boolean isLostStage() {
        return RecordCategory.CLOSED_LOST.equals(recordCategory);
    }

    public boolean isClosedStage() {
        return isWonStage() || isLostStage();
    }
}
