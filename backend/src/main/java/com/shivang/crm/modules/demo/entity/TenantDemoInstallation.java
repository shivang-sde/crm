package com.shivang.crm.modules.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tenant_demo_installations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "template_key", "template_version"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantDemoInstallation {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "template_key", length = 100, nullable = false)
    private String templateKey;

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion;

    @Column(name = "installed_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant installedAt = Instant.now();

    @Column(name = "installed_by", nullable = false)
    private UUID installedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> summary;
}
