package com.shivang.crm.modules.deal.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.RecordCategory;

public interface DealRepository extends JpaRepository<Deal, UUID>, JpaSpecificationExecutor<Deal> {

    Optional<Deal> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Deal> findByTenantIdAndName(UUID tenantId, String name);

    @Query("SELECT COUNT(d) FROM Deal d WHERE d.tenantId = :tenantId AND d.stage.id = :stageId AND d.deleted = false")
    Integer countByStageId(@Param("tenantId") UUID tenantId, @Param("stageId") UUID stageId);

    @Query("SELECT COUNT(d) FROM Deal d WHERE d.tenantId = :tenantId AND d.stage.recordCategory = :recordCategory AND d.deleted = false")
    Integer countByRecordCategory(@Param("tenantId") UUID tenantId, @Param("recordCategory") RecordCategory recordCategory);

    @Query("SELECT COUNT(d) FROM Deal d WHERE d.tenantId = :tenantId AND d.stage.recordCategory = com.shivang.crm.modules.deal.entity.RecordCategory.CLOSED_WON AND d.deleted = false")
    Integer countWonDeals(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(d) FROM Deal d WHERE d.tenantId = :tenantId AND d.stage.recordCategory = com.shivang.crm.modules.deal.entity.RecordCategory.CLOSED_LOST AND d.deleted = false")
    Integer countLostDeals(@Param("tenantId") UUID tenantId);
}
