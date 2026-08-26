package com.shivang.crm.modules.acquisition.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface LeadIngestionEventRepository extends JpaRepository<LeadIngestionEvent, UUID> {

    Optional<LeadIngestionEvent> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Optional<LeadIngestionEvent> findByIdAndTenantIdAndIngestionConfigIdAndDeletedFalse(
        UUID id,
        UUID tenantId,
        UUID ingestionConfigId
    );

    Page<LeadIngestionEvent> findByTenantIdAndIngestionConfigIdAndDeletedFalse(
        UUID tenantId,
        UUID ingestionConfigId,
        Pageable pageable
    );

    Page<LeadIngestionEvent> findByTenantIdAndIngestionConfigIdAndStatusAndDeletedFalse(
        UUID tenantId,
        UUID ingestionConfigId,
        LeadIngestionEventStatus status,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM LeadIngestionEvent e WHERE e.id = :id")
    Optional<LeadIngestionEvent> findByIdForUpdate(@Param("id") UUID id);

    Optional<LeadIngestionEvent> findByTenantIdAndIngestionConfigIdAndIdempotencyKeyAndDeletedFalse(
        UUID tenantId,
        UUID ingestionConfigId,
        String idempotencyKey
    );

    Optional<LeadIngestionEvent> findByTenantIdAndIngestionConfigIdAndExternalEventIdAndDeletedFalse(
        UUID tenantId,
        UUID ingestionConfigId,
        String externalEventId
    );
}