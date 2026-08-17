package com.shivang.crm.modules.workflow.service;

import lombok.Getter;

@Getter
public class WorkflowTaskCreationException extends RuntimeException {

    private final String errorCode;

    public WorkflowTaskCreationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}