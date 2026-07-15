package com.shivang.crm.modules.integration.service.impl;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.integration.service.TemplateResolver;

@Service
public class SimpleTemplateResolver implements TemplateResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}");

    @Override
    public String resolve(String template, Map<String, Object> context) {
        if (template == null || template.isBlank()) {
            return template;
        }

        if (context == null) {
            context = Map.of();
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = resolveValue(context, key);
            if (value == null) {
                throw new IllegalArgumentException("Unable to resolve template variable: " + key);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private Object resolveValue(Map<String, Object> context, String key) {
        if (context.containsKey(key)) {
            return context.get(key);
        }

        String[] parts = key.split("\\.");
        Object current = context;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
