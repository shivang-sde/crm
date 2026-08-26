package com.shivang.crm.modules.workflow.service;

import java.time.Instant;

import lombok.Getter;

/**
 * Thrown by the WAIT node when its resume time is in the future.
 *
 * Carries the scheduled resume instant so the runtime can persist
 * {@code nextAttemptAt = resumeAt} and return the execution to PENDING,
 * letting the existing dispatcher resume it once due.
 */
@Getter
public class WorkflowWaitScheduledException extends WorkflowRuntimeException {

    private final Instant resumeAt;

    public WorkflowWaitScheduledException(Instant resumeAt) {
        super("WORKFLOW_WAIT_SCHEDULED", "Workflow is waiting until " + resumeAt);
        this.resumeAt = resumeAt;
    }
}
