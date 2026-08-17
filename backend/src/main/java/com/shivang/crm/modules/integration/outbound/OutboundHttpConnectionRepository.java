package com.shivang.crm.modules.integration.outbound;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboundHttpConnectionRepository extends JpaRepository<OutboundHttpConnection, UUID> {

    Optional<OutboundHttpConnection> findByIdAndTenantIdAndActiveTrueAndDeletedFalse(UUID id, UUID tenantId);
}