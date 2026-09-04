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

@Entity
@Table(name = "outbound_http_connections")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundHttpConnection extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "auth_type", nullable = false, length = 30)
    private String authType;

    /**
     * Legacy/default credential reference for this connection.
     * Retained for backward compatibility — the authoritative runtime
     * credential selection for HTTP_API now uses
     * {@code OutboundHttpConnectionCredential.credentialScope + ownerUserId + connectionId}
     * (TENANT vs USER) and for generic CREDENTIAL mode uses
     * {@code connectionId IS NULL}. New code should not treat this as the
     * single source of truth; it remains as the canonical tenant default
     * for existing rows and is kept in sync by {@code WorkflowHttpConnectionService}.
     */
    @Column(name = "credential_id")
    private UUID credentialId;

    @PrePersist
    protected void applyDefaults() {
        if (active == null) active = true;
        if (authType == null || authType.isBlank()) authType = "NONE";
    }
}