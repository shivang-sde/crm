package com.shivang.crm.modules.dialer.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.dialer.entity.CallProviderLink;

@Repository
public interface CallProviderLinkRepository extends JpaRepository<CallProviderLink, UUID> {
    List<CallProviderLink> findByTenantId(UUID tenantId);
    Optional<CallProviderLink> findByCallId(UUID callId);
    Optional<CallProviderLink> findByExternalCallId(String externalCallId);
}
