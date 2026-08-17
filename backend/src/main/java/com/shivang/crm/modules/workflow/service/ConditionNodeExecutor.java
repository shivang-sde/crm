package com.shivang.crm.modules.workflow.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
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
    private static final Set<String> OPERATORS = Set.of(
        "EQUALS", "NOT_EQUALS", "GREATER_THAN", "GREATER_THAN_OR_EQUAL", "LESS_THAN", "LESS_THAN_OR_EQUAL",
        "CONTAINS", "NOT_CONTAINS", "IS_NULL", "IS_NOT_NULL", "IN", "NOT_IN"
    );

    private final WorkflowValueResolver valueResolver;

    public ConditionNodeExecutor(WorkflowValueResolver valueResolver) {
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

        List<Boolean> results = new ArrayList<>();
        for (Object rawCondition : conditions) {
            if (!(rawCondition instanceof Map<?, ?> condition)) {
                throw conditionFailure("WORKFLOW_CONDITION_CONFIG_INVALID", "Each condition must be an object");
            }
            results.add(evaluateCondition(condition, context));
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

    private boolean evaluateCondition(Map<?, ?> condition, WorkflowExecutionContext context) {
        String field = valueText(condition.get("field"));
        String operator = keyword(condition.get("operator"));
        if (field.isBlank()) {
            throw conditionFailure("WORKFLOW_CONDITION_CONFIG_INVALID", "Condition field is required");
        }
        if (!OPERATORS.contains(operator)) {
            throw conditionFailure("WORKFLOW_CONDITION_OPERATOR_INVALID", "Unsupported condition operator: " + operator);
        }

        WorkflowResolvedValue resolved = valueResolver.resolve(context, field);
        if (!resolved.found()) {
            throw conditionFailure("WORKFLOW_CONDITION_FIELD_NOT_FOUND", "Condition field was not found: " + field);
        }
        Object actual = resolved.value();
        Object expected = condition.get("value");

        return switch (operator) {
            case "IS_NULL" -> actual == null;
            case "IS_NOT_NULL" -> actual != null;
            case "EQUALS" -> compareEquality(actual, expected);
            case "NOT_EQUALS" -> !compareEquality(actual, expected);
            case "GREATER_THAN" -> compareOrdered(actual, expected, operator) > 0;
            case "GREATER_THAN_OR_EQUAL" -> compareOrdered(actual, expected, operator) >= 0;
            case "LESS_THAN" -> compareOrdered(actual, expected, operator) < 0;
            case "LESS_THAN_OR_EQUAL" -> compareOrdered(actual, expected, operator) <= 0;
            case "CONTAINS" -> contains(actual, expected, false);
            case "NOT_CONTAINS" -> contains(actual, expected, true);
            case "IN" -> membership(actual, expected, false);
            case "NOT_IN" -> membership(actual, expected, true);
            default -> throw conditionFailure("WORKFLOW_CONDITION_OPERATOR_INVALID", "Unsupported condition operator: " + operator);
        };
    }

    private boolean compareEquality(Object actual, Object expected) {
        if (actual == null || expected == null) return actual == expected;
        if (actual instanceof Number || expected instanceof Number) {
            return toDecimal(actual).compareTo(toDecimal(expected)) == 0;
        }
        if (actual instanceof Boolean actualBoolean && expected instanceof Boolean expectedBoolean) {
            return actualBoolean.equals(expectedBoolean);
        }
        if (actual instanceof UUID actualUuid) {
            try { return actualUuid.equals(UUID.fromString(String.valueOf(expected))); }
            catch (IllegalArgumentException ex) { throw conditionFailure("WORKFLOW_CONDITION_VALUE_INVALID", "Expected a valid UUID"); }
        }
        return actual instanceof String && expected instanceof String
            ? actual.equals(expected)
            : actual.equals(expected);
    }

    private int compareOrdered(Object actual, Object expected, String operator) {
        if (actual == null || expected == null) {
            throw conditionFailure("WORKFLOW_CONDITION_VALUE_INVALID", operator + " does not support null values");
        }
        if (actual instanceof Number || expected instanceof Number) {
            return toDecimal(actual).compareTo(toDecimal(expected));
        }
        Instant actualInstant = toInstant(actual);
        Instant expectedInstant = toInstant(expected);
        if (actualInstant != null && expectedInstant != null) return actualInstant.compareTo(expectedInstant);
        throw conditionFailure("WORKFLOW_CONDITION_VALUE_INVALID", "Values are not safely orderable");
    }

    private boolean contains(Object actual, Object expected, boolean negate) {
        if (!(actual instanceof String actualText) || !(expected instanceof String expectedText)) {
            throw conditionFailure("WORKFLOW_CONDITION_VALUE_INVALID", "CONTAINS requires string values");
        }
        boolean result = actualText.contains(expectedText);
        return negate != result;
    }

    private boolean membership(Object actual, Object expected, boolean negate) {
        if (!(expected instanceof List<?> values)) {
            throw conditionFailure("WORKFLOW_CONDITION_VALUE_INVALID", "IN requires a list value");
        }
        boolean result = values.stream().anyMatch(value -> compareEquality(actual, value));
        return negate != result;
    }

    private UUID selectEdge(List<WorkflowEdge> edges, String outcome) {
        WorkflowEdge selected = null;
        Set<String> seen = new HashSet<>();
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

    private BigDecimal toDecimal(Object value) {
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException ex) { throw conditionFailure("WORKFLOW_CONDITION_VALUE_INVALID", "Values are not numeric"); }
    }

    private Instant toInstant(Object value) {
        try {
            if (value instanceof Instant instant) return instant;
            if (value instanceof LocalDate date) return date.atStartOfDay().toInstant(ZoneOffset.UTC);
            if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
            if (value instanceof String text) return Instant.parse(text);
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private String valueText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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