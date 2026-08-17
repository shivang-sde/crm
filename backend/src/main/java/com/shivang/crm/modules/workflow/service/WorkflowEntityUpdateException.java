package com.shivang.crm.modules.workflow.service;

import lombok.Getter;

@Getter
public class WorkflowEntityUpdateException extends RuntimeException {

    private final String errorCode;

    public WorkflowEntityUpdateException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}