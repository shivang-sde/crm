package com.shivang.crm.modules.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "demo_data_records",
    indexes = {
        @Index(name = "idx_demo_data_records_tenant", columnList = "tenant_id"),
        @Index(name = "idx_demo_data_records_tenant_template", columnList = "tenant_id, template_key"),
        @Index(name = "idx_demo_data_records_tenant_entity", columnList = "tenant_id, entity_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemoDataRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "template_key", length = 100, nullable = false)
    private String templateKey;

    @Column(name = "entity_type", length = 50, nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
