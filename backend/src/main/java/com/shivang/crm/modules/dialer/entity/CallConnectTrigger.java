package com.shivang.crm.modules.dialer.entity;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "call_connect_triggers")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CallConnectTrigger extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_instance_id")
    private ConnectorInstance connectorInstance;

    @Column(name = "trigger_key", nullable = false, length = 100)
    private String triggerKey;

    @Column(name = "call_direction", nullable = false, length = 20)
    private String callDirection;

    @Column(name = "open_action_type", nullable = false, length = 50)
    private String openActionType;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_resolve_by", length = 50)
    private String entityResolveBy;

    @Column(name = "target_route", length = 255)
    private String targetRoute;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "priority")
    private Integer priority = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
