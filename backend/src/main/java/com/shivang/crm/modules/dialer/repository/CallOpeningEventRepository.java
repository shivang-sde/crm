package com.shivang.crm.modules.dialer.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shivang.crm.modules.dialer.entity.CallOpeningEvent;

public interface CallOpeningEventRepository extends JpaRepository<CallOpeningEvent, UUID> {
    List<CallOpeningEvent> findByTenantIdAndDeliveryStatus(UUID tenantId, String deliveryStatus);
    List<CallOpeningEvent> findByTenantIdAndAgentIdAndDeliveryStatus(UUID tenantId, String agentId, String deliveryStatus);

    Optional<CallOpeningEvent> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
    select e
    from CallOpeningEvent e
    where e.tenantId = :tenantId
      and e.deliveryStatus = 'PENDING'
      and (e.userId is null or e.userId = :userId)
    order by e.createdAt asc
""")
List<CallOpeningEvent> findPendingForTenantAndUser(
        @Param("tenantId") UUID tenantId,
        @Param("userId") UUID userId
);


}
