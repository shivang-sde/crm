package com.shivang.crm.modules.integration.service;

import java.util.Map;

public interface TemplateResolver {
    String resolve(String template, Map<String, Object> context);
}
