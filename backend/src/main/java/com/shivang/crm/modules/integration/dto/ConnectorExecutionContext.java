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
public class ConnectorExecutionContext {

    private UUID tenantId;
    private UUID userId;
    private String providerKey;
    private String actionKey;
    private UUID connectorInstanceId;
    private String entityType;
    private UUID entityId;
    private Map<String, Object> entity = new HashMap<>();
    private Map<String, Object> user = new HashMap<>();
    private Map<String, Object> tenant = new HashMap<>();
    private Map<String, Object> credential = new HashMap<>();
    private Map<String, Object> input = new HashMap<>();
    private Map<String, Object> requestMetadata = new HashMap<>();

    public Map<String, Object> toTemplateContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("tenant", tenant);
        context.put("user", user);
        context.put("entity", entity);
        context.put("credential", credential);
        context.put("input", input);
        context.put("requestMetadata", requestMetadata);
        context.put("providerKey", providerKey);
        context.put("actionKey", actionKey);
        context.put("connectorInstanceId", connectorInstanceId);
        context.put("entityType", entityType);
        context.put("entityId", entityId);
        return context;
    }
}
