package com.shivang.crm.modules.dialer.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.dialer.entity.CallConnectTrigger;

@Repository
public interface CallConnectTriggerRepository extends JpaRepository<CallConnectTrigger, UUID> {
    List<CallConnectTrigger> findByTenantId(UUID tenantId);
}
