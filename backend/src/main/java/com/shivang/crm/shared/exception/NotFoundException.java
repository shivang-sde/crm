package com.shivang.crm.shared.exception;

public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }

    public NotFoundException(String resource, String identifier) {
        super("NOT_FOUND", resource + " not found with identifier: " + identifier);
    }
}
