package com.shivang.crm.modules.workflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class WorkflowUpdateValueSupport {

    private WorkflowUpdateValueSupport() {
    }

    static UUID uuid(Object value, String field) {
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception ex) {
            throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_VALIDATION_FAILED", "Invalid UUID for " + field);
        }
    }

    static BigDecimal decimal(Object value, String field) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ex) {
            throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_VALIDATION_FAILED", "Invalid number for " + field);
        }
    }

    static Integer integer(Object value, String field) {
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception ex) {
            throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_VALIDATION_FAILED", "Invalid integer for " + field);
        }
    }

    static LocalDate date(Object value, String field) {
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception ex) {
            throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_VALIDATION_FAILED", "Invalid date for " + field);
        }
    }

    static String text(Object value, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_VALIDATION_FAILED", field + " cannot be blank");
        }
        return String.valueOf(value);
    }

    static Map<String, Object> customFields(Map<String, Object> current, String key, Object value) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (current != null) merged.putAll(current);
        merged.put(key, value);
        return merged;
    }

    static String customKey(String field) {
        if (!field.startsWith("customFields.") || field.length() <= "customFields.".length()) {
            throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_FIELD_NOT_SUPPORTED", "Custom field path is invalid");
        }
        return field.substring("customFields.".length());
    }

    static WorkflowEntityUpdateResult result(String type, UUID id, String field, Object value) {
        return new WorkflowEntityUpdateResult(type, id, field, value, Map.of("updated", true));
    }
}