package com.shivang.crm.modules.lead.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.shared.base.TenantOwnedEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "leads",
    indexes = {
   @Index(name="idx_lead_tenant", columnList="tenant_id"),
   @Index(name="idx_lead_status", columnList="status_id"),
   @Index(name="idx_lead_owner", columnList="owner_user_id"),
   @Index(name="idx_lead_email", columnList="email"),
   @Index(name="idx_lead_phone", columnList="phone")
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
public class Lead extends TenantOwnedEntity {

    // Standard CRM Fields
    @Column(length = 100, nullable = false)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String company;

    // Status & Source
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id", nullable = false)
    private LeadStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private LeadSource source;

    // Ownership

    @Column(name = "updated_by")
    private UUID updatedBy;

    // Lead tracking
    @Column(columnDefinition = "integer default 0")
    @Builder.Default
    private Integer score = 0;
    
    // Conversion tracking
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isConverted = false;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "converted_account_id")
    private UUID convertedAccountId;

    @Column(name = "converted_contact_id")
    private UUID convertedContactId;

    @Column(name = "last_contacted_at")
    private Instant lastContactedAt;

    // CRITICAL: JSONB for custom fields (NOT EAV pattern)
    // Structure: {"field_key": "value", "vehicle_type": "SUV", "budget": "1500000"}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> customData;

    // Helper methods


    public String getFullName() {
        if (lastName != null && !lastName.isBlank()) {
            return firstName + " " + lastName;
        }
        return firstName;
    }

    
}
