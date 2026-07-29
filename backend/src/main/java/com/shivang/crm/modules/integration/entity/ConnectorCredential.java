package com.shivang.crm.modules.integration.entity;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.shivang.crm.modules.auth.entity.User;
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
import lombok.Builder;

@Entity
@Table(name = "connector_credentials")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorCredential extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_instance_id", nullable = false)
    private ConnectorInstance connectorInstance;

    @Column(name = "credential_name", nullable = false, length = 200)
    private String credentialName;

    @Column(name = "auth_type", nullable = false, length = 50)
    private String authType;

    @Column(name = "credential_scope", nullable = false, length = 30)
    private String credentialScope; 

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "owner_user_id",
        insertable = false,
        updatable = false
    )
    private User ownerUser;

    @Column(name = "encrypted_value", columnDefinition = "TEXT")
    private String encryptedValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
