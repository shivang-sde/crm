package com.shivang.crm.modules.records.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.records.entity.MappingTransform;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordMappingProfile;
import com.shivang.crm.modules.records.entity.MappingProfileMode;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecordMappingService {

    public static final int MAX_PATH_LENGTH = 500;
    public static final int MAX_PATH_DEPTH = 10;
    public static final int MAX_MAPPINGS = 100;
    public static final int MAX_PREVIEW_PAYLOAD_BYTES = 256 * 1024;

    /**
     * Pure mapping: external payload + profile -> canonical data Map
     * No DB writes, no events, no side effects.
     */
    public Map<String, Object> map(RecordMappingProfile profile, Map<String, Object> payload, Map<UUID, RecordField> fieldById) {
        if (payload == null) payload = Map.of();
        // Preview size guard
        if (payload.toString().length() > MAX_PREVIEW_PAYLOAD_BYTES) {
            throw new BusinessException("PAYLOAD_TOO_LARGE", "Preview payload exceeds 256KB limit");
        }
        MappingProfileMode mode = profile.getMode();
        if (mode == MappingProfileMode.DIRECT) {
            return mapDirect(payload, fieldById);
        } else {
            return mapCustom(profile, payload, fieldById);
        }
    }

    private Map<String, Object> mapDirect(Map<String, Object> payload, Map<UUID, RecordField> fieldById) {
        Map<String, Object> result = new HashMap<>();
        Map<String, RecordField> byKey = new HashMap<>();
        for (RecordField f : fieldById.values()) byKey.put(f.getFieldKey(), f);
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (byKey.containsKey(e.getKey())) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapCustom(RecordMappingProfile profile, Map<String, Object> payload, Map<UUID, RecordField> fieldById) {
        Map<String, Object> config = profile.getConfiguration();
        if (config == null) return Map.of();
        Object mappingsObj = config.get("mappings");
        if (!(mappingsObj instanceof java.util.List)) return Map.of();
        java.util.List<Map<String, Object>> mappings = (java.util.List<Map<String, Object>>) mappingsObj;
        if (mappings.size() > MAX_MAPPINGS) {
            throw new BusinessException("TOO_MANY_MAPPINGS", "Too many mappings: " + mappings.size() + " max " + MAX_MAPPINGS);
        }
        Map<String, Object> result = new HashMap<>();
        for (Map<String, Object> m : mappings) {
            Object sourceObj = m.get("source");
            Object targetFieldIdObj = m.get("targetFieldId");
            if (sourceObj == null || targetFieldIdObj == null) continue;
            String source = sourceObj.toString().trim();
            String targetFieldIdStr = targetFieldIdObj.toString().trim();
            if (source.isEmpty() || targetFieldIdStr.isEmpty()) continue;
            UUID targetFieldId;
            try { targetFieldId = UUID.fromString(targetFieldIdStr); } catch (Exception ex) { continue; }
            RecordField field = fieldById.get(targetFieldId);
            if (field == null) continue;
            Object rawValue = resolvePath(payload, source);
            if (rawValue == null) continue; // missing source or explicit null -> omit per null semantics
            // Array encountered while traversing scalar mapping -> omit deterministically (resolvePath returns null for non-Map)
            // Transformation
            String transformStr = m.get("transform") != null ? m.get("transform").toString() : null;
            MappingTransform transform = MappingTransform.fromString(transformStr);
            Object transformed;
            try {
                transformed = applyTransform(rawValue, transform, field);
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BusinessException("TRANSFORMATION_ERROR", "Failed to transform source '" + source + "' for field '" + field.getFieldKey() + "': " + ex.getMessage());
            }
            result.put(field.getFieldKey(), transformed);
        }
        return result;
    }

    private Object applyTransform(Object value, MappingTransform transform, RecordField field) {
        if (transform == null || transform == MappingTransform.IDENTITY) return value;
        switch (transform) {
            case STRING:
                if (value instanceof String) return value;
                if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
                throw new BusinessException("TRANSFORMATION_ERROR", "STRING transform cannot serialize object/array for field '" + field.getFieldKey() + "'");
            case INTEGER:
                if (value instanceof Integer || value instanceof Long) return value;
                if (value instanceof Number n) {
                    double d = n.doubleValue();
                    if (d != Math.floor(d)) throw new BusinessException("TRANSFORMATION_ERROR", "INTEGER transform: value not integral for field '" + field.getFieldKey() + "'");
                    return n.longValue();
                }
                if (value instanceof String s) {
                    String t = s.trim();
                    if (t.isEmpty()) throw new BusinessException("TRANSFORMATION_ERROR", "INTEGER transform: empty string");
                    try {
                        if (t.contains(".")) throw new NumberFormatException();
                        return Long.parseLong(t);
                    } catch (NumberFormatException e) {
                        throw new BusinessException("TRANSFORMATION_ERROR", "INTEGER transform: '" + t + "' is not integral");
                    }
                }
                throw new BusinessException("TRANSFORMATION_ERROR", "INTEGER transform: unsupported type");
            case DECIMAL:
                if (value instanceof Number) return value;
                if (value instanceof String s) {
                    try { return new BigDecimal(s.trim()); } catch (Exception e) {
                        throw new BusinessException("TRANSFORMATION_ERROR", "DECIMAL transform: invalid decimal '" + s + "'");
                    }
                }
                throw new BusinessException("TRANSFORMATION_ERROR", "DECIMAL transform: unsupported type");
            case BOOLEAN:
                if (value instanceof Boolean) return value;
                if (value instanceof String s) {
                    String n = s.trim().toLowerCase();
                    if ("true".equals(n)) return true;
                    if ("false".equals(n)) return false;
                    throw new BusinessException("TRANSFORMATION_ERROR", "BOOLEAN transform: must be true/false");
                }
                throw new BusinessException("TRANSFORMATION_ERROR", "BOOLEAN transform: unsupported type");
            case DATE:
                if (!(value instanceof String s)) throw new BusinessException("TRANSFORMATION_ERROR", "DATE transform: must be string yyyy-MM-dd");
                try { LocalDate.parse(s.trim()); return s.trim(); } catch (DateTimeParseException e) {
                    throw new BusinessException("TRANSFORMATION_ERROR", "DATE transform: invalid date '" + s + "'");
                }
            case DATETIME:
                if (!(value instanceof String s)) throw new BusinessException("TRANSFORMATION_ERROR", "DATETIME transform: must be string ISO-8601");
                String t = s.trim();
                try { Instant.parse(t); return t; } catch (DateTimeParseException e) {
                    try { java.time.LocalDateTime.parse(t); return t; } catch (DateTimeParseException e2) {
                        throw new BusinessException("TRANSFORMATION_ERROR", "DATETIME transform: invalid datetime '" + t + "'");
                    }
                }
            case ENUM:
                if (!(value instanceof String)) throw new BusinessException("TRANSFORMATION_ERROR", "ENUM transform: must be string");
                return ((String) value).trim();
            case IDENTITY:
            default:
                return value;
        }
    }

    @SuppressWarnings("unchecked")
    private Object resolvePath(Map<String, Object> payload, String path) {
        if (path.length() > MAX_PATH_LENGTH) return null;
        String[] parts = path.split("\\.", -1);
        if (parts.length > MAX_PATH_DEPTH) return null;
        for (String p : parts) if (p.isEmpty()) return null;
        Object current = payload;
        for (String part : parts) {
            if (!(current instanceof Map)) return null; // array or scalar encountered -> omit deterministically
            Map<String, Object> map = (Map<String, Object>) current;
            if (!map.containsKey(part)) return null;
            current = map.get(part);
            if (current == null) return null; // explicit null -> omit
        }
        return current;
    }
}
