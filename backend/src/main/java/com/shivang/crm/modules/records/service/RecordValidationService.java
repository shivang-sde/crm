package com.shivang.crm.modules.records.service;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordFieldType;
import com.shivang.crm.shared.exception.BusinessException;

@Service
public class RecordValidationService {

    public void validateAndNormalize(UUID tenantId, List<RecordField> activeFields, Map<String, Object> data) {
        Map<String, RecordField> fieldByKey = activeFields.stream()
                .collect(Collectors.toMap(RecordField::getFieldKey, Function.identity()));

        // Required check
        for (RecordField field : activeFields) {
            if (Boolean.TRUE.equals(field.getIsRequired()) && Boolean.TRUE.equals(field.getIsActive())) {
                Object value = data == null ? null : data.get(field.getFieldKey());
                if (value == null || isBlankValue(value)) {
                    throw new BusinessException("REQUIRED_FIELD_MISSING",
                            "Required field '" + field.getFieldKey() + "' is missing");
                }
            }
        }

        if (data == null || data.isEmpty()) {
            return;
        }

        // Unknown field rejection + type validation
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            RecordField field = fieldByKey.get(key);
            if (field == null) {
                throw new BusinessException("UNKNOWN_FIELD",
                        "Unknown field '" + key + "' for this record type");
            }
            if (Boolean.FALSE.equals(field.getIsActive())) {
                throw new BusinessException("FIELD_INACTIVE",
                        "Field '" + key + "' is inactive");
            }
            if (value == null) {
                if (Boolean.TRUE.equals(field.getIsRequired())) {
                    throw new BusinessException("REQUIRED_FIELD_MISSING",
                            "Required field '" + key + "' cannot be null");
                }
                continue;
            }
            validateType(field, value);
        }
    }

    private boolean isBlankValue(Object value) {
        if (value instanceof String s) return s.isBlank();
        return false;
    }

    private void validateType(RecordField field, Object value) {
        RecordFieldType type = field.getFieldType();
        String key = field.getFieldKey();
        switch (type) {
            case TEXT, LONG_TEXT -> {
                if (!(value instanceof String)) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a string");
                }
            }
            case INTEGER -> {
                if (value instanceof Integer || value instanceof Long) return;
                if (value instanceof Number n) {
                    double d = n.doubleValue();
                    if (d != Math.floor(d)) {
                        throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be an integer");
                    }
                    return;
                }
                if (value instanceof String s) {
                    try { Long.parseLong(s.trim()); return; } catch (NumberFormatException e) {
                        throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be an integer");
                    }
                }
                throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be an integer");
            }
            case DECIMAL -> {
                if (value instanceof Number) return;
                if (value instanceof String s) {
                    try { new BigDecimal(s.trim()); return; } catch (Exception e) {
                        throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a decimal");
                    }
                }
                throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a decimal");
            }
            case BOOLEAN -> {
                if (value instanceof Boolean) return;
                if (value instanceof String s) {
                    String n = s.trim().toLowerCase();
                    if ("true".equals(n) || "false".equals(n)) return;
                }
                throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a boolean");
            }
            case DATE -> {
                if (!(value instanceof String s)) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a date string (yyyy-MM-dd)");
                }
                try { LocalDate.parse(s.trim()); } catch (DateTimeParseException e) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a valid date (yyyy-MM-dd)");
                }
            }
            case DATETIME -> {
                if (!(value instanceof String s)) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a datetime string (ISO-8601)");
                }
                try { Instant.parse(s.trim()); } catch (DateTimeParseException e) {
                    // try LocalDateTime fallback
                    try { java.time.LocalDateTime.parse(s.trim()); } catch (DateTimeParseException e2) {
                        throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a valid datetime (ISO-8601)");
                    }
                }
            }
            case ENUM -> {
                if (!(value instanceof String s)) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a string (enum)");
                }
                List<String> options = field.getOptionsJson();
                if (options == null || options.isEmpty()) {
                    throw new BusinessException("INVALID_FIELD_CONFIG", "Enum field '" + key + "' has no options configured");
                }
                if (!options.contains(s)) {
                    throw new BusinessException("INVALID_ENUM_VALUE",
                            "Field '" + key + "' value '" + s + "' is not a valid option. Allowed: " + String.join(", ", options));
                }
            }
            case URL -> {
                if (!(value instanceof String s)) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a URL string");
                }
                try { new URL(s.trim()); } catch (MalformedURLException e) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a valid URL");
                }
            }
            case PHONE -> {
                if (!(value instanceof String s)) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a phone string");
                }
                String trimmed = s.trim();
                if (!trimmed.matches("^[+0-9\\-()\\s]+$")) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a valid phone");
                }
                String digits = trimmed.replaceAll("[^0-9]", "");
                if (digits.length() < 7 || digits.length() > 15) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a valid phone (7-15 digits)");
                }
            }
            case EMAIL -> {
                if (!(value instanceof String s)) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be an email string");
                }
                if (!s.trim().contains("@") || !s.trim().contains(".")) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a valid email");
                }
                if (!s.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a valid email");
                }
            }
            case JSON -> {
                // Accept any JSON-compatible value: map, list, string, number, boolean
            }
            case REFERENCE -> {
                if (!(value instanceof String s)) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a UUID string (reference to " + field.getReferenceEntityType() + ")");
                }
                String trimmed = s.trim();
                if (trimmed.isEmpty()) {
                    if (Boolean.TRUE.equals(field.getIsRequired())) {
                        throw new BusinessException("REQUIRED_FIELD_MISSING", "Required reference field '" + key + "' is missing");
                    }
                    return;
                }
                try { UUID.fromString(trimmed); } catch (IllegalArgumentException e) {
                    throw new BusinessException("INVALID_FIELD_TYPE", "Field '" + key + "' must be a valid UUID (reference to " + field.getReferenceEntityType() + ")");
                }
            }
        }
    }
}
