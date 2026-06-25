package com.shivang.crm.modules.deal.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;

public interface DealStageRepository extends JpaRepository<DealStage, UUID> {

    Optional<DealStage> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DealStage> findByTenantIdAndName(UUID tenantId, String name);

    List<DealStage> findByTenantIdOrderByDisplayOrder(UUID tenantId);

    Optional<DealStage> findByTenantIdAndIsDefault(UUID tenantId, Boolean isDefault);

    Optional<DealStage> findFirstByTenantIdAndRecordCategoryOrderByDisplayOrder(UUID tenantId, RecordCategory recordCategory);

    @Query("SELECT COUNT(ds) FROM DealStage ds WHERE ds.tenantId = :tenantId AND ds.deleted = false")
    Integer countByTenantId(@Param("tenantId") UUID tenantId);
}
