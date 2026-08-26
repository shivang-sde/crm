package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.workflow.entity.WorkflowEdge;
import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;

/**
 * Evaluates configured conditions (same rule model as CONDITION) and selects
 * exactly one outgoing edge whose edge key matches the resulting outcome
 * (TRUE / FALSE). Falls back to nothing on no-match — the runtime error is
 * deterministic rather than silently choosing an edge.
 */
@Component
public class BranchNodeExecutor implements WorkflowNodeExecutor, WorkflowNodeExecutorRegistrationProvider {

    private static final Set<String> LOGICS = Set.of("AND", "OR");

    private final WorkflowConditionEvaluator conditionEvaluator;

    public BranchNodeExecutor(WorkflowConditionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    @Override
    public WorkflowNodeExecutionResult execute(
        WorkflowExecution execution,
        WorkflowNode node,
        List<WorkflowEdge> outgoingEdges,
        WorkflowExecutionContext context
    ) {
        Map<String, Object> configuration = node.getConfiguration();
        if (configuration == null) {
            throw branchFailure("WORKFLOW_BRANCH_INVALID", "Branch configuration is required");
        }

        String logic = keyword(configuration.get("logic"));
        if (!LOGICS.contains(logic)) {
            throw branchFailure("WORKFLOW_BRANCH_INVALID", "Branch logic must be AND or OR");
        }
        Object rawConditions = configuration.get("conditions");
        if (!(rawConditions instanceof List<?> conditions) || conditions.isEmpty()) {
            throw branchFailure("WORKFLOW_BRANCH_INVALID", "At least one branch condition is required");
        }

        List<Boolean> results = new java.util.ArrayList<>();
        for (Object rawCondition : conditions) {
            if (!(rawCondition instanceof Map<?, ?> condition)) {
                throw branchFailure("WORKFLOW_BRANCH_INVALID", "Each branch condition must be an object");
            }
            results.add(conditionEvaluator.evaluate(condition, context));
        }

        boolean outcomeBoolean = "AND".equals(logic)
            ? results.stream().allMatch(Boolean.TRUE::equals)
            : results.stream().anyMatch(Boolean.TRUE::equals);
        String outcome = outcomeBoolean ? "TRUE" : "FALSE";

        UUID selectedEdgeId = selectEdge(outgoingEdges, outcome);

        return new WorkflowNodeExecutionResult(
            com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.COMPLETED,
            Map.of("outcome", outcome, "selectedEdgeId", String.valueOf(selectedEdgeId)),
            List.of(selectedEdgeId),
            null,
            null
        );
    }

    private UUID selectEdge(List<WorkflowEdge> edges, String outcome) {
        WorkflowEdge selected = null;
        Set<String> seen = new java.util.HashSet<>();
        for (WorkflowEdge edge : edges) {
            String edgeKey = edge.getEdgeKey() == null ? "" : edge.getEdgeKey().trim().toUpperCase();
            if (!seen.add(edgeKey)) {
                throw branchFailure("WORKFLOW_BRANCH_INVALID", "Duplicate branch edge key: " + edgeKey);
            }
            if (outcome.equals(edgeKey)) selected = edge;
        }
        if (selected == null) {
            throw branchFailure("WORKFLOW_BRANCH_NO_MATCH",
                "No outgoing branch edge matches outcome " + outcome);
        }
        return selected.getId();
    }

    private String keyword(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase();
    }

    private WorkflowRuntimeException branchFailure(String code, String message) {
        return new WorkflowRuntimeException(code, message);
    }

    @Override
    public WorkflowNodeExecutorRegistration registration() {
        return new WorkflowNodeExecutorRegistration(WorkflowNodeType.BRANCH, this);
    }
}
