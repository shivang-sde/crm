package com.shivang.crm.modules.integration.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.integration.dto.ConnectorExecutionRequest;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionResult;
import com.shivang.crm.modules.integration.entity.ConnectorExecution;

public interface ConnectorExecutionService {
    ConnectorExecutionResult execute(ConnectorExecutionRequest request);
    ConnectorExecution save(ConnectorExecution connectorExecution);
    Optional<ConnectorExecution> findById(UUID id);
    List<ConnectorExecution> findByTenantId(UUID tenantId);
}
