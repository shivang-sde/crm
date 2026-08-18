package com.shivang.crm.modules.workflow.service;

import lombok.Getter;

@Getter
public class WorkflowRuntimeException extends RuntimeException {

    private final String errorCode;
    private final WorkflowFailureDisposition disposition;

    public WorkflowRuntimeException(String errorCode, String message) {
        this(errorCode, message, WorkflowFailureDisposition.NON_RETRYABLE);
    }

    public WorkflowRuntimeException(String errorCode, String message, WorkflowFailureDisposition disposition) {
        super(message);
        this.errorCode = errorCode;
        this.disposition = disposition;
    }
}