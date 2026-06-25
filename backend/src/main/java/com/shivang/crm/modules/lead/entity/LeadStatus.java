package com.shivang.crm.modules.lead.entity;

import java.time.Instant;
import java.util.UUID;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lead_statuses", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@lombok.experimental.SuperBuilder
public class LeadStatus extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 20)
    private String color;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isDefault = false;

    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isClosed = false;
}
