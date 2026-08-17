package com.shivang.crm.modules.acquisition.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionSourceField;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionSourceFieldType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeadIngestionFieldDiscoveryService {

    private final ObjectMapper objectMapper;

    public List<LeadIngestionSourceField> discoverFields(Map<String, Object> payload) {
        List<LeadIngestionSourceField> fields = new ArrayList<>();
        if (payload == null || payload.isEmpty()) {
            return fields;
        }

        JsonNode root = objectMapper.valueToTree(payload);
        discoverLeafFields(root, "", (path, node) -> fields.add(LeadIngestionSourceField.builder()
            .path(path)
            .sampleValue(node == null || node.isNull() ? null : objectMapper.convertValue(node, Object.class))
            .detectedType(resolveType(node))
            .build()));

        return fields;
    }

    private void discoverLeafFields(
            JsonNode node,
            String currentPath,
            java.util.function.BiConsumer<String, JsonNode> onLeaf) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                String nextPath = currentPath.isBlank() ? fieldName : currentPath + "." + fieldName;
                JsonNode child = node.get(fieldName);

                if (child != null && child.isObject()) {
                    discoverLeafFields(child, nextPath, onLeaf);
                    continue;
                }

                onLeaf.accept(nextPath, child);
            }
            return;
        }

        if (!currentPath.isBlank()) {
            onLeaf.accept(currentPath, node);
        }
    }

    private LeadIngestionSourceFieldType resolveType(JsonNode node) {
        if (node == null || node.isNull()) {
            return LeadIngestionSourceFieldType.NULL;
        }
        if (node.isTextual()) {
            return LeadIngestionSourceFieldType.STRING;
        }
        if (node.isNumber()) {
            return LeadIngestionSourceFieldType.NUMBER;
        }
        if (node.isBoolean()) {
            return LeadIngestionSourceFieldType.BOOLEAN;
        }
        if (node.isArray()) {
            return LeadIngestionSourceFieldType.ARRAY;
        }
        return LeadIngestionSourceFieldType.OBJECT;
    }
}
