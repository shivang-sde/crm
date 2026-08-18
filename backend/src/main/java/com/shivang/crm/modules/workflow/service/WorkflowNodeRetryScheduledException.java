package com.shivang.crm.modules.workflow.service;

public class WorkflowNodeRetryScheduledException extends WorkflowRuntimeException {

    public WorkflowNodeRetryScheduledException(String message) {
        super("WORKFLOW_NODE_RETRY_SCHEDULED", message, WorkflowFailureDisposition.RETRYABLE);
    }
}