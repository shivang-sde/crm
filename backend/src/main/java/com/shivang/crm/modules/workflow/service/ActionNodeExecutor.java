package com.shivang.crm.modules.workflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.workflow.entity.WorkflowEdge;
import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;

@Component
public class ActionNodeExecutor implements WorkflowNodeExecutor, WorkflowNodeExecutorRegistrationProvider {

    // Marker used by OutboundHttpServiceImpl when no HTTP response was received (network/timeout failure);
    // the remote side effect outcome is unknown, so it must not be recorded as a known FAILED idempotency state.
    private static final String AMBIGUOUS_OUTBOUND_HTTP_ERROR_CODE = "OUTBOUND_HTTP_FAILED";

    private final WorkflowActionExecutorRegistry actionExecutorRegistry;
    private final WorkflowValueResolver valueResolver;
    private final WorkflowNodeIdempotencyService idempotencyService;

    public ActionNodeExecutor(
        WorkflowActionExecutorRegistry actionExecutorRegistry,
        WorkflowValueResolver valueResolver,
        WorkflowNodeIdempotencyService idempotencyService
    ) {
        this.actionExecutorRegistry = actionExecutorRegistry;
        this.valueResolver = valueResolver;
        this.idempotencyService = idempotencyService;
    }

    @Override
    public WorkflowNodeExecutionResult execute(
        WorkflowExecution execution,
        WorkflowNode node,
        List<WorkflowEdge> outgoingEdges,
        WorkflowExecutionContext context
    ) {
        if (outgoingEdges.size() != 1) {
            throw new WorkflowRuntimeException("WORKFLOW_BRANCH_NOT_SUPPORTED", "ACTION nodes must have exactly one outgoing edge");
        }
        Map<String, Object> nodeConfiguration = node.getConfiguration();
        if (nodeConfiguration == null || nodeConfiguration.get("actionType") == null) {
            throw new WorkflowRuntimeException("WORKFLOW_ACTION_INVALID_CONFIG", "ACTION node requires actionType");
        }

        String actionType = String.valueOf(nodeConfiguration.get("actionType"));
        Object rawConfiguration = nodeConfiguration.get("config");
        if (rawConfiguration != null && !(rawConfiguration instanceof Map<?, ?>)) {
            throw new WorkflowRuntimeException("WORKFLOW_ACTION_INVALID_CONFIG", "ACTION config must be an object");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> configuration = rawConfiguration == null
            ? Map.of()
            : (Map<String, Object>) rawConfiguration;
        Map<String, Object> resolvedConfiguration = resolveMap(configuration, context);
        if (context.getWorkflowNodeExecutionId() == null) {
            throw new WorkflowRuntimeException("WORKFLOW_IDEMPOTENCY_CLAIM_FAILED", "Workflow node execution identity is missing");
        }

        WorkflowNodeIdempotencyService.WorkflowNodeIdempotencyClaim claim = idempotencyService.claim(
            context.getIdentity().tenantId(),
            context.getExecution().getId(),
            context.getWorkflowNodeExecutionId()
        );
        WorkflowActionExecutionResult result;
        if (!claim.execute()) {
            result = WorkflowActionExecutionResult.completed(
                claim.record().getResult() == null ? Map.of() : claim.record().getResult()
            );
        } else {
            try {
                result = actionExecutorRegistry.get(actionType).execute(context, resolvedConfiguration);
            } catch (WorkflowRuntimeException ex) {
                if (!isAmbiguousOutcome(ex.getErrorCode())) {
                    idempotencyService.fail(claim.record(), ex.getErrorCode(), ex.getMessage());
                }
                throw ex;
            } catch (RuntimeException ex) {
                idempotencyService.fail(claim.record(), "WORKFLOW_ACTION_EXECUTION_FAILED", "Workflow action failed");
                throw ex;
            }
            if (!result.success()) {
                idempotencyService.fail(claim.record(), result.errorCode(), result.errorMessage());
            }
        }
        if (!result.success()) {
            throw new WorkflowRuntimeException(
                result.errorCode() == null ? "WORKFLOW_ACTION_EXECUTION_FAILED" : result.errorCode(),
                result.errorMessage() == null ? "Workflow action failed" : result.errorMessage()
            );
        }
        if (claim.execute()) {
            idempotencyService.complete(claim.record(), result.output());
        }
        UUID edgeId = outgoingEdges.get(0).getId();
        return new WorkflowNodeExecutionResult(
            com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.COMPLETED,
            result.output(),
            List.of(edgeId),
            null,
            null
        );
    }

    private boolean isAmbiguousOutcome(String errorCode) {
        return AMBIGUOUS_OUTBOUND_HTTP_ERROR_CODE.equals(errorCode);
    }

    private Map<String, Object> resolveMap(Map<String, Object> configuration, WorkflowExecutionContext context) {        Map<String, Object> resolved = new java.util.LinkedHashMap<>();
        configuration.forEach((key, value) -> resolved.put(key, resolveValue(value, context)));
        return resolved;
    }

    private Object resolveValue(Object value, WorkflowExecutionContext context) {
        if (value instanceof String text && text.startsWith("{{") && text.endsWith("}}")) {
            String fieldPath = text.substring(2, text.length() - 2).trim();
            WorkflowResolvedValue resolved = valueResolver.resolve(context, fieldPath);
            if (!resolved.found()) {
                throw new WorkflowRuntimeException("WORKFLOW_ACTION_VALUE_RESOLUTION_FAILED", "Action value was not found: " + fieldPath);
            }
            return resolved.value();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new java.util.LinkedHashMap<>();
            map.forEach((key, nestedValue) -> nested.put(String.valueOf(key), resolveValue(nestedValue, context)));
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>();
            list.forEach(item -> resolved.add(resolveValue(item, context)));
            return resolved;
        }
        return value;
    }

    @Override
    public WorkflowNodeExecutorRegistration registration() {
        return new WorkflowNodeExecutorRegistration(WorkflowNodeType.ACTION, this);
    }
}