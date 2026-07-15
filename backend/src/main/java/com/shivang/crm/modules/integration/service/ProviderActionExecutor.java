package com.shivang.crm.modules.integration.service;

import java.util.Map;

import com.shivang.crm.modules.integration.dto.ConnectorExecutionContext;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionResult;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ProviderActionDefinition;

public interface ProviderActionExecutor {
    ConnectorExecutionResult execute(ConnectorExecutionContext context,
                                     ProviderActionDefinition actionDefinition,
                                     ConnectorInstance connectorInstance,
                                     Map<String, Object> credentials);
}
