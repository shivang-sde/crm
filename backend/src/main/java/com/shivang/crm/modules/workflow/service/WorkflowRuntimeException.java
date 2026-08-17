package com.shivang.crm.modules.workflow.service;

import lombok.Getter;

@Getter
public class WorkflowRuntimeException extends RuntimeException {

    private final String errorCode;

    public WorkflowRuntimeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}