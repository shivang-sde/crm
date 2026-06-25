package com.shivang.crm.modules.lead.entity;

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
@Table(name = "lead_sources",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@lombok.experimental.SuperBuilder
public class LeadSource extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(length = 100, nullable = false)
    private String name;

    private String description;

    @Column(columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean isActive = true;
}
