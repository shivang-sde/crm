package com.shivang.crm.modules.integration.outbound;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboundHttpConnectionCredentialRepository
        extends JpaRepository<OutboundHttpConnectionCredential, UUID> {

    Optional<OutboundHttpConnectionCredential> findByIdAndTenantIdAndIsActiveTrueAndDeletedFalse(UUID id, UUID tenantId);

    java.util.List<OutboundHttpConnectionCredential> findByTenantIdAndConnectionIdAndOwnerUserIdAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID connectionId, UUID ownerUserId);

    java.util.List<OutboundHttpConnectionCredential> findByTenantIdAndConnectionIdAndCredentialScopeAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID connectionId, String credentialScope);

    java.util.List<OutboundHttpConnectionCredential> findByTenantIdAndConnectionIdAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID connectionId);

    // Generic HTTP credentials — connectionId IS NULL, for CREDENTIAL templating mode
    java.util.List<OutboundHttpConnectionCredential> findByTenantIdAndConnectionIdIsNullAndOwnerUserIdAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID ownerUserId);

    java.util.List<OutboundHttpConnectionCredential> findByTenantIdAndConnectionIdIsNullAndCredentialScopeAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, String credentialScope);

    java.util.List<OutboundHttpConnectionCredential> findByTenantIdAndConnectionIdIsNullAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId);

    Optional<OutboundHttpConnectionCredential> findByTenantIdAndConnectionIdIsNullAndOwnerUserIdAndIsActiveTrueAndDeletedFalse(
            UUID tenantId, UUID ownerUserId);

    Optional<OutboundHttpConnectionCredential> findByTenantIdAndConnectionIdIsNullAndCredentialScopeAndIsActiveTrueAndDeletedFalse(
            UUID tenantId, String credentialScope);
}
