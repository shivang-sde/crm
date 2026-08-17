package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowEntityContextProvider {

    String entityType();

    Optional<Map<String, Object>> load(UUID tenantId, UUID entityId);
}