package com.shivang.crm.modules.workflow.service;

import lombok.Getter;

@Getter
public class WorkflowOwnerAssignmentException extends RuntimeException {

    private final String errorCode;

    public WorkflowOwnerAssignmentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}