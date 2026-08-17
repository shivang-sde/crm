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
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                return WorkflowResolvedValue.missing();
            }
            current = map.get(segment);
        }
        return WorkflowResolvedValue.of(current);
    }
}