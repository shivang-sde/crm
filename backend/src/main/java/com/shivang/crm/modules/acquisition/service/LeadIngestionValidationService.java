package com.shivang.crm.modules.acquisition.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.acquisition.dto.MappedLeadData;
import com.shivang.crm.modules.acquisition.dto.ValidatedLeadIngestionData;
import com.shivang.crm.modules.acquisition.dto.ValidationError;
import com.shivang.crm.modules.lead.entity.LeadCustomField;
import com.shivang.crm.modules.lead.entity.LeadSource;
import com.shivang.crm.modules.lead.entity.LeadStatus;
import com.shivang.crm.modules.lead.repository.LeadCustomFieldRepository;
import com.shivang.crm.modules.lead.repository.LeadSourceRepository;
import com.shivang.crm.modules.lead.repository.LeadStatusRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeadIngestionValidationService {

    private static final Pattern SIMPLE_EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$");

    private static final Set<String> SUPPORTED_CUSTOM_FIELD_TYPES = Set.of(
        "TEXT", "TEXTAREA", "EMAIL", "PHONE", "NUMBER", "DATE", "BOOLEAN", "SELECT", "MULTISELECT", "URL"
    );

    private final LeadCustomFieldRepository leadCustomFieldRepository;
    private final LeadSourceRepository leadSourceRepository;
    private final LeadStatusRepository leadStatusRepository;

    @Transactional(readOnly = true)
    public ValidatedLeadIngestionData validateAndNormalize(UUID tenantId, MappedLeadData mappedData) {
        if (mappedData == null) {
            return ValidatedLeadIngestionData.builder().errors(List.of(validationError("", "INVALID_INPUT", "No mapped lead data was provided"))).build();
        }

        List<ValidationError> errors = new ArrayList<>();
        Map<String, Object> customData = new LinkedHashMap<>();

        String firstName = normalizeStringValue(getStringValue(mappedData.getStandardFields(), "firstName"));
        String lastName = normalizeStringValue(getStringValue(mappedData.getStandardFields(), "lastName"));
        String email = normalizeEmail(toStringValue(getStringValue(mappedData.getStandardFields(), "email")));
        String phone = normalizePhone(toStringValue(getStringValue(mappedData.getStandardFields(), "phone")));
        String company = normalizeStringValue(getStringValue(mappedData.getStandardFields(), "company"));

        if (firstName == null || firstName.isBlank()) {
            errors.add(validationError("firstName", "REQUIRED", "Lead firstName is required"));
        }

        if (email != null && !isValidEmail(email)) {
            errors.add(validationError("email", "INVALID_EMAIL", "Email format is invalid"));
        }

        if (phone != null && !isPlausiblePhone(phone)) {
            errors.add(validationError("phone", "INVALID_PHONE", "Phone format is invalid"));
        } else if (mappedData.getStandardFields() != null
            && mappedData.getStandardFields().get("phone") != null
            && !toStringValue(mappedData.getStandardFields().get("phone")).trim().isBlank()
            && phone == null) {
            errors.add(validationError("phone", "INVALID_PHONE", "Phone format is invalid"));
        }

        Object sourceValue = resolveSourceValue(tenantId, mappedData.getSystemFields(), errors);
        Object statusValue = resolveStatusValue(tenantId, mappedData.getSystemFields(), errors);

        Map<String, Object> normalizedCustom = normalizeCustomFields(tenantId, mappedData.getCustomFields(), errors);
        if (normalizedCustom != null) {
            customData.putAll(normalizedCustom);
        }

        validateRequiredCustomFields(tenantId, errors, customData);

        return ValidatedLeadIngestionData.builder()
            .firstName(firstName)
            .lastName(lastName)
            .email(email)
            .phone(phone)
            .company(company)
            .sourceValue(sourceValue)
            .statusValue(statusValue)
            .customData(customData.isEmpty() ? null : customData)
            .errors(errors.isEmpty() ? List.of() : errors)
            .build();
    }

    private Object resolveSourceValue(UUID tenantId, Map<String, Object> systemFields, List<ValidationError> errors) {
        if (systemFields == null || systemFields.isEmpty()) {
            return null;
        }

        Object rawValue = systemFields.get("source");
        if (rawValue == null) {
            return null;
        }

        Optional<LeadSource> resolved = resolveLeadSource(tenantId, rawValue);
        if (resolved.isEmpty()) {
            errors.add(validationError("source", "INVALID_REFERENCE", "Could not resolve tenant-safe source for value '" + rawValue + "'"));
            return null;
        }

        return resolved.get().getId();
    }

    private Object resolveStatusValue(UUID tenantId, Map<String, Object> systemFields, List<ValidationError> errors) {
        if (systemFields == null || systemFields.isEmpty()) {
            return findDefaultStatusValue(tenantId, errors);
        }

        Object rawValue = systemFields.get("status");
        if (rawValue == null) {
            return findDefaultStatusValue(tenantId, errors);
        }

        Optional<LeadStatus> resolved = resolveLeadStatus(tenantId, rawValue);
        if (resolved.isEmpty()) {
            errors.add(validationError("status", "INVALID_REFERENCE", "Could not resolve tenant-safe status for value '" + rawValue + "'"));
            return null;
        }

        return resolved.get().getId();
    }

    private UUID findDefaultStatusValue(UUID tenantId, List<ValidationError> errors) {
        return leadStatusRepository.findDefaultStatusByTenant(tenantId)
            .map(LeadStatus::getId)
            .orElseGet(() -> {
                errors.add(validationError("status", "DEFAULT_STATUS_REQUIRED", "No default lead status is configured for this tenant"));
                return null;
            });
    }

    private Optional<LeadSource> resolveLeadSource(UUID tenantId, Object rawValue) {
        if (rawValue instanceof LeadSource leadSource) {
            return leadSource.getTenantId() != null && leadSource.getTenantId().equals(tenantId)
                ? Optional.of(leadSource)
                : Optional.empty();
        }
        if (rawValue instanceof UUID uuid) {
            return leadSourceRepository.findById(uuid)
                .filter(source -> tenantId.equals(source.getTenantId()))
                .filter(source -> !Boolean.TRUE.equals(source.getDeleted()));
        }
        if (rawValue instanceof String candidate) {
            String normalized = candidate.trim();
            if (normalized.isBlank()) {
                return Optional.empty();
            }
            try {
                UUID uuid = UUID.fromString(normalized);
                return leadSourceRepository.findById(uuid)
                    .filter(source -> tenantId.equals(source.getTenantId()))
                    .filter(source -> !Boolean.TRUE.equals(source.getDeleted()));
            } catch (IllegalArgumentException ignored) {
                return leadSourceRepository.findByTenantIdAndName(tenantId, normalized)
                    .filter(source -> !Boolean.TRUE.equals(source.getDeleted()));
            }
        }
        return Optional.empty();
    }

    private Optional<LeadStatus> resolveLeadStatus(UUID tenantId, Object rawValue) {
        if (rawValue instanceof LeadStatus leadStatus) {
            return leadStatus.getTenantId() != null && leadStatus.getTenantId().equals(tenantId)
                ? Optional.of(leadStatus)
                : Optional.empty();
        }
        if (rawValue instanceof UUID uuid) {
            return leadStatusRepository.findByIdAndTenantId(uuid, tenantId)
                .filter(status -> !Boolean.TRUE.equals(status.getDeleted()));
        }
        if (rawValue instanceof String candidate) {
            String normalized = candidate.trim();
            if (normalized.isBlank()) {
                return Optional.empty();
            }
            try {
                UUID uuid = UUID.fromString(normalized);
                return leadStatusRepository.findByIdAndTenantId(uuid, tenantId)
                    .filter(status -> !Boolean.TRUE.equals(status.getDeleted()));
            } catch (IllegalArgumentException ignored) {
                return leadStatusRepository.findByTenantIdAndName(tenantId, normalized)
                    .filter(status -> !Boolean.TRUE.equals(status.getDeleted()));
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> normalizeCustomFields(UUID tenantId, Map<String, Object> mappedCustomFields, List<ValidationError> errors) {
        if (mappedCustomFields == null || mappedCustomFields.isEmpty()) {
            return new LinkedHashMap<>();
        }

        List<LeadCustomField> activeFields = leadCustomFieldRepository.findActiveFieldsByTenant(tenantId);
        Map<String, LeadCustomField> fieldsByKey = activeFields.stream()
            .collect(java.util.stream.Collectors.toMap(LeadCustomField::getFieldKey, field -> field, (a, b) -> b, LinkedHashMap::new));

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : mappedCustomFields.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                errors.add(validationError("customData", "INVALID_KEY", "Custom field key is missing"));
                continue;
            }

            LeadCustomField field = fieldsByKey.get(key);
            if (field == null) {
                errors.add(validationError(key, "INVALID_CUSTOM_FIELD", "Custom field '" + key + "' is not valid for this tenant"));
                continue;
            }

            if (Boolean.TRUE.equals(field.getDeleted())) {
                errors.add(validationError(key, "INVALID_CUSTOM_FIELD", "Custom field '" + key + "' is deleted and cannot be used"));
                continue;
            }

            Object normalizedValue = convertCustomFieldValue(field, entry.getValue(), errors, key);
            if (normalizedValue != null) {
                normalized.put(key, normalizedValue);
            } else if (Boolean.TRUE.equals(field.getIsRequired())) {
                errors.add(validationError(key, "CUSTOM_FIELD_REQUIRED", "Custom field '" + field.getFieldLabel() + "' is required"));
            }
        }

        return normalized;
    }

    private Object convertCustomFieldValue(LeadCustomField field, Object value, List<ValidationError> errors, String fieldKey) {
        if (value == null) {
            return null;
        }

        String fieldType = field.getFieldType() == null ? "TEXT" : field.getFieldType().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CUSTOM_FIELD_TYPES.contains(fieldType)) {
            errors.add(validationError(fieldKey, "UNSUPPORTED_TYPE", "Custom field type '" + fieldType + "' is not supported for validation"));
            return null;
        }

        return switch (fieldType) {
            case "TEXT", "TEXTAREA" -> normalizeTextValue(value, fieldKey, errors);
            case "EMAIL" -> normalizeEmailValue(value, fieldKey, errors);
            case "PHONE" -> normalizePhoneValue(value, fieldKey, errors);
            case "NUMBER" -> normalizeNumberValue(value, fieldKey, errors);
            case "DATE" -> normalizeDateValue(value, fieldKey, errors);
            case "BOOLEAN" -> normalizeBooleanValue(value, fieldKey, errors);
            case "SELECT" -> normalizeSelectValue(field, value, fieldKey, errors);
            case "MULTISELECT" -> normalizeMultiSelectValue(field, value, fieldKey, errors);
            case "URL" -> normalizeUrlValue(value, fieldKey, errors);
            default -> value;
        };
    }

    private Object normalizeTextValue(Object value, String fieldKey, List<ValidationError> errors) {
        if (value instanceof String str) {
            String normalized = str.trim();
            return normalized.isBlank() ? null : normalized;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must be a string"));
        return null;
    }

    private Object normalizeEmailValue(Object value, String fieldKey, List<ValidationError> errors) {
        if (!(value instanceof String str)) {
            errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must be a string"));
            return null;
        }

        String normalized = normalizeEmail(str);
        if (normalized == null) {
            return null;
        }
        if (!isValidEmail(normalized)) {
            errors.add(validationError(fieldKey, "INVALID_EMAIL", "Custom field '" + fieldKey + "' must contain a valid email address"));
            return null;
        }
        return normalized;
    }

    private Object normalizePhoneValue(Object value, String fieldKey, List<ValidationError> errors) {
        if (!(value instanceof String str)) {
            errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must be a string"));
            return null;
        }

        String trimmed = str.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        String normalized = normalizePhone(trimmed);
        if (normalized == null) {
            errors.add(validationError(fieldKey, "INVALID_PHONE", "Custom field '" + fieldKey + "' contains invalid phone characters"));
            return null;
        }
        if (!isPlausiblePhone(normalized)) {
            errors.add(validationError(fieldKey, "INVALID_PHONE", "Custom field '" + fieldKey + "' must have a plausible phone number"));
            return null;
        }
        return normalized;
    }

    private Object normalizeNumberValue(Object value, String fieldKey, List<ValidationError> errors) {
        if (value instanceof Number number) {
            try {
                return new java.math.BigDecimal(number.toString());
            } catch (NumberFormatException e) {
                errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must be a valid number"));
                return null;
            }
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isBlank()) {
                return null;
            }
            try {
                return new java.math.BigDecimal(trimmed);
            } catch (NumberFormatException e) {
                errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must be a valid number"));
                return null;
            }
        }
        errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must be a number"));
        return null;
    }

    private Object normalizeDateValue(Object value, String fieldKey, List<ValidationError> errors) {
        if (!(value instanceof String str)) {
            errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must be a date string"));
            return null;
        }

        String trimmed = str.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE).toString();
        } catch (DateTimeParseException ignored) {
            errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' uses an unsupported date format; use yyyy-MM-dd"));
            return null;
        }
    }

    private Object normalizeBooleanValue(Object value, String fieldKey, List<ValidationError> errors) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isBlank()) {
                return null;
            }
            if ("true".equalsIgnoreCase(trimmed)) {
                return true;
            }
            if ("false".equalsIgnoreCase(trimmed)) {
                return false;
            }
        }
        errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must be a boolean"));
        return null;
    }

    private Object normalizeSelectValue(LeadCustomField field, Object value, String fieldKey, List<ValidationError> errors) {
        if (!(value instanceof String str)) {
            errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must be a string"));
            return null;
        }

        String normalized = str.trim();
        if (normalized.isBlank()) {
            return null;
        }

        if (isAllowedOption(field, normalized)) {
            return normalized;
        }

        errors.add(validationError(fieldKey, "INVALID_OPTION", "Custom field '" + fieldKey + "' contains an invalid option value"));
        return null;
    }

    private Object normalizeMultiSelectValue(LeadCustomField field, Object value, String fieldKey, List<ValidationError> errors) {
        if (value instanceof List<?> list) {
            List<String> selected = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof String str)) {
                    errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must contain only string option values"));
                    return null;
                }
                String normalized = str.trim();
                if (!isAllowedOption(field, normalized)) {
                    errors.add(validationError(fieldKey, "INVALID_OPTION", "Custom field '" + fieldKey + "' contains an invalid option value"));
                    return null;
                }
                selected.add(normalized);
            }
            return selected;
        }

        if (value instanceof String str) {
            String[] parts = str.split(",");
            List<String> selected = new ArrayList<>();
            for (String part : parts) {
                String normalized = part.trim();
                if (normalized.isEmpty()) {
                    errors.add(validationError(fieldKey, "INVALID_OPTION", "Custom field '" + fieldKey + "' contains an invalid option value"));
                    return null;
                }
                if (!isAllowedOption(field, normalized)) {
                    errors.add(validationError(fieldKey, "INVALID_OPTION", "Custom field '" + fieldKey + "' contains an invalid option value"));
                    return null;
                }
                selected.add(normalized);
            }
            if (selected.isEmpty()) {
                errors.add(validationError(fieldKey, "INVALID_OPTION", "Custom field '" + fieldKey + "' contains no valid option values"));
                return null;
            }
            return selected;
        }

        errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must be a list or comma-separated string"));
        return null;
    }

    private Object normalizeUrlValue(Object value, String fieldKey, List<ValidationError> errors) {
        if (!(value instanceof String str)) {
            errors.add(validationError(fieldKey, "INVALID_CUSTOM_FIELD_VALUE", "Custom field '" + fieldKey + "' must be a string"));
            return null;
        }

        String trimmed = str.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        try {
            new URI(trimmed);
            return trimmed;
        } catch (URISyntaxException e) {
            errors.add(validationError(fieldKey, "INVALID_URL", "Custom field '" + fieldKey + "' must be a valid URL"));
            return null;
        }
    }

    private void validateRequiredCustomFields(UUID tenantId, List<ValidationError> errors, Map<String, Object> customData) {
        List<LeadCustomField> requiredFields = leadCustomFieldRepository.findByTenantIdOrderByDisplayOrder(tenantId).stream()
            .filter(field -> Boolean.TRUE.equals(field.getIsRequired()) && Boolean.TRUE.equals(field.getIsActive()) && !Boolean.TRUE.equals(field.getDeleted()))
            .toList();

        for (LeadCustomField field : requiredFields) {
            Object value = customData == null ? null : customData.get(field.getFieldKey());
            if (value == null || isBlankValue(value)) {
                boolean alreadyReported = errors.stream()
                    .anyMatch(e -> field.getFieldKey().equals(e.getField()) && "CUSTOM_FIELD_REQUIRED".equals(e.getCode()));
                if (!alreadyReported) {
                    errors.add(validationError(field.getFieldKey(), "CUSTOM_FIELD_REQUIRED", "Custom field '" + field.getFieldLabel() + "' is required"));
                }
            }
        }
    }

    private boolean isAllowedOption(LeadCustomField field, String value) {
        if (field.getOptionsJson() == null || field.getOptionsJson().isEmpty()) {
            return false;
        }
        return field.getOptionsJson().stream().anyMatch(option -> value.equals(option.get("value")));
    }

    private boolean isBlankValue(Object value) {
        if (value instanceof String str) {
            return str.isBlank();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    private String normalizeStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str.trim().isBlank() ? null : str.trim();
        }
        return String.valueOf(value).trim().isBlank() ? null : String.valueOf(value).trim();
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = value.trim();
        if (sanitized.isBlank()) {
            return null;
        }

        StringBuilder normalized = new StringBuilder();
        boolean hasLeadingPlus = false;

        for (char ch : sanitized.toCharArray()) {
            if (ch == '+') {
                if (normalized.length() == 0 && !hasLeadingPlus) {
                    normalized.append(ch);
                    hasLeadingPlus = true;
                } else {
                    return null;
                }
                continue;
            }

            if (ch == ' ' || ch == '-' || ch == '(' || ch == ')' || ch == '.') {
                continue;
            }

            if (Character.isDigit(ch)) {
                normalized.append(ch);
                continue;
            }

            return null;
        }

        String result = normalized.toString();
        return result.isBlank() ? null : result;
    }

    private boolean isValidEmail(String email) {
        return email != null && SIMPLE_EMAIL_PATTERN.matcher(email).matches();
    }

    private boolean isPlausiblePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }

        String value = phone.trim();
        if (value.startsWith("+")) {
            value = value.substring(1);
        }

        if (value.isBlank()) {
            return false;
        }

        return value.chars().allMatch(Character::isDigit) && value.length() >= 7 && value.length() <= 15;
    }

    private Object getStringValue(Map<String, Object> fieldMap, String key) {
        if (fieldMap == null) {
            return null;
        }
        return fieldMap.get(key);
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str;
        }
        return String.valueOf(value);
    }

    private ValidationError validationError(String field, String code, String message) {
        return ValidationError.builder().field(field).code(code).message(message).build();
    }
}
