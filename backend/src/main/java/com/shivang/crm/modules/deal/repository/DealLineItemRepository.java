package com.shivang.crm.modules.deal.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.deal.entity.DealLineItem;

@Repository
public interface DealLineItemRepository extends JpaRepository<DealLineItem, UUID> {

    List<DealLineItem> findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(UUID tenantId, UUID dealId);

    Optional<DealLineItem> findByIdAndTenantIdAndDealIdAndDeletedFalse(UUID id, UUID tenantId, UUID dealId);

    boolean existsByIdAndTenantIdAndDealIdAndDeletedFalse(UUID id, UUID tenantId, UUID dealId);

    boolean existsByTenantIdAndDealIdAndDeletedFalse(UUID tenantId, UUID dealId);

    @Query("SELECT COALESCE(SUM(dli.lineTotal), 0) FROM DealLineItem dli WHERE dli.tenantId = :tenantId AND dli.dealId = :dealId AND dli.deleted = false")
    BigDecimal sumLineTotalsByTenantIdAndDealIdAndDeletedFalse(@Param("tenantId") UUID tenantId, @Param("dealId") UUID dealId);
    boolean existsByTenantIdAndOfferingIdAndDeletedFalse(UUID tenantId, UUID offeringId);
}
