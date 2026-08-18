package com.shivang.crm.modules.workflow.service;

public record WorkflowNodeRetryPolicy(
    boolean enabled,
    int maxAttempts,
    long initialDelaySeconds,
    long maxDelaySeconds,
    boolean jitter
) {
}