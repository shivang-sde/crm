package com.shivang.crm.modules.workflow.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.dto.WorkflowGraphValidationError;
import com.shivang.crm.modules.workflow.entity.WorkflowEdge;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;
import com.shivang.crm.modules.workflow.entity.WorkflowVersion;
import com.shivang.crm.modules.workflow.repository.WorkflowEdgeRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowVersionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowGraphValidationService {

    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final WorkflowEdgeRepository workflowEdgeRepository;

    @Transactional(readOnly = true)
    public List<WorkflowGraphValidationError> validate(UUID tenantId, UUID versionId) {
        WorkflowVersion version = workflowVersionRepository.findByIdAndTenantIdAndDeletedFalse(versionId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow version not found"));
        List<WorkflowNode> nodes = workflowNodeRepository.findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantId, versionId);
        List<WorkflowEdge> edges = workflowEdgeRepository.findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantId, versionId);
        List<WorkflowGraphValidationError> errors = new ArrayList<>();

        if (!tenantId.equals(version.getTenantId()) || version.getWorkflow() == null || !tenantId.equals(version.getWorkflow().getTenantId())) {
            errors.add(error("WORKFLOW_CROSS_TENANT_REFERENCE", "Workflow version and workflow must belong to the tenant"));
        }

        Map<UUID, WorkflowNode> nodesById = new HashMap<>();
        Map<String, WorkflowNode> nodesByKey = new HashMap<>();
        for (WorkflowNode node : nodes) {
            nodesById.put(node.getId(), node);
            if (nodesByKey.put(node.getNodeKey(), node) != null) {
                errors.add(error("WORKFLOW_DUPLICATE_NODE_KEY", "Node key is duplicated: " + node.getNodeKey()));
            }
            if (!tenantId.equals(node.getTenantId()) || node.getWorkflowVersion() == null
                || !versionId.equals(node.getWorkflowVersion().getId())) {
                errors.add(error("WORKFLOW_CROSS_VERSION_EDGE", "Node does not belong to the workflow version"));
            }
        }

        List<WorkflowNode> triggers = nodes.stream().filter(node -> node.getNodeType() == WorkflowNodeType.TRIGGER).toList();
        List<WorkflowNode> ends = nodes.stream().filter(node -> node.getNodeType() == WorkflowNodeType.END).toList();
        if (triggers.isEmpty()) {
            errors.add(error("WORKFLOW_TRIGGER_REQUIRED", "Exactly one TRIGGER node is required"));
        } else if (triggers.size() > 1) {
            errors.add(error("WORKFLOW_MULTIPLE_TRIGGERS", "Exactly one TRIGGER node is required"));
        }
        if (ends.isEmpty()) {
            errors.add(error("WORKFLOW_END_REQUIRED", "At least one END node is required"));
        }

        Map<UUID, Set<UUID>> outgoing = new HashMap<>();
        Map<UUID, Set<UUID>> incoming = new HashMap<>();
        for (WorkflowEdge edge : edges) {
            WorkflowNode source = edge.getSourceNode();
            WorkflowNode target = edge.getTargetNode();
            if (!tenantId.equals(edge.getTenantId()) || edge.getWorkflowVersion() == null
                || !versionId.equals(edge.getWorkflowVersion().getId())) {
                errors.add(error("WORKFLOW_CROSS_VERSION_EDGE", "Edge does not belong to the workflow version"));
            }
            if (source == null || target == null || !nodesById.containsKey(source.getId()) || !nodesById.containsKey(target.getId())) {
                errors.add(error("WORKFLOW_DANGLING_EDGE", "Edge references a missing node"));
                continue;
            }
            if (!tenantId.equals(source.getTenantId()) || !tenantId.equals(target.getTenantId())
                || source.getWorkflowVersion() == null || target.getWorkflowVersion() == null
                || !versionId.equals(source.getWorkflowVersion().getId()) || !versionId.equals(target.getWorkflowVersion().getId())) {
                errors.add(error("WORKFLOW_CROSS_TENANT_REFERENCE", "Edge nodes must belong to the same tenant and version"));
            }
            outgoing.computeIfAbsent(source.getId(), ignored -> new HashSet<>()).add(target.getId());
            incoming.computeIfAbsent(target.getId(), ignored -> new HashSet<>()).add(source.getId());
            if (source.getNodeType() == WorkflowNodeType.END) {
                errors.add(error("WORKFLOW_END_OUTGOING_EDGE", "END nodes cannot have outgoing edges"));
            }
        }

        for (WorkflowNode condition : nodes.stream().filter(node -> node.getNodeType() == WorkflowNodeType.CONDITION).toList()) {
            List<WorkflowEdge> conditionEdges = edges.stream()
                .filter(edge -> edge.getSourceNode() != null && condition.getId().equals(edge.getSourceNode().getId()))
                .toList();
            Set<String> outcomes = new HashSet<>();
            for (WorkflowEdge edge : conditionEdges) {
                Object rawOutcome = edge.getConfiguration() == null ? null : edge.getConfiguration().get("outcome");
                String outcome = rawOutcome == null ? "" : String.valueOf(rawOutcome).trim().toUpperCase();
                if (!Set.of("TRUE", "FALSE").contains(outcome)) {
                    errors.add(error("WORKFLOW_EDGE_INVALID", "CONDITION edges must use TRUE or FALSE outcomes"));
                } else if (!outcomes.add(outcome)) {
                    errors.add(error("WORKFLOW_EDGE_INVALID", "CONDITION cannot have duplicate " + outcome + " edges"));
                }
            }
            if (conditionEdges.size() != 2 || !outcomes.containsAll(Set.of("TRUE", "FALSE"))) {
                errors.add(error("WORKFLOW_EDGE_INVALID", "CONDITION requires exactly one TRUE and one FALSE edge"));
            }
        }

        for (WorkflowNode action : nodes.stream().filter(node -> node.getNodeType() == WorkflowNodeType.ACTION).toList()) {
            Map<String, Object> configuration = action.getConfiguration();
            Object actionType = configuration == null ? null : configuration.get("actionType");
            if (actionType == null || String.valueOf(actionType).isBlank()) {
                errors.add(error("WORKFLOW_ACTION_INVALID_CONFIG", "ACTION nodes require a non-blank actionType"));
            }
            if (configuration != null && configuration.containsKey("config")
                && !(configuration.get("config") instanceof Map<?, ?>)) {
                errors.add(error("WORKFLOW_ACTION_INVALID_CONFIG", "ACTION config must be an object"));
            }
            if ("UPDATE_ENTITY_FIELD".equalsIgnoreCase(String.valueOf(actionType))
                && configuration != null && configuration.get("config") instanceof Map<?, ?> actionConfig) {
                for (String required : List.of("entityType", "entityId", "field")) {
                    if (actionConfig.get(required) == null || String.valueOf(actionConfig.get(required)).isBlank()) {
                        errors.add(error("WORKFLOW_UPDATE_INVALID_CONFIG", "UPDATE_ENTITY_FIELD requires " + required));
                    }
                }
                if (!actionConfig.containsKey("value")) {
                    errors.add(error("WORKFLOW_UPDATE_INVALID_CONFIG", "UPDATE_ENTITY_FIELD requires value"));
                }
            }
            if ("ASSIGN_OWNER".equalsIgnoreCase(String.valueOf(actionType))
                && configuration != null && configuration.get("config") instanceof Map<?, ?> ownerConfig) {
                for (String required : List.of("entityType", "entityId", "ownerId")) {
                    if (ownerConfig.get(required) == null || String.valueOf(ownerConfig.get(required)).isBlank()) {
                        errors.add(error("WORKFLOW_ASSIGN_OWNER_INVALID_CONFIG", "ASSIGN_OWNER requires " + required));
                    }
                }
            }
            if ("CREATE_TASK".equalsIgnoreCase(String.valueOf(actionType))
                && configuration != null && configuration.get("config") instanceof Map<?, ?> taskConfig) {
                if (taskConfig.get("subject") == null || String.valueOf(taskConfig.get("subject")).isBlank()) {
                    errors.add(error("WORKFLOW_CREATE_TASK_SUBJECT_REQUIRED", "CREATE_TASK requires subject"));
                }
            }
            if ("CLICK_TO_CALL".equalsIgnoreCase(String.valueOf(actionType))
                && configuration != null && configuration.get("config") instanceof Map<?, ?> callConfig) {
                boolean hasPhone = callConfig.get("phoneNumber") != null && !String.valueOf(callConfig.get("phoneNumber")).isBlank();
                boolean hasEntityPair = callConfig.get("entityType") != null && !String.valueOf(callConfig.get("entityType")).isBlank()
                    && callConfig.get("entityId") != null && !String.valueOf(callConfig.get("entityId")).isBlank();
                if (!hasPhone && !hasEntityPair) {
                    errors.add(error("WORKFLOW_CLICK_TO_CALL_PHONE_REQUIRED", "CLICK_TO_CALL requires phoneNumber or entityType and entityId"));
                }
            }
            if ("HTTP_API".equalsIgnoreCase(String.valueOf(actionType))
                && configuration != null && configuration.get("config") instanceof Map<?, ?> httpConfig) {
                Object method = httpConfig.get("method");
                if (method == null || !Set.of("GET", "POST", "PUT", "PATCH", "DELETE")
                    .contains(String.valueOf(method).trim().toUpperCase())) {
                    errors.add(error("WORKFLOW_HTTP_API_INVALID_METHOD", "HTTP_API requires GET, POST, PUT, PATCH, or DELETE method"));
                }
                Object url = httpConfig.get("url");
                if (url == null || String.valueOf(url).isBlank()) {
                    errors.add(error("WORKFLOW_HTTP_API_URL_REQUIRED", "HTTP_API requires url"));
                }
                validateObjectField(httpConfig, "queryParams", "WORKFLOW_HTTP_API_INVALID_CONFIG", errors);
                validateObjectField(httpConfig, "headers", "WORKFLOW_HTTP_API_INVALID_CONFIG", errors);
                validateObjectField(httpConfig, "body", "WORKFLOW_HTTP_API_INVALID_CONFIG", errors);
                Object connectionId = httpConfig.get("connectionId");
                if (connectionId != null && !String.valueOf(connectionId).trim().startsWith("{{")) {
                    try { UUID.fromString(String.valueOf(connectionId)); }
                    catch (IllegalArgumentException ex) { errors.add(error("WORKFLOW_HTTP_API_INVALID_CONNECTION", "connectionId must be a UUID or runtime expression")); }
                }
            }
        }

        if (triggers.size() == 1) {
            WorkflowNode trigger = triggers.get(0);
            if (!incoming.getOrDefault(trigger.getId(), Set.of()).isEmpty()) {
                errors.add(error("WORKFLOW_TRIGGER_INCOMING_EDGE", "TRIGGER nodes cannot have incoming edges"));
            }

            Set<UUID> reachable = traverse(trigger.getId(), outgoing);
            for (WorkflowNode node : nodes) {
                if (!reachable.contains(node.getId())) {
                    errors.add(error("WORKFLOW_UNREACHABLE_NODE", "Node is not reachable from the TRIGGER: " + node.getNodeKey()));
                }
                if (node.getNodeType() != WorkflowNodeType.END && outgoing.getOrDefault(node.getId(), Set.of()).isEmpty()) {
                    errors.add(error("WORKFLOW_DEAD_END", "Non-END node has no outgoing edge: " + node.getNodeKey()));
                }
            }

            Set<UUID> canReachEnd = reverseTraverse(ends, incoming);
            for (UUID nodeId : reachable) {
                if (!canReachEnd.contains(nodeId)) {
                    errors.add(error("WORKFLOW_END_UNREACHABLE", "Node cannot reach an END node"));
                }
            }
        }

        if (triggers.size() == 1) {
            validateTriggerConfiguration(version, triggers.get(0), errors);
        }
        return errors;
    }

    private void validateTriggerConfiguration(WorkflowVersion version, WorkflowNode trigger, List<WorkflowGraphValidationError> errors) {
        Map<String, Object> configuration = trigger.getConfiguration();
        if (configuration == null) {
            errors.add(error("WORKFLOW_TRIGGER_MISMATCH", "TRIGGER configuration must contain entityType and eventType"));
            return;
        }
        String entityType = String.valueOf(configuration.get("entityType"));
        String eventType = String.valueOf(configuration.get("eventType"));
        if (!version.getTriggerEntityType().equals(entityType) || !version.getTriggerEventType().equals(eventType)) {
            errors.add(error("WORKFLOW_TRIGGER_MISMATCH", "TRIGGER configuration must match workflow version trigger fields"));
        }
    }

    private Set<UUID> traverse(UUID start, Map<UUID, Set<UUID>> graph) {
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            UUID current = queue.removeFirst();
            if (!visited.add(current)) continue;
            queue.addAll(graph.getOrDefault(current, Set.of()));
        }
        return visited;
    }

    private Set<UUID> reverseTraverse(List<WorkflowNode> ends, Map<UUID, Set<UUID>> incoming) {
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        ends.forEach(end -> queue.add(end.getId()));
        while (!queue.isEmpty()) {
            UUID current = queue.removeFirst();
            if (!visited.add(current)) continue;
            queue.addAll(incoming.getOrDefault(current, Set.of()));
        }
        return visited;
    }

    private WorkflowGraphValidationError error(String code, String message) {
        return new WorkflowGraphValidationError(code, message);
    }

    private void validateObjectField(Map<?, ?> configuration, String field, String code, List<WorkflowGraphValidationError> errors) {
        if (configuration.containsKey(field) && configuration.get(field) != null && !(configuration.get(field) instanceof Map<?, ?>)) {
            errors.add(error(code, "HTTP_API " + field + " must be an object"));
        }
    }
}