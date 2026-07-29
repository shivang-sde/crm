package com.shivang.crm.modules.integration.entity;

import java.util.UUID;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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
@Table(
    name = "connector_user_agents",
    indexes = {
        @Index(
            name = "idx_connector_user_agent_tenant",
            columnList = "tenant_id"
        ),
        @Index(
            name = "idx_connector_user_agent_connector",
            columnList = "tenant_id, connector_instance_id"
        ),
        @Index(
            name = "idx_connector_user_agent_user",
            columnList = "tenant_id, user_id"
        ),
        @Index(
            name = "idx_connector_user_agent_external_id",
            columnList =
                "tenant_id, connector_instance_id, external_agent_id"
        ),
        @Index(
            name = "idx_connector_user_agent_external_number",
            columnList =
                "tenant_id, connector_instance_id, external_agent_number"
        )
    }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorUserAgent extends BaseEntity {

    @Column(
        name = "tenant_id",
        nullable = false
    )
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "connector_instance_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_connector_user_agents_connector"
        )
    )
    private ConnectorInstance connectorInstance;

    @Column(
        name = "user_id",
        nullable = false
    )
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        insertable = false,
        updatable = false,
        foreignKey = @ForeignKey(
            name = "fk_connector_user_agents_user"
        )
    )
    private User user;

    @Column(
        name = "external_agent_id",
        length = 150
    )
    private String externalAgentId;

    @Column(
        name = "external_agent_number",
        length = 100
    )
    private String externalAgentNumber;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}