package com.shivang.crm.modules.workflow.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.workflow.entity.WorkflowEdge;
import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;
import com.shivang.crm.modules.workflow.repository.WorkflowEdgeRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeExecutionRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowGraphRuntimeService {

    private final WorkflowNodeRepository workflowNodeRepository;
    private final WorkflowEdgeRepository workflowEdgeRepository;
    private final WorkflowNodeExecutionRepository workflowNodeExecutionRepository;
    private final WorkflowNodeExecutorRegistry workflowNodeExecutorRegistry;
    private final WorkflowEntityContextProviderRegistry workflowEntityContextProviderRegistry;

    public void execute(WorkflowExecution execution) {
        UUID tenantId = execution.getTenantId();
        UUID versionId = execution.getWorkflowVersion().getId();

        List<WorkflowNode> nodes = workflowNodeRepository
            .findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantId, versionId);
        List<WorkflowEdge> edges = workflowEdgeRepository
            .findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantId, versionId);

        Map<UUID, WorkflowNode> nodesById = new HashMap<>();
        Map<UUID, List<WorkflowEdge>> outgoing = new HashMap<>();
        for (WorkflowNode node : nodes) {
            if (node.getWorkflowVersion() == null || !versionId.equals(node.getWorkflowVersion().getId())
                || !tenantId.equals(node.getTenantId())) {
                throw runtimeFailure("WORKFLOW_GRAPH_INVALID", "Workflow node does not belong to the execution tenant/version");
            }
            nodesById.put(node.getId(), node);
        }

        for (WorkflowEdge edge : edges) {
            if (edge.getWorkflowVersion() == null || !versionId.equals(edge.getWorkflowVersion().getId())
                || !tenantId.equals(edge.getTenantId()) || edge.getSourceNode() == null || edge.getTargetNode() == null
                || !nodesById.containsKey(edge.getSourceNode().getId()) || !nodesById.containsKey(edge.getTargetNode().getId())) {
                throw runtimeFailure("WORKFLOW_EDGE_INVALID", "Workflow edge does not belong to the execution graph");
            }
            outgoing.computeIfAbsent(edge.getSourceNode().getId(), ignored -> new java.util.ArrayList<>()).add(edge);
        }

        List<WorkflowNode> triggers = nodes.stream()
            .filter(node -> node.getNodeType() == WorkflowNodeType.TRIGGER)
            .toList();
        if (triggers.size() != 1) {
            throw runtimeFailure("WORKFLOW_TRIGGER_NOT_FOUND", "Workflow execution requires exactly one TRIGGER node");
        }

        Set<UUID> visited = new HashSet<>();
        WorkflowExecutionContext context = new WorkflowExecutionContext(execution, workflowEntityContextProviderRegistry);
        WorkflowNode current = triggers.get(0);
        while (true) {
            if (!visited.add(current.getId())) {
                throw runtimeFailure("WORKFLOW_GRAPH_INVALID", "Workflow graph revisited a node without loop semantics");
            }

            WorkflowNodeExecutionResult result = executeNode(execution, current, outgoing.getOrDefault(current.getId(), List.of()), context);
            context.recordNodeOutput(current.getNodeKey(), result.outputContext());
            if (current.getNodeType() == WorkflowNodeType.END) {
                return;
            }

            List<WorkflowEdge> nextEdges = outgoing.getOrDefault(current.getId(), List.of());
            if (nextEdges.isEmpty()) {
                throw runtimeFailure("WORKFLOW_NODE_NOT_FOUND", "Non-END workflow node has no outgoing edge");
            }

            List<UUID> selectedEdgeIds = result.selectedEdgeIds();
            if (selectedEdgeIds.size() != 1) {
                throw runtimeFailure("WORKFLOW_EDGE_INVALID", "Node executor did not select exactly one outgoing edge");
            }
            WorkflowEdge selectedEdge = nextEdges.stream()
                .filter(edge -> selectedEdgeIds.contains(edge.getId()))
                .findFirst()
                .orElseThrow(() -> runtimeFailure("WORKFLOW_EDGE_INVALID", "Node executor selected an invalid outgoing edge"));
            current = nodesById.get(selectedEdge.getTargetNode().getId());
            if (current == null) {
                throw runtimeFailure("WORKFLOW_NODE_NOT_FOUND", "Workflow edge target node was not found");
            }
        }
    }

    private WorkflowNodeExecutionResult executeNode(WorkflowExecution execution, WorkflowNode node, List<WorkflowEdge> outgoingEdges, WorkflowExecutionContext context) {
        com.shivang.crm.modules.workflow.entity.WorkflowNodeExecution nodeExecution = workflowNodeExecutionRepository
            .findByWorkflowExecutionIdAndWorkflowNodeIdAndDeletedFalse(execution.getId(), node.getId())
            .orElseGet(() -> com.shivang.crm.modules.workflow.entity.WorkflowNodeExecution.builder()
                .tenantId(execution.getTenantId())
                .workflowExecution(execution)
                .workflowNode(node)
                .nodeKey(node.getNodeKey())
                .nodeType(node.getNodeType())
                .inputContext(execution.getTriggerContext())
                .build());

        if (nodeExecution.getStatus() == com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.COMPLETED) {
            Map<String, Object> output = nodeExecution.getOutputContext() == null ? Map.of() : nodeExecution.getOutputContext();
            Object selectedEdgeId = output.get("selectedEdgeId");
            List<UUID> selectedEdges = selectedEdgeId == null ? List.of() : List.of(UUID.fromString(String.valueOf(selectedEdgeId)));
            return new WorkflowNodeExecutionResult(nodeExecution.getStatus(), output, selectedEdges, null, null);
        }

        WorkflowNodeExecutionResult result;
        try {
            nodeExecution.setStatus(com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.RUNNING);
            nodeExecution.setStartedAt(java.time.Instant.now());
            workflowNodeExecutionRepository.save(nodeExecution);
            WorkflowNodeExecutor executor = workflowNodeExecutorRegistry.get(node.getNodeType());
            result = executor.execute(execution, node, outgoingEdges, context);
            nodeExecution.setStatus(result.status());
            Map<String, Object> outputContext = new HashMap<>(result.outputContext() == null ? Map.of() : result.outputContext());
            if (result.selectedEdgeIds().size() == 1) {
                outputContext.put("selectedEdgeId", result.selectedEdgeIds().get(0).toString());
            }
            nodeExecution.setOutputContext(outputContext);
            nodeExecution.setCompletedAt(java.time.Instant.now());
            nodeExecution.setErrorCode(result.errorCode());
            nodeExecution.setErrorMessage(result.errorMessage());
        } catch (WorkflowRuntimeException ex) {
            nodeExecution.setStatus(com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.FAILED);
            nodeExecution.setErrorCode(ex.getErrorCode());
            nodeExecution.setErrorMessage(ex.getMessage());
            nodeExecution.setCompletedAt(java.time.Instant.now());
            workflowNodeExecutionRepository.save(nodeExecution);
            throw ex;
        }
        workflowNodeExecutionRepository.save(nodeExecution);
        return result;
    }

    private WorkflowRuntimeException runtimeFailure(String errorCode, String message) {
        return new WorkflowRuntimeException(errorCode, message);
    }
}