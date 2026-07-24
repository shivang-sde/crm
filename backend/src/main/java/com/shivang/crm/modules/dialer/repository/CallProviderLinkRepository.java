package com.shivang.crm.modules.dialer.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.dialer.entity.CallProviderLink;

@Repository
public interface CallProviderLinkRepository extends JpaRepository<CallProviderLink, UUID> {
    List<CallProviderLink> findByTenantIdAndDeletedFalse(UUID tenantId);
    Optional<CallProviderLink>
    findByTenantIdAndCallIdAndDeletedFalse(
            UUID tenantId,
            UUID callId
    );
    Optional<CallProviderLink>
    findByTenantIdAndExternalCallIdAndDeletedFalse(
            UUID tenantId,
            String externalCallId
    );

     Optional<CallProviderLink>
    findByTenantIdAndCorrelationKeyAndDeletedFalse(
            UUID tenantId,
            String correlationKey
    );

}
