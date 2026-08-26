package com.shivang.crm.modules.workflow.service;

import lombok.Getter;
import tools.jackson.core.JacksonException;

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

    WorkflowRuntimeException(String workflow_context_serialization_failed, String unable_to_serialize_canonical_event_metad, JacksonException ex) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}