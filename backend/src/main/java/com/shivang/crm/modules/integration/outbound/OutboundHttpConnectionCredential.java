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
 * Encrypted credential material for an Outbound HTTP connection.
 *
 * Values are encrypted through the shared {@link com.shivang.crm.modules.integration.service.CredentialEncryptionService}
 * (AES-256-GCM) before persistence and are decrypted only at request
 * execution time inside the outbound transport. Rows are tenant-scoped and
 * soft-deletable following project conventions.
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

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @PrePersist
    protected void applyDefaults() {
        if (isActive == null) isActive = true;
        setDeleted(false);
    }
}
