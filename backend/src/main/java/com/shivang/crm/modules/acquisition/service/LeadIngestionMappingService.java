package com.shivang.crm.modules.acquisition.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.acquisition.config.LeadIngestionConfig;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionFieldMappingRequest;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionFieldMappingResponse;
import com.shivang.crm.modules.acquisition.dto.MappedLeadData;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionFieldMapping;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionTargetType;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionTransformType;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionEventRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionFieldMappingRepository;
import com.shivang.crm.modules.integration.webhook.JsonPathValueExtractor;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeadIngestionMappingService {

    private final LeadIngestionConfigRepository leadIngestionConfigRepository;
    private final LeadIngestionFieldMappingRepository leadIngestionFieldMappingRepository;
    private final LeadIngestionEventRepository leadIngestionEventRepository;
    private final LeadIngestionTargetFieldService leadIngestionTargetFieldService;
    private final JsonPathValueExtractor jsonPathValueExtractor;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public void assertConfigOwnership(UUID tenantId, UUID configId) {
        ensureConfig(configId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<LeadIngestionFieldMappingResponse> listMappings(UUID tenantId, UUID configId) {
        ensureConfig(configId, tenantId);

        return leadIngestionFieldMappingRepository
            .findByTenantIdAndIngestionConfigIdAndDeletedFalseOrderByDisplayOrderAscCreatedAtAsc(tenantId, configId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public LeadIngestionFieldMappingResponse createMapping(UUID tenantId, UUID configId, LeadIngestionFieldMappingRequest request) {
        ensureConfig(configId, tenantId);
        String normalizedSourcePath = normalizePath(request.getSourcePath());
        validateSourcePath(request.getSourcePath(), normalizedSourcePath);
        String normalizedTargetField = normalizeTargetField(request.getTargetField());
        validateTarget(tenantId, request.getTargetType(), normalizedTargetField);
        ensureNoDuplicateTarget(tenantId, configId, request.getTargetType(), normalizedTargetField, null);
        validateTransformConfig(request.getTransformType(), request.getTransformConfig());
        Integer displayOrder = request.getDisplayOrder();
        if (displayOrder == null) {
            displayOrder = 0;
        }

        LeadIngestionFieldMapping mapping = LeadIngestionFieldMapping.builder()
            .tenantId(tenantId)
            .ingestionConfigId(configId)
            .sourcePath(normalizedSourcePath)
            .targetType(request.getTargetType())
            .targetField(normalizedTargetField)
            .transformType(request.getTransformType() == null ? LeadIngestionTransformType.NONE : request.getTransformType())
            .transformConfig(request.getTransformConfig())
            .defaultValue(request.getDefaultValue())
            .required(Boolean.TRUE.equals(request.getRequired()))
            .active(request.getActive() == null ? Boolean.TRUE : request.getActive())
            .displayOrder(displayOrder)
            .build();

        LeadIngestionFieldMapping saved = leadIngestionFieldMappingRepository.save(mapping);
        return toResponse(saved);
    }

    @Transactional
    public LeadIngestionFieldMappingResponse updateMapping(UUID tenantId, UUID configId, UUID mappingId, LeadIngestionFieldMappingRequest request) {
        ensureConfig(configId, tenantId);
        String normalizedSourcePath = normalizePath(request.getSourcePath());
        validateSourcePath(request.getSourcePath(), normalizedSourcePath);
        String normalizedTargetField = normalizeTargetField(request.getTargetField());
        validateTarget(tenantId, request.getTargetType(), normalizedTargetField);
        ensureNoDuplicateTarget(tenantId, configId, request.getTargetType(), normalizedTargetField, mappingId);
        validateTransformConfig(request.getTransformType(), request.getTransformConfig());
        Integer displayOrder = request.getDisplayOrder();
        if (displayOrder == null) {
            displayOrder = 0;
        }

        LeadIngestionFieldMapping mapping = leadIngestionFieldMappingRepository
            .findByIdAndTenantIdAndIngestionConfigIdAndDeletedFalse(mappingId, tenantId, configId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Mapping not found"));

        mapping.setSourcePath(normalizedSourcePath);
        mapping.setTargetType(request.getTargetType());
        mapping.setTargetField(normalizedTargetField);
        mapping.setTransformType(request.getTransformType() == null ? LeadIngestionTransformType.NONE : request.getTransformType());
        mapping.setTransformConfig(request.getTransformConfig());
        mapping.setDefaultValue(request.getDefaultValue());
        mapping.setRequired(Boolean.TRUE.equals(request.getRequired()));
        mapping.setActive(request.getActive() == null ? Boolean.TRUE : request.getActive());
        mapping.setDisplayOrder(displayOrder);
        mapping.setUpdatedAt(Instant.now());

        LeadIngestionFieldMapping saved = leadIngestionFieldMappingRepository.save(mapping);
        return toResponse(saved);
    }

    @Transactional
    public void deleteMapping(UUID tenantId, UUID userId, UUID configId, UUID mappingId) {
        ensureConfig(configId, tenantId);

        LeadIngestionFieldMapping mapping = leadIngestionFieldMappingRepository
            .findByIdAndTenantIdAndIngestionConfigIdAndDeletedFalse(mappingId, tenantId, configId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Mapping not found"));

        mapping.setActive(false);
        mapping.softDelete(userId);
        leadIngestionFieldMappingRepository.save(mapping);
    }

    @Transactional(readOnly = true)
    public MappedLeadData preview(UUID tenantId, UUID configId, UUID eventId) {
        ensureConfig(configId, tenantId);

        LeadIngestionEvent event = leadIngestionEventRepository
            .findByIdAndTenantIdAndIngestionConfigIdAndDeletedFalse(eventId, tenantId, configId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Ingestion event not found"));

        List<LeadIngestionFieldMapping> mappings = leadIngestionFieldMappingRepository
            .findByTenantIdAndIngestionConfigIdAndDeletedFalseOrderByDisplayOrderAscCreatedAtAsc(tenantId, configId)
            .stream()
            .filter(mapping -> Boolean.TRUE.equals(mapping.getActive()))
            .toList();

        Map<String, Object> standardFields = new LinkedHashMap<>();
        Map<String, Object> systemFields = new LinkedHashMap<>();
        Map<String, Object> customFields = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        JsonNode payloadNode = objectMapper.valueToTree(event.getRawPayload());

        for (LeadIngestionFieldMapping mapping : mappings) {
            JsonNode extractedNode = jsonPathValueExtractor.extractNode(payloadNode, mapping.getSourcePath());
            Object rawValue = extractedNode == null || extractedNode.isNull()
                ? null
                : objectMapper.convertValue(extractedNode, Object.class);
            Object value;
            try {
                value = applyTransform(rawValue, mapping.getTransformType(), mapping.getTransformConfig());
            } catch (BusinessException ex) {
                errors.add("Transform failed for target " + mapping.getTargetType() + ":" + mapping.getTargetField()
                    + " from sourcePath=" + mapping.getSourcePath() + " — " + ex.getMessage());
                continue;
            }

            if ((value == null || (value instanceof String str && str.isBlank())) && mapping.getDefaultValue() != null) {
                value = mapping.getDefaultValue();
            }

            if (mapping.getRequired() && (value == null || (value instanceof String str && str.isBlank()))) {
                errors.add("Required mapped value missing for target " + mapping.getTargetType() + ":" + mapping.getTargetField()
                    + " from sourcePath=" + mapping.getSourcePath());
                continue;
            }

            if (value == null) {
                continue;
            }

            if (mapping.getTargetType() == LeadIngestionTargetType.STANDARD_FIELD) {
                standardFields.put(mapping.getTargetField(), value);
                continue;
            }

            if (mapping.getTargetType() == LeadIngestionTargetType.SYSTEM_FIELD) {
                systemFields.put(mapping.getTargetField(), value);
                continue;
            }

            customFields.put(mapping.getTargetField(), value);
        }

        return MappedLeadData.builder()
            .standardFields(standardFields)
            .systemFields(systemFields)
            .customFields(customFields)
            .errors(errors)
            .build();
    }

    @Transactional(readOnly = true)
    public MappedLeadData previewFromPayload(UUID tenantId, UUID configId, Map<String, Object> rawPayload) {
        ensureConfig(configId, tenantId);

        List<LeadIngestionFieldMapping> mappings = leadIngestionFieldMappingRepository
            .findByTenantIdAndIngestionConfigIdAndDeletedFalseOrderByDisplayOrderAscCreatedAtAsc(tenantId, configId)
            .stream()
            .filter(mapping -> Boolean.TRUE.equals(mapping.getActive()))
            .toList();

        Map<String, Object> standardFields = new LinkedHashMap<>();
        Map<String, Object> systemFields = new LinkedHashMap<>();
        Map<String, Object> customFields = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        JsonNode payloadNode = objectMapper.valueToTree(rawPayload == null ? Map.of() : rawPayload);

        for (LeadIngestionFieldMapping mapping : mappings) {
            JsonNode extractedNode = jsonPathValueExtractor.extractNode(payloadNode, mapping.getSourcePath());
            Object rawValue = extractedNode == null || extractedNode.isNull()
                ? null
                : objectMapper.convertValue(extractedNode, Object.class);
            Object value;
            try {
                value = applyTransform(rawValue, mapping.getTransformType(), mapping.getTransformConfig());
            } catch (BusinessException ex) {
                errors.add("Transform failed for target " + mapping.getTargetType() + ":" + mapping.getTargetField()
                    + " from sourcePath=" + mapping.getSourcePath() + " — " + ex.getMessage());
                continue;
            }

            if ((value == null || (value instanceof String str && str.isBlank())) && mapping.getDefaultValue() != null) {
                value = mapping.getDefaultValue();
            }

            if (mapping.getRequired() && (value == null || (value instanceof String str && str.isBlank()))) {
                errors.add("Required mapped value missing for target " + mapping.getTargetType() + ":" + mapping.getTargetField()
                    + " from sourcePath=" + mapping.getSourcePath());
                continue;
            }

            if (value == null) {
                continue;
            }

            if (mapping.getTargetType() == LeadIngestionTargetType.STANDARD_FIELD) {
                standardFields.put(mapping.getTargetField(), value);
                continue;
            }

            if (mapping.getTargetType() == LeadIngestionTargetType.SYSTEM_FIELD) {
                systemFields.put(mapping.getTargetField(), value);
                continue;
            }

            customFields.put(mapping.getTargetField(), value);
        }

        return MappedLeadData.builder()
            .standardFields(standardFields)
            .systemFields(systemFields)
            .customFields(customFields)
            .errors(errors)
            .build();
    }

    @Transactional(readOnly = true)
    public LeadIngestionEvent findEventForDiscovery(UUID tenantId, UUID configId, UUID eventId) {
        ensureConfig(configId, tenantId);

        return leadIngestionEventRepository
            .findByIdAndTenantIdAndIngestionConfigIdAndDeletedFalse(eventId, tenantId, configId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Ingestion event not found"));
    }

    private LeadIngestionConfig ensureConfig(UUID configId, UUID tenantId) {
        return leadIngestionConfigRepository.findByIdAndTenantIdAndDeletedFalse(configId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Ingestion config not found"));
    }

    private void validateTarget(UUID tenantId, LeadIngestionTargetType targetType, String targetField) {
        if (!leadIngestionTargetFieldService.isSupportedTarget(targetType, targetField, tenantId)) {
            throw new BusinessException("VALIDATION_ERROR", "Unsupported target field for mapping: " + targetType + ":" + targetField);
        }
    }

    private void ensureNoDuplicateTarget(
            UUID tenantId,
            UUID configId,
            LeadIngestionTargetType targetType,
            String targetField,
            UUID excludeMappingId) {
        boolean duplicateExists = excludeMappingId == null
            ? leadIngestionFieldMappingRepository
                .findByTenantIdAndIngestionConfigIdAndTargetTypeAndTargetFieldAndDeletedFalse(
                    tenantId,
                    configId,
                    targetType,
                    targetField)
                .filter(existing -> Boolean.TRUE.equals(existing.getActive()))
                .isPresent()
            : leadIngestionFieldMappingRepository
                .findDuplicateTargetExcludingId(
                    tenantId,
                    configId,
                    targetType,
                    targetField,
                    excludeMappingId)
                .filter(existing -> Boolean.TRUE.equals(existing.getActive()))
                .isPresent();

        if (duplicateExists) {
            throw new BusinessException(
                "VALIDATION_ERROR",
                "Duplicate mapping target is not allowed for config: " + targetType + ":" + targetField);
        }
    }

    private String normalizePath(String path) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String normalizeTargetField(String targetField) {
        return targetField == null ? "" : targetField.trim();
    }

    private void validateSourcePath(String originalPath, String normalizedPath) {
        String candidate = originalPath == null ? "" : originalPath.trim();

        if (candidate.isBlank() || normalizedPath.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Source path is required");
        }

        List<String> forbiddenTokens = Arrays.asList("*", "[", "]", "?(", "..", "(", ")");
        for (String token : forbiddenTokens) {
            if (candidate.contains(token)) {
                throw new BusinessException("VALIDATION_ERROR", "Unsupported source path syntax");
            }
        }

        if (!normalizedPath.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")) {
            throw new BusinessException("VALIDATION_ERROR", "Unsupported source path syntax");
        }
    }

    private void validateTransformConfig(LeadIngestionTransformType transformType, Map<String, Object> transformConfig) {
        if (transformConfig == null || transformConfig.isEmpty()) {
            return;
        }
        if (transformConfig.size() > 20) {
            throw new BusinessException("VALIDATION_ERROR", "Transform config is too large");
        }
        Object chainObj = transformConfig.get("chain");
        if (chainObj != null) {
            if (!(chainObj instanceof List<?> chain)) {
                throw new BusinessException("VALIDATION_ERROR", "Transform chain must be an array");
            }
            if (chain.size() > 10) {
                throw new BusinessException("VALIDATION_ERROR", "Transform chain too long");
            }
            for (Object item : chain) {
                if (!(item instanceof String s) || s.isBlank()) {
                    throw new BusinessException("VALIDATION_ERROR", "Invalid transform in chain");
                }
                try {
                    LeadIngestionTransformType.valueOf(s.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw new BusinessException("VALIDATION_ERROR", "Invalid transform in chain: " + s);
                }
            }
        }
        Object prefixObj = transformConfig.get("prefix");
        if (prefixObj != null && !(prefixObj instanceof String)) {
            throw new BusinessException("VALIDATION_ERROR", "Transform prefix must be a string");
        }
        Object suffixObj = transformConfig.get("suffix");
        if (suffixObj != null && !(suffixObj instanceof String)) {
            throw new BusinessException("VALIDATION_ERROR", "Transform suffix must be a string");
        }
        Object regexObj = transformConfig.get("regex");
        if (regexObj != null) {
            if (!(regexObj instanceof Map<?,?> regexMap)) {
                throw new BusinessException("VALIDATION_ERROR", "Transform regex must be an object");
            }
            Object patternObj = regexMap.get("pattern");
            if (patternObj instanceof String pattern && !pattern.isBlank()) {
                try {
                    java.util.regex.Pattern.compile(pattern);
                } catch (Exception ex) {
                    throw new BusinessException("VALIDATION_ERROR", "Invalid regex pattern: " + pattern);
                }
            }
        }
        if (transformConfig.containsKey("pattern")) {
            Object patternObj = transformConfig.get("pattern");
            if (patternObj instanceof String pattern && !pattern.isBlank()) {
                try {
                    java.util.regex.Pattern.compile(pattern);
                } catch (Exception ex) {
                    throw new BusinessException("VALIDATION_ERROR", "Invalid regex pattern: " + pattern);
                }
            }
        }
    }

    private Object applyTransform(Object value, LeadIngestionTransformType transformType, Map<String, Object> transformConfig) {
        Object current = value;
        // Primary transformType
        current = applySingleTransform(current, transformType);

        if (transformConfig == null || transformConfig.isEmpty()) {
            return current;
        }

        // Chain: transformConfig.chain = ["TRIM","LOWERCASE",...]
        Object chainObj = transformConfig.get("chain");
        if (chainObj instanceof List<?> chain) {
            for (Object item : chain) {
                if (item instanceof String s) {
                    LeadIngestionTransformType t;
                    try {
                        t = LeadIngestionTransformType.valueOf(s.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new BusinessException("VALIDATION_ERROR", "Invalid transform in chain: " + s);
                    }
                    current = applySingleTransform(current, t);
                }
            }
        }

        // Prefix / suffix (string-only, applied after chain)
        if (current instanceof String str) {
            Object prefixObj = transformConfig.get("prefix");
            if (prefixObj instanceof String prefix && !prefix.isEmpty()) {
                str = prefix + str;
            }
            Object suffixObj = transformConfig.get("suffix");
            if (suffixObj instanceof String suffix && !suffix.isEmpty()) {
                str = str + suffix;
            }
            current = str;
        }

        // Regex replace: transformConfig.regex = { "pattern": "...", "replacement": "..." }
        Object regexObj = transformConfig.get("regex");
        if (regexObj instanceof Map<?,?> regexMap) {
            Object patternObj = regexMap.get("pattern");
            Object replacementObj = regexMap.get("replacement");
            if (patternObj instanceof String pattern && pattern != null) {
                String replacement = replacementObj instanceof String r ? r : "";
                if (current instanceof String str) {
                    try {
                        str = str.replaceAll(pattern, replacement);
                    } catch (Exception ex) {
                        throw new BusinessException("VALIDATION_ERROR", "Invalid regex pattern: " + pattern + " — " + ex.getMessage());
                    }
                    current = str;
                }
            }
        }
        // Also support top-level pattern/replacement for convenience
        if (transformConfig.containsKey("pattern") && current instanceof String str) {
            Object patternObj = transformConfig.get("pattern");
            Object replacementObj = transformConfig.get("replacement");
            if (patternObj instanceof String pattern) {
                String replacement = replacementObj instanceof String r ? r : "";
                try {
                    str = str.replaceAll(pattern, replacement);
                    current = str;
                } catch (Exception ex) {
                    throw new BusinessException("VALIDATION_ERROR", "Invalid regex pattern: " + pattern + " — " + ex.getMessage());
                }
            }
        }

        return current;
    }

    private Object applySingleTransform(Object value, LeadIngestionTransformType transformType) {
        if (value == null || transformType == null || transformType == LeadIngestionTransformType.NONE) {
            return value;
        }
        if (!(value instanceof String strValue)) {
            return value;
        }
        return switch (transformType) {
            case TRIM -> strValue.trim();
            case LOWERCASE -> strValue.toLowerCase(Locale.ROOT);
            case UPPERCASE -> strValue.toUpperCase(Locale.ROOT);
            case NONE -> strValue;
        };
    }


    private LeadIngestionFieldMappingResponse toResponse(LeadIngestionFieldMapping mapping) {
        return LeadIngestionFieldMappingResponse.builder()
            .id(mapping.getId())
            .sourcePath(mapping.getSourcePath())
            .targetType(mapping.getTargetType())
            .targetField(mapping.getTargetField())
            .transformType(mapping.getTransformType())
            .transformConfig(mapping.getTransformConfig())
            .defaultValue(mapping.getDefaultValue())
            .required(mapping.getRequired())
            .active(mapping.getActive())
            .displayOrder(mapping.getDisplayOrder())
            .createdAt(mapping.getCreatedAt())
            .updatedAt(mapping.getUpdatedAt())
            .build();
    }
}
