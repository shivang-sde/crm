package com.shivang.crm.modules.workflow.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.shivang.crm.modules.workflow.entity.WorkflowExecution;

import lombok.Getter;

@Getter
public class WorkflowExecutionContext {

    private final WorkflowExecution execution;
    private final WorkflowExecutionIdentity identity;
    private final Map<String, Object> trigger;
    private final Map<String, Object> triggerContext;
    private volatile Map<String, Object> entity;
    private final Map<String, Map<String, Object>> nodeOutputs = new HashMap<>();
    private UUID workflowNodeExecutionId;
    private final WorkflowEntityContextProviderRegistry entityContextProviderRegistry;
    // Execution-only credential map for {{credential.*}} templating — never persisted, cleared after node
    private volatile Map<String, Object> credentialContext = Map.of();

    public WorkflowExecutionContext(
        WorkflowExecution execution,
        WorkflowEntityContextProviderRegistry entityContextProviderRegistry
    ) {
        this.execution = execution;
        this.entityContextProviderRegistry = entityContextProviderRegistry;
        this.identity = new WorkflowExecutionIdentity(
            execution.getTenantId(), execution.getActorId(), execution.getActorType()
        );
        this.triggerContext = execution.getTriggerContext() == null
            ? Map.of()
            : Map.copyOf(execution.getTriggerContext());
        Map<String, Object> triggerData = new LinkedHashMap<>();
        triggerData.put("eventId", execution.getTriggerEventId());
        triggerData.put("tenantId", execution.getTenantId());
        triggerData.put("entityType", execution.getEntityType());
        triggerData.put("entityId", execution.getEntityId());
        triggerData.put("eventType", execution.getEventType());
        triggerData.put("actorId", execution.getActorId());
        triggerData.put("actorType", execution.getActorType());
        triggerData.put("metadata", this.triggerContext);
        this.trigger = Map.copyOf(triggerData);
        this.entity = loadEntity();
    }

    /**
     * Reloads the triggering entity through its read-only context provider.
     * Mutating actions (UPDATE_ENTITY_FIELD / ASSIGN_OWNER) call this so later
     * CONDITION / BRANCH nodes in the same run observe the committed state
     * instead of the execution-start snapshot.
     */
    public void refreshEntity() {
        this.entity = loadEntity();
    }

    private Map<String, Object> loadEntity() {
        return entityContextProviderRegistry
            .load(execution.getTenantId(), execution.getEntityType(), execution.getEntityId())
            .orElseGet(Map::of);
    }

    public void recordNodeOutput(String nodeKey, Map<String, Object> output) {
        nodeOutputs.put(
            nodeKey,
            output == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(output))
        );
    }

    public void setWorkflowNodeExecutionId(UUID workflowNodeExecutionId) {
        this.workflowNodeExecutionId = workflowNodeExecutionId;
    }

    public Map<String, Object> getCredentialContext() {
        return credentialContext;
    }

    public void setCredentialContext(Map<String, Object> credentialContext) {
        this.credentialContext = credentialContext == null ? Map.of() : Map.copyOf(credentialContext);
    }

    public void clearCredentialContext() {
        this.credentialContext = Map.of();
    }
}