package com.shivang.crm.modules.workflow.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
                errors.add(errorForNode("WORKFLOW_DUPLICATE_NODE_KEY", "Node key is duplicated: " + node.getNodeKey(), node));
            }
            if (!tenantId.equals(node.getTenantId()) || node.getWorkflowVersion() == null
                || !versionId.equals(node.getWorkflowVersion().getId())) {
                errors.add(errorForNode("WORKFLOW_CROSS_VERSION_EDGE", "Node does not belong to the workflow version", node));
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
                errors.add(errorForEdge("WORKFLOW_CROSS_VERSION_EDGE", "Edge does not belong to the workflow version", edge));
            }
            if (source == null || target == null || !nodesById.containsKey(source.getId()) || !nodesById.containsKey(target.getId())) {
                errors.add(errorForEdge("WORKFLOW_DANGLING_EDGE", "Edge references a missing node", edge));
                continue;
            }
            if (!tenantId.equals(source.getTenantId()) || !tenantId.equals(target.getTenantId())
                || source.getWorkflowVersion() == null || target.getWorkflowVersion() == null
                || !versionId.equals(source.getWorkflowVersion().getId()) || !versionId.equals(target.getWorkflowVersion().getId())) {
                errors.add(errorForEdge("WORKFLOW_CROSS_TENANT_REFERENCE", "Edge nodes must belong to the same tenant and version", edge));
            }
            outgoing.computeIfAbsent(source.getId(), ignored -> new HashSet<>()).add(target.getId());
            incoming.computeIfAbsent(target.getId(), ignored -> new HashSet<>()).add(source.getId());
            if (source.getNodeType() == WorkflowNodeType.END) {
                errors.add(errorForNodeEdge("WORKFLOW_END_OUTGOING_EDGE", "END nodes cannot have outgoing edges", source, edge));
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
                    errors.add(errorForNodeEdge("WORKFLOW_EDGE_INVALID", "CONDITION edges must use TRUE or FALSE outcomes", condition, edge));
                } else if (!outcomes.add(outcome)) {
                    errors.add(errorForNodeEdge("WORKFLOW_EDGE_INVALID", "CONDITION cannot have duplicate " + outcome + " edges", condition, edge));
                }
            }
            if (conditionEdges.size() != 2 || !outcomes.containsAll(Set.of("TRUE", "FALSE"))) {
                errors.add(errorForNode("WORKFLOW_EDGE_INVALID", "CONDITION requires exactly one TRUE and one FALSE edge", condition));
            }
        }

        for (WorkflowNode wait : nodes.stream().filter(node -> node.getNodeType() == WorkflowNodeType.WAIT).toList()) {
            List<WorkflowEdge> waitEdges = edges.stream()
                .filter(edge -> edge.getSourceNode() != null && wait.getId().equals(edge.getSourceNode().getId()))
                .toList();
            if (waitEdges.size() != 1) {
                errors.add(errorForNode("WORKFLOW_EDGE_INVALID", "WAIT requires exactly one outgoing edge", wait));
            }

            Map<String, Object> configuration = wait.getConfiguration();
            Object waitType = configuration == null ? null : configuration.get("waitType");
            boolean isDuration = "DURATION".equalsIgnoreCase(String.valueOf(waitType));
            if (isDuration) {
                Object amountObj = configuration.get("amount");
                Object unitObj = configuration.get("unit");
                if (amountObj == null || unitObj == null || String.valueOf(amountObj).isBlank() || String.valueOf(unitObj).isBlank()) {
                    errors.add(errorForNode("WORKFLOW_WAIT_RESUME_AT_REQUIRED", "WAIT duration requires amount and unit", wait));
                } else {
                    try {
                        long amount = Long.parseLong(String.valueOf(amountObj).trim());
                        if (amount <= 0) errors.add(errorForNode("WORKFLOW_WAIT_RESUME_AT_INVALID", "Wait amount must be positive", wait));
                        String unit = String.valueOf(unitObj).trim().toUpperCase();
                        if (!java.util.Set.of("MINUTES","MINUTE","M","HOURS","HOUR","H","DAYS","DAY","D").contains(unit)) {
                            errors.add(errorForNode("WORKFLOW_WAIT_RESUME_AT_INVALID", "Unsupported wait unit: " + unit, wait));
                        }
                    } catch (NumberFormatException ex) {
                        errors.add(errorForNode("WORKFLOW_WAIT_RESUME_AT_INVALID", "Invalid wait amount", wait));
                    }
                }
            } else {
                Object rawResumeAt = configuration == null ? null : configuration.get("resumeAt");
                if (rawResumeAt == null || String.valueOf(rawResumeAt).isBlank()) {
                    errors.add(errorForNode("WORKFLOW_WAIT_RESUME_AT_REQUIRED", "WAIT nodes require a resumeAt timestamp", wait));
                } else {
                    try {
                        Instant resumeAt = Instant.parse(String.valueOf(rawResumeAt).trim());
                        if (!resumeAt.isAfter(Instant.now())) {
                            errors.add(errorForNode("WORKFLOW_WAIT_RESUME_AT_PAST",
                                "WAIT resumeAt must be in the future", wait));
                        }
                    } catch (DateTimeParseException parseEx) {
                        errors.add(errorForNode("WORKFLOW_WAIT_RESUME_AT_INVALID",
                            "WAIT resumeAt must be an ISO-8601 UTC timestamp, e.g. 2026-08-30T10:30:00Z", wait));
                    }
                }
            }
        }

        for (WorkflowNode branch : nodes.stream().filter(node -> node.getNodeType() == WorkflowNodeType.BRANCH).toList()) {
            List<WorkflowEdge> branchEdges = edges.stream()
                .filter(edge -> edge.getSourceNode() != null && branch.getId().equals(edge.getSourceNode().getId()))
                .toList();

            Set<String> keys = new HashSet<>();
            for (WorkflowEdge edge : branchEdges) {
                String edgeKey = edge.getEdgeKey() == null ? "" : edge.getEdgeKey().trim().toUpperCase();
                if (!Set.of("TRUE", "FALSE").contains(edgeKey)) {
                    errors.add(errorForNodeEdge("WORKFLOW_BRANCH_INVALID", "BRANCH edges must use TRUE or FALSE edge keys", branch, edge));
                } else if (!keys.add(edgeKey)) {
                    errors.add(errorForNodeEdge("WORKFLOW_BRANCH_INVALID", "BRANCH cannot have duplicate " + edgeKey + " edges", branch, edge));
                }
            }
            if (branchEdges.size() != 2 || !keys.containsAll(Set.of("TRUE", "FALSE"))) {
                errors.add(errorForNode("WORKFLOW_BRANCH_INVALID", "BRANCH requires exactly one TRUE and one FALSE edge", branch));
            }

            Map<String, Object> configuration = branch.getConfiguration();
            Object rawLogic = configuration == null ? null : configuration.get("logic");
            if (rawLogic == null
                || !Set.of("AND", "OR").contains(String.valueOf(rawLogic).trim().toUpperCase())) {
                errors.add(errorForNode("WORKFLOW_BRANCH_INVALID", "Branch logic must be AND or OR", branch));
            }
            Object rawConditions = configuration == null ? null : configuration.get("conditions");
            if (!(rawConditions instanceof List<?> conditions) || conditions.isEmpty()) {
                errors.add(errorForNode("WORKFLOW_BRANCH_INVALID", "At least one branch condition is required", branch));
            } else {
                for (Object rawCondition : conditions) {
                    if (!(rawCondition instanceof Map<?, ?> condition)) {
                        errors.add(errorForNode("WORKFLOW_BRANCH_INVALID", "Each branch condition must be an object", branch));
                        continue;
                    }
                    Object field = condition.get("field");
                    if (field == null || String.valueOf(field).isBlank()) {
                        errors.add(errorForNode("WORKFLOW_BRANCH_INVALID", "Branch condition field is required", branch));
                    }
                    Object operator = condition.get("operator");
                    if (operator == null || !WorkflowConditionEvaluator.SUPPORTED_OPERATORS.contains(
                        String.valueOf(operator).trim().toUpperCase())) {
                        errors.add(errorForNode("WORKFLOW_BRANCH_INVALID",
                            "Unsupported branch condition operator: " + operator, branch));
                    }
                }
            }
        }

        for (WorkflowNode action : nodes.stream().filter(node -> node.getNodeType() == WorkflowNodeType.ACTION).toList()) {
            Map<String, Object> configuration = action.getConfiguration();
            Object actionType = configuration == null ? null : configuration.get("actionType");
            if (actionType == null || String.valueOf(actionType).isBlank()) {
                errors.add(errorForNode("WORKFLOW_ACTION_INVALID_CONFIG", "ACTION nodes require a non-blank actionType", action));
            }
            if (configuration != null && configuration.containsKey("config")
                && !(configuration.get("config") instanceof Map<?, ?>)) {
                errors.add(errorForNode("WORKFLOW_ACTION_INVALID_CONFIG", "ACTION config must be an object", action));
            }
            if ("UPDATE_ENTITY_FIELD".equalsIgnoreCase(String.valueOf(actionType))
                && configuration != null && configuration.get("config") instanceof Map<?, ?> actionConfig) {
                for (String required : List.of("entityType", "entityId", "field")) {
                    if (actionConfig.get(required) == null || String.valueOf(actionConfig.get(required)).isBlank()) {
                        errors.add(errorForNode("WORKFLOW_UPDATE_INVALID_CONFIG", "UPDATE_ENTITY_FIELD requires " + required, action));
                    }
                }
                if (!actionConfig.containsKey("value")) {
                    errors.add(errorForNode("WORKFLOW_UPDATE_INVALID_CONFIG", "UPDATE_ENTITY_FIELD requires value", action));
                }
            }
            if ("ASSIGN_OWNER".equalsIgnoreCase(String.valueOf(actionType))
                && configuration != null && configuration.get("config") instanceof Map<?, ?> ownerConfig) {
                for (String required : List.of("entityType", "entityId", "ownerId")) {
                    if (ownerConfig.get(required) == null || String.valueOf(ownerConfig.get(required)).isBlank()) {
                        errors.add(errorForNode("WORKFLOW_ASSIGN_OWNER_INVALID_CONFIG", "ASSIGN_OWNER requires " + required, action));
                    }
                }
            }
            if ("CREATE_TASK".equalsIgnoreCase(String.valueOf(actionType))
                && configuration != null && configuration.get("config") instanceof Map<?, ?> taskConfig) {
                if (taskConfig.get("subject") == null || String.valueOf(taskConfig.get("subject")).isBlank()) {
                    errors.add(errorForNode("WORKFLOW_CREATE_TASK_SUBJECT_REQUIRED", "CREATE_TASK requires subject", action));
                }
            }
            if ("CLICK_TO_CALL".equalsIgnoreCase(String.valueOf(actionType))
                && configuration != null && configuration.get("config") instanceof Map<?, ?> callConfig) {
                boolean hasPhone = callConfig.get("phoneNumber") != null && !String.valueOf(callConfig.get("phoneNumber")).isBlank();
                boolean hasEntityPair = callConfig.get("entityType") != null && !String.valueOf(callConfig.get("entityType")).isBlank()
                    && callConfig.get("entityId") != null && !String.valueOf(callConfig.get("entityId")).isBlank();
                if (!hasPhone && !hasEntityPair) {
                    errors.add(errorForNode("WORKFLOW_CLICK_TO_CALL_PHONE_REQUIRED", "CLICK_TO_CALL requires phoneNumber or entityType and entityId", action));
                }
            }
            if ("HTTP_API".equalsIgnoreCase(String.valueOf(actionType))
                && configuration != null && configuration.get("config") instanceof Map<?, ?> httpConfig) {
                Object method = httpConfig.get("method");
                if (method == null || !Set.of("GET", "POST", "PUT", "PATCH", "DELETE")
                    .contains(String.valueOf(method).trim().toUpperCase())) {
                    errors.add(errorForNode("WORKFLOW_HTTP_API_INVALID_METHOD", "HTTP_API requires GET, POST, PUT, PATCH, or DELETE method", action));
                }
                Object url = httpConfig.get("url");
                if (url == null || String.valueOf(url).isBlank()) {
                    errors.add(errorForNode("WORKFLOW_HTTP_API_URL_REQUIRED", "HTTP_API requires url", action));
                }
                validateObjectFieldForNode(httpConfig, "queryParams", "WORKFLOW_HTTP_API_INVALID_CONFIG", errors, action);
                validateObjectFieldForNode(httpConfig, "headers", "WORKFLOW_HTTP_API_INVALID_CONFIG", errors, action);
                validateObjectFieldForNode(httpConfig, "body", "WORKFLOW_HTTP_API_INVALID_CONFIG", errors, action);
                Object connectionId = httpConfig.get("connectionId");
                if (connectionId != null && !String.valueOf(connectionId).trim().startsWith("{{")) {
                    try { UUID.fromString(String.valueOf(connectionId)); }
                    catch (IllegalArgumentException ex) { errors.add(errorForNode("WORKFLOW_HTTP_API_INVALID_CONNECTION", "connectionId must be a UUID or runtime expression", action)); }
                }
            }
        }

        if (triggers.size() == 1) {
            WorkflowNode trigger = triggers.get(0);
            if (!incoming.getOrDefault(trigger.getId(), Set.of()).isEmpty()) {
                errors.add(errorForNode("WORKFLOW_TRIGGER_INCOMING_EDGE", "TRIGGER nodes cannot have incoming edges", trigger));
            }

            Set<UUID> reachable = traverse(trigger.getId(), outgoing);
            for (WorkflowNode node : nodes) {
                if (!reachable.contains(node.getId())) {
                    errors.add(errorForNode("WORKFLOW_UNREACHABLE_NODE", "Node is not reachable from the TRIGGER: " + node.getNodeKey(), node));
                }
                if (node.getNodeType() != WorkflowNodeType.END && outgoing.getOrDefault(node.getId(), Set.of()).isEmpty()) {
                    errors.add(errorForNode("WORKFLOW_DEAD_END", "Non-END node has no outgoing edge: " + node.getNodeKey(), node));
                }
            }

            Set<UUID> canReachEnd = reverseTraverse(ends, incoming);
            for (UUID nodeId : reachable) {
                if (!canReachEnd.contains(nodeId)) {
                    WorkflowNode unreachable = nodesById.get(nodeId);
                    errors.add(errorForNode("WORKFLOW_END_UNREACHABLE", "Node cannot reach an END node" + (unreachable != null ? ": " + unreachable.getNodeKey() : ""), unreachable));
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
            errors.add(errorForNode("WORKFLOW_TRIGGER_MISMATCH", "TRIGGER configuration must contain entityType and eventType", trigger));
            return;
        }
        String entityType = String.valueOf(configuration.get("entityType"));
        String eventType = String.valueOf(configuration.get("eventType"));
        if (!version.getTriggerEntityType().equals(entityType) || !version.getTriggerEventType().equals(eventType)) {
            errors.add(errorForNode("WORKFLOW_TRIGGER_MISMATCH", "TRIGGER configuration must match workflow version trigger fields", trigger));
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
        return new WorkflowGraphValidationError(code, message, null, null, null);
    }

    private WorkflowGraphValidationError errorForNode(String code, String message, WorkflowNode node) {
        if (node == null) return error(code, message);
        return new WorkflowGraphValidationError(code, message, node.getId(), node.getNodeKey(), null);
    }

    private WorkflowGraphValidationError errorForEdge(String code, String message, WorkflowEdge edge) {
        if (edge == null) return error(code, message);
        return new WorkflowGraphValidationError(code, message, null, null, edge.getId());
    }

    private WorkflowGraphValidationError errorForNodeEdge(String code, String message, WorkflowNode node, WorkflowEdge edge) {
        UUID nodeId = node != null ? node.getId() : null;
        String nodeKey = node != null ? node.getNodeKey() : null;
        UUID edgeId = edge != null ? edge.getId() : null;
        return new WorkflowGraphValidationError(code, message, nodeId, nodeKey, edgeId);
    }

    private void validateObjectField(Map<?, ?> configuration, String field, String code, List<WorkflowGraphValidationError> errors) {
        if (configuration.containsKey(field) && configuration.get(field) != null && !(configuration.get(field) instanceof Map<?, ?>)) {
            errors.add(error(code, "HTTP_API " + field + " must be an object"));
        }
    }

    private void validateObjectFieldForNode(Map<?, ?> configuration, String field, String code, List<WorkflowGraphValidationError> errors, WorkflowNode node) {
        if (configuration.containsKey(field) && configuration.get(field) != null && !(configuration.get(field) instanceof Map<?, ?>)) {
            errors.add(errorForNode(code, "HTTP_API " + field + " must be an object", node));
        }
    }
}