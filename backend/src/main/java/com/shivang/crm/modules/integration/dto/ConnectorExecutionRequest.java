package com.shivang.crm.modules.integration.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorExecutionRequest {

    private UUID tenantId;
    private UUID userId;
    private String providerKey;
    private UUID connectorInstanceId;
    private String actionKey;
    private String entityType;
    private UUID entityId;
    private Map<String, Object> entityData = new HashMap<>();
    private Map<String, Object> inputData = new HashMap<>();
}
