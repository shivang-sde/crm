package com.shivang.crm.modules.workflow.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;

@Component
public class WorkflowNodeExecutorRegistry {

    private final Map<WorkflowNodeType, WorkflowNodeExecutor> executors;

    public WorkflowNodeExecutorRegistry(List<WorkflowNodeExecutorRegistrationProvider> providers) {
        EnumMap<WorkflowNodeType, WorkflowNodeExecutor> resolved = new EnumMap<>(WorkflowNodeType.class);
        providers.forEach(provider -> {
            WorkflowNodeExecutorRegistration registration = provider.registration();
            resolved.put(registration.nodeType(), registration.executor());
        });
        this.executors = Map.copyOf(resolved);
    }

    public WorkflowNodeExecutor get(WorkflowNodeType nodeType) {
        WorkflowNodeExecutor executor = executors.get(nodeType);
        if (executor == null) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_NODE_TYPE_NOT_SUPPORTED",
                "Node type is not supported by the current runtime: " + nodeType
            );
        }
        return executor;
    }
}