package com.shivang.crm.shared.exception;

public class PermissionDeniedException extends BusinessException {

    public PermissionDeniedException(String message) {
        super("PERMISSION_DENIED", message);
    }

    public PermissionDeniedException(String errorCode, String message) {
        super(errorCode, message);
    }
}
