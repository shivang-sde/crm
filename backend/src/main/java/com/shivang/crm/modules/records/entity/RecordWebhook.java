package com.shivang.crm.modules.records.entity;

import java.util.UUID;

import com.shivang.crm.shared.base.TenantOwnedEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "record_webhooks",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_record_webhooks_tenant_key", columnNames = {"tenant_id", "webhook_key"})
    },
    indexes = {
        @Index(name = "idx_record_webhooks_tenant", columnList = "tenant_id"),
        @Index(name = "idx_record_webhooks_tenant_type", columnList = "tenant_id, record_type_id"),
        @Index(name = "idx_record_webhooks_type", columnList = "record_type_id")
    }
)
@AttributeOverrides({
    @AttributeOverride(name = "ownerId", column = @Column(name = "owner_user_id"))
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RecordWebhook extends TenantOwnedEntity {

    @Column(length = 200, nullable = false)
    private String name;

    @Column(name = "webhook_key", length = 100, nullable = false)
    private String webhookKey;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "record_type_id", nullable = false)
    private UUID recordTypeId;

    @Column(name = "mapping_profile_id")
    private UUID mappingProfileId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_mode", length = 20, nullable = false)
    @Builder.Default
    private WebhookAuthMode authMode = WebhookAuthMode.NONE;

    @Column(name = "secret_hash", length = 128)
    private String secretHash;

    @Column(name = "secret_encrypted", columnDefinition = "TEXT")
    private String secretEncrypted;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
