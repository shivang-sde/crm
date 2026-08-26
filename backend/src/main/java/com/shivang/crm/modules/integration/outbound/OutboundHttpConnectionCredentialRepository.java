package com.shivang.crm.modules.integration.outbound;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboundHttpConnectionCredentialRepository
        extends JpaRepository<OutboundHttpConnectionCredential, UUID> {

    Optional<OutboundHttpConnectionCredential> findByIdAndTenantIdAndIsActiveTrueAndDeletedFalse(UUID id, UUID tenantId);
}
