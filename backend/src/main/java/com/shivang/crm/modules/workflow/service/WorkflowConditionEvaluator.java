package com.shivang.crm.modules.workflow.service;

import java.math.BigDecimal;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Shared condition-field evaluation used by CONDITION and BRANCH nodes.
 * Evaluates a single structured rule against the workflow execution context.
 */
@Component
@RequiredArgsConstructor
public class WorkflowConditionEvaluator {

    private final WorkflowValueResolver valueResolver;

    public boolean evaluate(Map<?, ?> condition, WorkflowExecutionContext context) {
        String field = valueText(condition.get("field"));
        String operator = keyword(condition.get("operator"));
        if (field.isBlank()) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_CONDITION_CONFIG_INVALID", "Condition field is required");
        }
        if (!SUPPORTED_OPERATORS.contains(operator)) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_CONDITION_OPERATOR_INVALID",
                "Unsupported condition operator: " + operator);
        }

        WorkflowResolvedValue resolved = valueResolver.resolve(context, field);
        if (!resolved.found()) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_CONDITION_FIELD_NOT_FOUND", "Condition field was not found: " + field);
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
            default -> throw new WorkflowRuntimeException(
                "WORKFLOW_CONDITION_OPERATOR_INVALID",
                "Unsupported condition operator: " + operator);
        };
    }

    public static final java.util.Set<String> SUPPORTED_OPERATORS = java.util.Set.of(
        "EQUALS", "NOT_EQUALS", "GREATER_THAN", "GREATER_THAN_OR_EQUAL", "LESS_THAN",
        "LESS_THAN_OR_EQUAL", "CONTAINS", "NOT_CONTAINS", "IS_NULL", "IS_NOT_NULL",
        "IN", "NOT_IN"
    );

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
            catch (IllegalArgumentException ex) {
                throw new WorkflowRuntimeException(
                    "WORKFLOW_CONDITION_VALUE_INVALID", "Expected a valid UUID");
            }
        }
        return actual instanceof String && expected instanceof String
            ? actual.equals(expected)
            : actual.equals(expected);
    }

    private int compareOrdered(Object actual, Object expected, String operator) {
        if (actual == null || expected == null) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_CONDITION_VALUE_INVALID", operator + " does not support null values");
        }
        if (actual instanceof Number || expected instanceof Number) {
            return toDecimal(actual).compareTo(toDecimal(expected));
        }
        Instant actualInstant = toInstant(actual);
        Instant expectedInstant = toInstant(expected);
        if (actualInstant != null && expectedInstant != null) return actualInstant.compareTo(expectedInstant);
        throw new WorkflowRuntimeException(
            "WORKFLOW_CONDITION_VALUE_INVALID", "Values are not safely orderable");
    }

    private boolean contains(Object actual, Object expected, boolean negate) {
        if (!(actual instanceof String actualText) || !(expected instanceof String expectedText)) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_CONDITION_VALUE_INVALID", "CONTAINS requires string values");
        }
        boolean result = actualText.contains(expectedText);
        return negate != result;
    }

    private boolean membership(Object actual, Object expected, boolean negate) {
        if (!(expected instanceof List<?> values)) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_CONDITION_VALUE_INVALID", "IN requires a list value");
        }
        boolean result = values.stream().anyMatch(value -> compareEquality(actual, value));
        return negate != result;
    }

    private BigDecimal toDecimal(Object value) {
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException ex) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_CONDITION_VALUE_INVALID", "Values are not numeric");
        }
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDate date) return date.atStartOfDay().toInstant(ZoneOffset.UTC);
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof String text) { try { return Instant.parse(text); } catch (RuntimeException ignored) { } }
        return null;
    }

    private String valueText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String keyword(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase();
    }
}
