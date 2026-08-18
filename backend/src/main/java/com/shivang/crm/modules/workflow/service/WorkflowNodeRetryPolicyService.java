package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.shivang.crm.modules.workflow.entity.WorkflowNode;

@Service
public class WorkflowNodeRetryPolicyService {

    private final WorkflowNodeRetryPolicy defaults;

    public WorkflowNodeRetryPolicyService(
        @Value("${app.workflow-runtime.retry.enabled:false}") boolean enabled,
        @Value("${app.workflow-runtime.retry.max-attempts:3}") int maxAttempts,
        @Value("${app.workflow-runtime.retry.initial-delay-seconds:5}") long initialDelaySeconds,
        @Value("${app.workflow-runtime.retry.max-delay-seconds:300}") long maxDelaySeconds,
        @Value("${app.workflow-runtime.retry.jitter:true}") boolean jitter
    ) {
        this.defaults = normalize(enabled, maxAttempts, initialDelaySeconds, maxDelaySeconds, jitter);
    }

    public WorkflowNodeRetryPolicy resolve(WorkflowNode node) {
        Map<String, Object> configuration = node.getConfiguration();
        if (configuration == null || !(configuration.get("retry") instanceof Map<?, ?> retry)) {
            return defaults;
        }
        return normalize(
            booleanValue(retry.get("enabled"), defaults.enabled()),
            intValue(retry.get("maxAttempts"), defaults.maxAttempts()),
            longValue(retry.get("initialDelaySeconds"), defaults.initialDelaySeconds()),
            longValue(retry.get("maxDelaySeconds"), defaults.maxDelaySeconds()),
            booleanValue(retry.get("jitter"), defaults.jitter())
        );
    }

    public long delaySeconds(WorkflowNodeRetryPolicy policy, int attempt) {
        long exponent = Math.min(Math.max(attempt - 1L, 0L), 30L);
        long multiplier = 1L << exponent;
        long delay = safeMultiply(policy.initialDelaySeconds(), multiplier);
        delay = Math.min(delay, policy.maxDelaySeconds());
        if (policy.jitter() && delay > 0) {
            long jitter = ThreadLocalRandom.current().nextLong(delay + 1);
            delay = Math.min(policy.maxDelaySeconds(), delay + jitter);
        }
        return delay;
    }

    private long safeMultiply(long left, long right) {
        if (left == 0 || right == 0) return 0;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private WorkflowNodeRetryPolicy normalize(boolean enabled, int maxAttempts, long initial, long maximum, boolean jitter) {
        return new WorkflowNodeRetryPolicy(
            enabled,
            Math.max(1, Math.min(maxAttempts, 100)),
            Math.max(0, Math.min(initial, 86400)),
            Math.max(0, Math.min(Math.max(initial, maximum), 7 * 86400)),
            jitter
        );
    }

    private boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private long longValue(Object value, long fallback) {
        try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }
}