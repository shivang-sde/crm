package com.shivang.crm.modules.workflow.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.workflow.entity.WorkflowEdge;
import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;

/**
 * Pauses workflow progression until a configured ISO-8601 instant.
 *
 * Configuration (JSONB):
 *   { "resumeAt": "2026-08-30T10:30:00Z" }
 *
 * Future resumeAt  -> WorkflowWaitScheduledException (runtime schedules resumption).
 * Due/past resumeAt -> node completes and selects its single outgoing edge.
 */
@Component
public class WaitNodeExecutor implements WorkflowNodeExecutor, WorkflowNodeExecutorRegistrationProvider {

    @Override
    public WorkflowNodeExecutionResult execute(
        WorkflowExecution execution,
        WorkflowNode node,
        List<WorkflowEdge> outgoingEdges,
        WorkflowExecutionContext context
    ) {
        if (outgoingEdges.size() != 1) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_BRANCH_NOT_SUPPORTED",
                "WAIT node must have exactly one outgoing edge"
            );
        }

        Map<String, Object> configuration = node.getConfiguration();
        Instant resumeAt = parseResumeAt(configuration);

        if (resumeAt.isAfter(Instant.now())) {
            throw new WorkflowWaitScheduledException(resumeAt);
        }

        UUID selectedEdgeId = outgoingEdges.get(0).getId();
        return new WorkflowNodeExecutionResult(
            com.shivang.crm.modules.workflow.entity.WorkflowNodeExecutionStatus.COMPLETED,
            Map.of(
                "resumeAt", String.valueOf(resumeAt),
                "selectedEdgeId", String.valueOf(selectedEdgeId)
            ),
            List.of(selectedEdgeId),
            null,
            null
        );
    }

    private Instant parseResumeAt(Map<String, Object> configuration) {
        // Business-friendly duration: if waitType == DURATION, compute resumeAt as now + duration (amount/unit)
        if (configuration != null && "DURATION".equalsIgnoreCase(String.valueOf(configuration.get("waitType")))) {
            Object amountObj = configuration.get("amount");
            Object unitObj = configuration.get("unit");
            if (amountObj == null || unitObj == null) {
                throw new WorkflowRuntimeException("WORKFLOW_WAIT_RESUME_AT_REQUIRED", "WAIT duration requires amount and unit");
            }
            long amount;
            try { amount = Long.parseLong(String.valueOf(amountObj).trim()); } catch (Exception ex) { throw new WorkflowRuntimeException("WORKFLOW_WAIT_RESUME_AT_INVALID", "Invalid wait amount"); }
            if (amount <= 0) throw new WorkflowRuntimeException("WORKFLOW_WAIT_RESUME_AT_INVALID", "Wait amount must be positive");
            String unit = String.valueOf(unitObj).trim().toUpperCase();
            java.time.Duration duration = switch (unit) {
                case "MINUTES", "MINUTE", "M" -> java.time.Duration.ofMinutes(amount);
                case "HOURS", "HOUR", "H" -> java.time.Duration.ofHours(amount);
                case "DAYS", "DAY", "D" -> java.time.Duration.ofDays(amount);
                default -> throw new WorkflowRuntimeException("WORKFLOW_WAIT_RESUME_AT_INVALID", "Unsupported wait unit: " + unit);
            };
            return Instant.now().plus(duration);
        }
        Object rawResumeAt = configuration == null ? null : configuration.get("resumeAt");
        if (rawResumeAt == null || String.valueOf(rawResumeAt).isBlank()) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_WAIT_RESUME_AT_REQUIRED",
                "WAIT nodes require a non-blank resumeAt timestamp"
            );
        }
        try {
            return Instant.parse(String.valueOf(rawResumeAt).trim());
        } catch (DateTimeParseException ex) {
            throw new WorkflowRuntimeException(
                "WORKFLOW_WAIT_RESUME_AT_INVALID",
                "WAIT resumeAt must be an ISO-8601 UTC timestamp, e.g. 2026-08-30T10:30:00Z"
            );
        }
    }

    @Override
    public WorkflowNodeExecutorRegistration registration() {
        return new WorkflowNodeExecutorRegistration(WorkflowNodeType.WAIT, this);
    }
}
