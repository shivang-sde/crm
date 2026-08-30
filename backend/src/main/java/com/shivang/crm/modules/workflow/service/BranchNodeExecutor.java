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
import com.shivang.crm.modules.workflow.service.WorkflowResolvedValue;
import com.shivang.crm.modules.workflow.service.WorkflowValueResolver;

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
    private final WorkflowValueResolver valueResolver;

    public BranchNodeExecutor(WorkflowConditionEvaluator conditionEvaluator, WorkflowValueResolver valueResolver) {
        this.conditionEvaluator = conditionEvaluator;
        this.valueResolver = valueResolver;
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
        List<Map<String, Object>> ruleResults = new java.util.ArrayList<>();
        int index = 0;
        for (Object rawCondition : conditions) {
            if (!(rawCondition instanceof Map<?, ?> condition)) {
                throw branchFailure("WORKFLOW_BRANCH_INVALID", "Each branch condition must be an object");
            }
            String field = valueText(condition.get("field"));
            String operator = keyword(condition.get("operator"));
            Object expected = condition.get("value");
            Object actual = null;
            boolean passed;
            try {
                try {
                    WorkflowResolvedValue resolved = valueResolver.resolve(context, field);
                    actual = resolved.found() ? resolved.value() : null;
                } catch (Exception ignore) {
                    actual = null;
                }
                passed = conditionEvaluator.evaluate(condition, context);
            } catch (RuntimeException ex) {
                passed = false;
            }
            results.add(passed);
            Map<String, Object> rr = new java.util.LinkedHashMap<>();
            rr.put("index", index);
            rr.put("field", field);
            rr.put("operator", operator);
            rr.put("expected", expected);
            rr.put("actual", actual);
            rr.put("passed", passed);
            ruleResults.add(rr);
            index++;
        }

        boolean outcomeBoolean = "AND".equals(logic)
            ? results.stream().allMatch(Boolean.TRUE::equals)
            : results.stream().anyMatch(Boolean.TRUE::equals);
        String outcome = outcomeBoolean ? "TRUE" : "FALSE";

        UUID selectedEdgeId = selectEdge(outgoingEdges, outcome);

        java.util.Map<String, Object> output = new java.util.LinkedHashMap<>();
        output.put("outcome", outcome);
        output.put("selectedEdgeId", String.valueOf(selectedEdgeId));
        output.put("ruleResults", ruleResults);
        output.put("logic", logic);
        return new WorkflowNodeExecutionResult(
            com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.COMPLETED,
            output,
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

    private String valueText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private WorkflowRuntimeException branchFailure(String code, String message) {
        return new WorkflowRuntimeException(code, message);
    }

    @Override
    public WorkflowNodeExecutorRegistration registration() {
        return new WorkflowNodeExecutorRegistration(WorkflowNodeType.BRANCH, this);
    }
}
