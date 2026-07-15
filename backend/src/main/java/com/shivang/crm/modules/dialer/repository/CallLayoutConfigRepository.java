package com.shivang.crm.modules.dialer.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.dialer.entity.CallLayoutConfig;

@Repository
public interface CallLayoutConfigRepository extends JpaRepository<CallLayoutConfig, UUID> {
    List<CallLayoutConfig> findByTenantId(UUID tenantId);
}
