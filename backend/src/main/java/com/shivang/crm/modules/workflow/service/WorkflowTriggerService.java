package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.entity.WorkflowActorType;
import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowVersion;
import com.shivang.crm.modules.workflow.entity.WorkflowVersionStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowVersionRepository;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CausalEventContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowTriggerService {

    static final String ERROR_MAX_CHAIN_DEPTH = "WORKFLOW_MAX_CHAIN_DEPTH_EXCEEDED";
    static final String ERROR_SELF_TRIGGER_SUPPRESSED = "WORKFLOW_SELF_TRIGGER_SUPPRESSED";

    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final ObjectMapper objectMapper;
    private final WorkflowConditionEvaluator conditionEvaluator;
    private final WorkflowEntityContextProviderRegistry entityContextProviderRegistry;

    @Value("${app.workflow-runtime.max-chain-depth:${WORKFLOW_MAX_CHAIN_DEPTH:5}}")
    private int maxChainDepth;

    @Transactional
    public void process(CanonicalCrmEvent event) {
        List<WorkflowVersion> matches = workflowVersionRepository.findActiveMatches(
            event.tenantId(),
            event.entityType(),
            event.eventType(),
            WorkflowStatus.ACTIVE,
            WorkflowVersionStatus.ACTIVE
        );

        String triggerContext = serializeContext(event);
        UUID actorId = readActorId(event.metadata());
        WorkflowActorType actorType = readActorType(event.metadata());

        Lineage lineage = readLineage(event.metadata());

        for (WorkflowVersion version : matches) {
            // WF-38: optional trigger filter predicate (RUN WHEN) — reuses existing condition engine.
            // Null/empty = every legitimate event matches (backward compat). Evaluated before execution creation.
            if (!matchesTriggerFilter(event, version)) {
                log.info("Trigger filter did not match: workflow={} version={} event={} entity={}",
                    version.getWorkflow().getId(), version.getId(), event.eventId(), event.entityId());
                continue;
            }
            if (lineage != null) {
                // Self-recursion: a workflow never chains into itself.
                if (lineage.causedByWorkflowId().equals(version.getWorkflow().getId())) {
                    workflowExecutionRepository.insertRejectedIfAbsent(
                        event.tenantId(), version.getWorkflow().getId(), version.getId(),
                        event.eventId(), event.entityType(), event.entityId(), event.eventType(),
                        actorId, actorType == null ? null : actorType.name(), triggerContext,
                        lineage.causedByExecutionId(), event.eventId(), lineage.chainDepth(),
                        ERROR_SELF_TRIGGER_SUPPRESSED,
                        "Suppressed: this workflow caused the triggering event"
                    );
                    log.info("Suppressed self-triggered execution: workflow={} event={}",
                        version.getWorkflow().getId(), event.eventId());
                    continue;
                }

                int nextDepth = lineage.chainDepth() + 1;
                if (nextDepth > maxChainDepth) {
                    workflowExecutionRepository.insertRejectedIfAbsent(
                        event.tenantId(), version.getWorkflow().getId(), version.getId(),
                        event.eventId(), event.entityType(), event.entityId(), event.eventType(),
                        actorId, actorType == null ? null : actorType.name(), triggerContext,
                        lineage.causedByExecutionId(), event.eventId(), lineage.chainDepth(),
                        ERROR_MAX_CHAIN_DEPTH,
                        "Suppressed: workflow causal chain depth " + nextDepth
                            + " exceeds maximum of " + maxChainDepth
                    );
                    log.warn("Rejected workflow execution beyond max causal depth {}: workflow={} event={}",
                        maxChainDepth, version.getWorkflow().getId(), event.eventId());
                    continue;
                }

                workflowExecutionRepository.insertIfAbsent(
                    event.tenantId(),
                    version.getWorkflow().getId(),
                    version.getId(),
                    event.eventId(),
                    event.entityType(),
                    event.entityId(),
                    event.eventType(),
                    actorId,
                    actorType == null ? null : actorType.name(),
                    WorkflowExecutionStatus.PENDING.name(),
                    triggerContext,
                    lineage.causedByExecutionId(),
                    event.eventId(),
                    nextDepth
                );
            } else {
                // Root execution: caused directly by a domain/external event.
                workflowExecutionRepository.insertIfAbsent(
                    event.tenantId(),
                    version.getWorkflow().getId(),
                    version.getId(),
                    event.eventId(),
                    event.entityType(),
                    event.entityId(),
                    event.eventType(),
                    actorId,
                    actorType == null ? null : actorType.name(),
                    WorkflowExecutionStatus.PENDING.name(),
                    triggerContext,
                    null,
                    event.eventId(),
                    0
                );
            }
        }
    }

    private boolean matchesTriggerFilter(CanonicalCrmEvent event, WorkflowVersion version) {
        Map<String, Object> filter = version.getTriggerFilter();
        if (filter == null || filter.isEmpty()) return true;
        Object conditionsObj = filter.get("conditions");
        if (!(conditionsObj instanceof List<?> conditions) || conditions.isEmpty()) return true;
        String logic = filter.get("logic") == null ? "AND" : String.valueOf(filter.get("logic")).trim().toUpperCase();
        if (!"AND".equals(logic) && !"OR".equals(logic)) logic = "AND";

        Map<String, Object> rawTriggerContext = event.metadata() == null ? Map.of() : event.metadata();
        java.util.Map<String, Object> normalizedTriggerContext = new java.util.LinkedHashMap<>(rawTriggerContext);
        if (!normalizedTriggerContext.containsKey("createdVia")) {
            Object src = normalizedTriggerContext.get("source");
            if ("UNIVERSAL_LEAD_INGESTION".equals(src)) normalizedTriggerContext.put("createdVia", "LEAD_INGESTION");
            else if ("MANUAL".equals(src)) normalizedTriggerContext.put("createdVia", "MANUAL");
            else if (normalizedTriggerContext.containsKey("ingestionConfigId")) normalizedTriggerContext.put("createdVia", "LEAD_INGESTION");
            else normalizedTriggerContext.put("createdVia", "MANUAL");
        }
        normalizedTriggerContext.putIfAbsent("ingestionConfigId", "");
        normalizedTriggerContext.putIfAbsent("ingestionEventId", "");

        com.shivang.crm.modules.workflow.entity.WorkflowExecution dummyExec = new com.shivang.crm.modules.workflow.entity.WorkflowExecution();
        dummyExec.setTenantId(event.tenantId());
        dummyExec.setEntityType(event.entityType());
        dummyExec.setEntityId(event.entityId());
        dummyExec.setEventType(event.eventType());
        dummyExec.setTriggerEventId(event.eventId());
        dummyExec.setTriggerContext(normalizedTriggerContext);
        dummyExec.setActorId(readActorId(event.metadata()));
        try {
            Object at = event.metadata() == null ? null : event.metadata().get("actorType");
            if (at != null) dummyExec.setActorType(com.shivang.crm.modules.workflow.entity.WorkflowActorType.valueOf(String.valueOf(at).trim().toUpperCase()));
        } catch (Exception ignored) {}
        com.shivang.crm.modules.workflow.entity.Workflow dummyWorkflow = new com.shivang.crm.modules.workflow.entity.Workflow();
        dummyWorkflow.setId(version.getWorkflow().getId());
        dummyWorkflow.setTenantId(event.tenantId());
        com.shivang.crm.modules.workflow.entity.WorkflowVersion dummyVersion = new com.shivang.crm.modules.workflow.entity.WorkflowVersion();
        dummyVersion.setId(version.getId());
        dummyVersion.setTenantId(event.tenantId());
        dummyVersion.setWorkflow(dummyWorkflow);
        dummyExec.setWorkflow(dummyWorkflow);
        dummyExec.setWorkflowVersion(dummyVersion);
        dummyExec.setId(UUID.randomUUID());

        com.shivang.crm.modules.workflow.service.WorkflowExecutionContext ctx;
        try {
            ctx = new com.shivang.crm.modules.workflow.service.WorkflowExecutionContext(dummyExec, entityContextProviderRegistry);
        } catch (Exception ex) {
            log.warn("Trigger filter context creation failed for workflow {}: {}", version.getId(), ex.getMessage());
            return false;
        }

        boolean result = "AND".equals(logic);
        for (Object condObj : conditions) {
            if (!(condObj instanceof Map<?, ?> cond)) continue;
            boolean passed;
            try {
                passed = conditionEvaluator.evaluate(cond, ctx);
            } catch (com.shivang.crm.modules.workflow.service.WorkflowRuntimeException ex) {
                if ("WORKFLOW_CONDITION_FIELD_NOT_FOUND".equals(ex.getErrorCode()) || "WORKFLOW_CONDITION_VALUE_INVALID".equals(ex.getErrorCode())) {
                    log.debug("Trigger filter field not found for workflow {}: {}", version.getId(), ex.getMessage());
                    passed = false;
                } else {
                    throw ex;
                }
            }
            if ("AND".equals(logic)) {
                result = result && passed;
                if (!result) break;
            } else {
                result = result || passed;
                if (result) break;
            }
        }
        return result;
    }

    private record Lineage(UUID causedByExecutionId, UUID causedByWorkflowId, int chainDepth) {
    }

    private Lineage readLineage(Map<String, Object> metadata) {
        if (metadata == null) return null;
        Object executionId = metadata.get(CausalEventContext.METADATA_CAUSED_BY_EXECUTION_ID);
        Object workflowId = metadata.get(CausalEventContext.METADATA_CAUSED_BY_WORKFLOW_ID);
        Object depth = metadata.get(CausalEventContext.METADATA_CHAIN_DEPTH);
        if (executionId == null || workflowId == null) return null;
        try {
            return new Lineage(
                UUID.fromString(String.valueOf(executionId)),
                UUID.fromString(String.valueOf(workflowId)),
                depth == null ? 0 : Integer.parseInt(String.valueOf(depth))
            );
        } catch (IllegalArgumentException ex) {
            throw new WorkflowRuntimeException("WORKFLOW_LINEAGE_INVALID", "Canonical event workflow lineage is invalid");
        }
    }

    private UUID readActorId(java.util.Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get("actorId");
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            throw new WorkflowRuntimeException("WORKFLOW_ACTOR_INVALID", "Canonical event actorId is invalid");
        }
    }

    private WorkflowActorType readActorType(java.util.Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get("actorType");
        if (value == null) return null;
        try {
            return WorkflowActorType.valueOf(String.valueOf(value).trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new WorkflowRuntimeException("WORKFLOW_ACTOR_INVALID", "Canonical event actorType is invalid");
        }
    }

    private String serializeContext(CanonicalCrmEvent event) {
        try {
            return objectMapper.writeValueAsString(event.metadata() == null ? java.util.Map.of() : event.metadata());
        } catch (JacksonException ex) {
            throw new WorkflowRuntimeException("WORKFLOW_CONTEXT_SERIALIZATION_FAILED", "Unable to serialize canonical event metadata", ex);
        }
    }
}
