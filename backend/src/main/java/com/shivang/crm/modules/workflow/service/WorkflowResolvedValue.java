package com.shivang.crm.modules.workflow.service;

public record WorkflowResolvedValue(boolean found, Object value) {

    public static WorkflowResolvedValue missing() {
        return new WorkflowResolvedValue(false, null);
    }

    public static WorkflowResolvedValue of(Object value) {
        return new WorkflowResolvedValue(true, value);
    }
}