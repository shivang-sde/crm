package com.shivang.crm.modules.integration.entity;

import java.util.UUID;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "connector_webhook_mappings")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorWebhookMapping extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "connector_instance_id", nullable = false)
    private UUID connectorInstanceId;

    @Column(name = "trigger_key", nullable = false, length = 100)
    private String triggerKey;

    @Column(name = "source_path", nullable = false, length = 500)
    private String sourcePath;

    @Column(name = "target_scope", nullable = false, length = 100)
    private String targetScope;

    @Column(name = "target_path", nullable = false, length = 200)
    private String targetPath;

    @Column(name = "transform_type", length = 100)
    private String transformType;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Column(name = "is_required")
    private Boolean isRequired = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "display_order")
    private Integer displayOrder = 0;
}
