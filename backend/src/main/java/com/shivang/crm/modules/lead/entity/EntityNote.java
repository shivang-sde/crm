package com.shivang.crm.modules.lead.entity;

import java.util.UUID;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "entity_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@lombok.experimental.SuperBuilder
public class EntityNote extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    private String entityType;

    private UUID entityId;

    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "lead_id", nullable = false)
    // private Lead lead;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String note;

    @Column(nullable = false)
    private UUID createdBy;

    private UUID updatedBy;
}
