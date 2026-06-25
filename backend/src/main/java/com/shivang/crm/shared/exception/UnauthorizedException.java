package com.shivang.crm.shared.exception;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String errorCode, String message) {
        super(errorCode, message);
    }
}
