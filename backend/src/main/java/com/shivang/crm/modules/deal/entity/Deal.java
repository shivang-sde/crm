package com.shivang.crm.modules.deal.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "deals",
    indexes = {
        @Index(name = "idx_deal_tenant", columnList = "tenant_id"),
        @Index(name = "idx_deal_stage_id", columnList = "stage_id"),
        @Index(name = "idx_deal_owner_user_id", columnList = "owner_user_id"),
        @Index(name = "idx_deal_account_id", columnList = "account_id"),
        @Index(name = "idx_deal_contact_id", columnList = "contact_id"),
        @Index(name = "idx_deal_lead_id", columnList = "lead_id"),
        @Index(name = "idx_deal_created_at", columnList = "created_at"),
        @Index(name = "idx_deal_expected_close_date", columnList = "expected_close_date"),
        @Index(name = "idx_deal_closed_date", columnList = "closed_date"),
        @Index(name = "idx_deal_forecast_category", columnList = "tenant_id, forecast_category")
    }
)
@AttributeOverrides({
    @AttributeOverride(name = "ownerId", column = @Column(name = "owner_user_id"))
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Deal extends TenantOwnedEntity {

    @Column(length = 255, nullable = false)
    private String name;

    // Relationships
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "contact_id")
    private UUID contactId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stage_id", nullable = false)
    private DealStage stage;

    @Column(name = "lead_id")
    private UUID leadId;

    // Financial & Timeline
    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column
    @Builder.Default
    private Integer probability = 0;

    @Column(name = "expected_revenue", precision = 18, scale = 2)
    private BigDecimal expectedRevenue;

    @Enumerated(EnumType.STRING)
    @Column(name = "forecast_category", length = 30)
    private ForecastCategory forecastCategory;

    @Column(name = "next_step", columnDefinition = "TEXT")
    private String nextStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "deal_type", length = 30)
    private DealType dealType;

    @Column(name = "lead_source", length = 100)
    private String leadSource;

    @Column(name = "campaign_source", length = 255)
    private String campaignSource;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "won_reason", columnDefinition = "TEXT")
    private String wonReason;

    @Column(name = "lost_reason", columnDefinition = "TEXT")
    private String lostReason;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Ownership
    @Column(name = "updated_by")
    private UUID updatedBy;

    // CRITICAL: JSONB for custom fields (NOT EAV pattern)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> customData;

    // Helper methods
    public String getStageName() {
        return stage != null ? stage.getName() : null;
    }

    public RecordCategory getRecordCategory() {
        return stage != null ? stage.getRecordCategory() : RecordCategory.OPEN;
    }

    public boolean isWon() {
        return stage != null && stage.isWonStage();
    }

    public boolean isLost() {
        return stage != null && stage.isLostStage();
    }
}
