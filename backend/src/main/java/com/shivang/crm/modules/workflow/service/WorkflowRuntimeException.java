package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import lombok.Getter;
import tools.jackson.core.JacksonException;

@Getter
public class WorkflowRuntimeException extends RuntimeException {

    private final String errorCode;
    private final WorkflowFailureDisposition disposition;
    private final Map<String, Object> outputContext;

    public WorkflowRuntimeException(String errorCode, String message) {
        this(errorCode, message, WorkflowFailureDisposition.NON_RETRYABLE, null);
    }

    public WorkflowRuntimeException(String errorCode, String message, WorkflowFailureDisposition disposition) {
        this(errorCode, message, disposition, null);
    }

    public WorkflowRuntimeException(String errorCode, String message, Map<String, Object> outputContext) {
        this(errorCode, message, WorkflowFailureDisposition.NON_RETRYABLE, outputContext);
    }

    public WorkflowRuntimeException(String errorCode, String message, WorkflowFailureDisposition disposition, Map<String, Object> outputContext) {
        super(message);
        this.errorCode = errorCode;
        this.disposition = disposition;
        this.outputContext = outputContext == null ? null : Map.copyOf(outputContext);
    }

    WorkflowRuntimeException(String workflow_context_serialization_failed, String unable_to_serialize_canonical_event_metad, JacksonException ex) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}