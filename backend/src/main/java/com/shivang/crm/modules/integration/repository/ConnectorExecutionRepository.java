package com.shivang.crm.modules.integration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.integration.entity.ConnectorExecution;

@Repository
public interface ConnectorExecutionRepository extends JpaRepository<ConnectorExecution, UUID> {
    List<ConnectorExecution> findByTenantId(UUID tenantId);
}
