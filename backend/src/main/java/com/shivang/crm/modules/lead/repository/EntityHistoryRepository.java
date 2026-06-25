package com.shivang.crm.modules.lead.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.lead.entity.EntityHistory;

@Repository
public interface EntityHistoryRepository extends JpaRepository<EntityHistory, UUID> {

    @Query("SELECT lh FROM EntityHistory lh WHERE lh.tenantId = :tenantId AND lh.entityId = :entityId ORDER BY lh.createdAt DESC")
    Page<EntityHistory> findByEntityIdAndTenant(@Param("entityId") UUID entityId, @Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT lh FROM EntityHistory lh WHERE lh.tenantId = :tenantId AND lh.eventType = :eventType ORDER BY lh.createdAt DESC")
    Page<EntityHistory> findByEventTypeAndTenant(@Param("eventType") String eventType, @Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT lh FROM EntityHistory lh WHERE lh.tenantId = :tenantId AND lh.entityId = :entityId AND lh.eventType IN :eventTypes ORDER BY lh.createdAt DESC")
    List<EntityHistory> findHistoriesByEntityIdAndTypes(@Param("entityId") UUID entityId, @Param("tenantId") UUID tenantId, @Param("eventTypes") List<String> eventTypes);

    @Query("SELECT COUNT(lh) FROM EntityHistory lh WHERE lh.tenantId = :tenantId AND lh.entityId = :entityId")
    Integer countByEntityIdAndTenant(@Param("entityId") UUID entityId, @Param("tenantId") UUID tenantId);

    EntityHistory findByIdAndEntityIdAndTenantId(UUID id, UUID entityId, UUID tenantId);

    List<EntityHistory> findByEntityIdAndTenantId(UUID entityId, UUID tenantId);
}
