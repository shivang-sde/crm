package com.shivang.crm.modules.dialer.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shivang.crm.modules.dialer.entity.CallOpeningEvent;

public interface CallOpeningEventRepository extends JpaRepository<CallOpeningEvent, UUID> {
    List<CallOpeningEvent> findByTenantIdAndDeliveryStatus(UUID tenantId, String deliveryStatus);
    List<CallOpeningEvent> findByTenantIdAndAgentIdAndDeliveryStatus(UUID tenantId, String agentId, String deliveryStatus);
}
