package com.shivang.crm.modules.integration.outbound;

import java.util.UUID;

import com.shivang.crm.shared.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Encrypted credential material for outbound HTTP.
 *
 * <p>Two usages share this table:</p>
 * <ul>
 *   <li><b>Connection-bound</b> — {@code connectionId != null}: credential belongs to a reusable
 *       {@link OutboundHttpConnection} and is selected at runtime via
 *       {@code credentialScope (TENANT|USER) + ownerUserId + connectionId}.
 *       One row per connection per scope (tenant shared + per-user).</li>
 *   <li><b>Generic workflow credential</b> — {@code connectionId == null}: not bound to any reusable
 *       connection, used by HTTP_API {@code authenticationMode=CREDENTIAL} with
 *       {@code {{credential.*}}} templating. Selected via
 *       {@code credentialScope + ownerUserId + tenantId} only.</li>
 * </ul>
 * <p>Values are encrypted through the shared {@link com.shivang.crm.modules.integration.service.CredentialEncryptionService}
 * (AES-256-GCM) before persistence and are decrypted only at request
 * execution time inside the outbound transport or {@code WorkflowCredentialService}.
 * Rows are tenant-scoped and soft-deletable. Never returned via API.</p>
 */
@Entity
@Table(name = "outbound_http_connection_credentials")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundHttpConnectionCredential extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "auth_type", nullable = false, length = 30)
    private String authType;

    @Column(name = "encrypted_value", nullable = false, columnDefinition = "text")
    private String encryptedValue;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(name = "credential_scope", nullable = false, length = 16)
    private String credentialScope;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @PrePersist
    protected void applyDefaults() {
        if (isActive == null) isActive = true;
        if (credentialScope == null || credentialScope.isBlank()) credentialScope = ownerUserId == null ? "TENANT" : "USER";
        setDeleted(false);
    }
}
