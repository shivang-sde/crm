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

@Component
public class ConditionNodeExecutor implements WorkflowNodeExecutor, WorkflowNodeExecutorRegistrationProvider {

    private static final Set<String> LOGICS = Set.of("AND", "OR");

    private final WorkflowConditionEvaluator conditionEvaluator;

    public ConditionNodeExecutor(WorkflowConditionEvaluator conditionEvaluator) {
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
            throw conditionFailure("WORKFLOW_CONDITION_CONFIG_INVALID", "Condition configuration is required");
        }

        String logic = keyword(configuration.get("logic"));
        if (!LOGICS.contains(logic)) {
            throw conditionFailure("WORKFLOW_CONDITION_CONFIG_INVALID", "Condition logic must be AND or OR");
        }
        Object rawConditions = configuration.get("conditions");
        if (!(rawConditions instanceof List<?> conditions) || conditions.isEmpty()) {
            throw conditionFailure("WORKFLOW_CONDITION_CONFIG_INVALID", "Condition list is required");
        }

        List<Boolean> results = new java.util.ArrayList<>();
        for (Object rawCondition : conditions) {
            if (!(rawCondition instanceof Map<?, ?> condition)) {
                throw conditionFailure("WORKFLOW_CONDITION_CONFIG_INVALID", "Each condition must be an object");
            }
            results.add(conditionEvaluator.evaluate(condition, context));
        }

        boolean result = "AND".equals(logic)
            ? results.stream().allMatch(Boolean.TRUE::equals)
            : results.stream().anyMatch(Boolean.TRUE::equals);

        UUID selectedEdgeId = selectEdge(outgoingEdges, result ? "TRUE" : "FALSE");
        return new WorkflowNodeExecutionResult(
            com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.COMPLETED,
            Map.of("result", result),
            List.of(selectedEdgeId),
            null,
            null
        );
    }

    private UUID selectEdge(List<WorkflowEdge> edges, String outcome) {
        WorkflowEdge selected = null;
        Set<String> seen = new java.util.HashSet<>();
        for (WorkflowEdge edge : edges) {
            String edgeOutcome = edge.getConfiguration() == null ? "" : keyword(edge.getConfiguration().get("outcome"));
            if (!seen.add(edgeOutcome)) {
                throw conditionFailure("WORKFLOW_EDGE_INVALID", "Duplicate condition edge outcome: " + edgeOutcome);
            }
            if (outcome.equals(edgeOutcome)) selected = edge;
        }
        if (selected == null || edges.size() != 2 || !seen.containsAll(Set.of("TRUE", "FALSE"))) {
            throw conditionFailure("WORKFLOW_EDGE_INVALID", "Condition requires exactly one TRUE and one FALSE edge");
        }
        return selected.getId();
    }

    private String keyword(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase();
    }

    private WorkflowRuntimeException conditionFailure(String code, String message) {
        return new WorkflowRuntimeException(code, message);
    }

    @Override
    public WorkflowNodeExecutorRegistration registration() {
        return new WorkflowNodeExecutorRegistration(WorkflowNodeType.CONDITION, this);
    }
}
