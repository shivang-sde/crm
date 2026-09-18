package com.shivang.crm.modules.commercial.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "integration_external_records",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_external_record_tenant_type_id",
            columnNames = {"tenant_id", "external_type", "external_id"})
    },
    indexes = {
        @Index(name = "idx_ext_rec_tenant_type", columnList = "tenant_id, external_type"),
        @Index(name = "idx_ext_rec_account", columnList = "account_id"),
        @Index(name = "idx_ext_rec_contact", columnList = "contact_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationExternalRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "external_type", nullable = false, length = 30)
    private String externalType; // CUSTOMER, QUOTATION, INVOICE

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "external_updated_at")
    private Instant externalUpdatedAt;

    @Column(name = "pdf_url", length = 2048)
    private String pdfUrl;

    @Column(name = "last_synced_hash", length = 128)
    private String lastSyncedHash;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.lastSyncedAt == null) this.lastSyncedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
