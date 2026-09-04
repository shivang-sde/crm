package com.shivang.crm.modules.workflow.service;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ContextWorkflowValueResolver implements WorkflowValueResolver {

    @Override
    public WorkflowResolvedValue resolve(WorkflowExecutionContext context, String fieldPath) {
        if (fieldPath == null || fieldPath.isBlank()) {
            return WorkflowResolvedValue.missing();
        }

        String normalizedPath = fieldPath.trim();
        // Credential namespace — execution-only, never persisted
        if (normalizedPath.startsWith("credential.")) {
            String credPath = normalizedPath.substring("credential.".length());
            Map<String, Object> credRoot = context.getCredentialContext();
            if (credRoot == null || credRoot.isEmpty()) return WorkflowResolvedValue.missing();
            // Support nested credential keys like credential.oauth.token via dotted traversal
            return find(credRoot, credPath);
        }
        Map<String, Object> root = context.getEntity();
        if (normalizedPath.startsWith("entity.")) {
            normalizedPath = normalizedPath.substring("entity.".length());
        } else if (normalizedPath.startsWith("trigger.")) {
            normalizedPath = normalizedPath.substring("trigger.".length());
            root = context.getTrigger();
        } else if (normalizedPath.startsWith("nodeOutputs.")) {
            normalizedPath = normalizedPath.substring("nodeOutputs.".length());
            int separator = normalizedPath.indexOf('.');
            String nodeKey = separator < 0 ? normalizedPath : normalizedPath.substring(0, separator);
            root = context.getNodeOutputs().getOrDefault(nodeKey, Map.of());
            normalizedPath = separator < 0 ? "" : normalizedPath.substring(separator + 1);
        }

        WorkflowResolvedValue directValue = find(root, normalizedPath);
        if (directValue.found()) {
            return directValue;
        }

        if (root != context.getEntity()) {
            directValue = find(context.getEntity(), normalizedPath);
            if (directValue.found()) return directValue;
        }

        directValue = find(context.getTrigger(), normalizedPath);
        if (directValue.found()) return directValue;

        for (Map<String, Object> output : context.getNodeOutputs().values()) {
            WorkflowResolvedValue outputValue = find(output, normalizedPath);
            if (outputValue.found()) {
                return outputValue;
            }
        }

        return WorkflowResolvedValue.missing();
    }

    private WorkflowResolvedValue find(Map<String, Object> values, String path) {
        if (values.containsKey(path)) {
            return WorkflowResolvedValue.of(values.get(path));
        }

        Object current = values;
        String[] segments = path.split("\\.");
        for (int i = 0; i < segments.length; i++) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segments[i])) {
                return WorkflowResolvedValue.missing();
            }
            current = map.get(segments[i]);
            // Navigating THROUGH a null related record resolves to a safe null
            // leaf instead of a hard "field not found" failure, so conditions
            // like entity.account.name IS_NULL work for missing relationships.
            if (current == null && i < segments.length - 1) {
                return WorkflowResolvedValue.of(null);
            }
        }
        return WorkflowResolvedValue.of(current);
    }
}