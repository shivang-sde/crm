package com.shivang.crm.modules.integration.exception;

import com.shivang.crm.shared.exception.BusinessException;

public class CredentialEncryptionException extends BusinessException {

    public CredentialEncryptionException(String message) {
        super("CREDENTIAL_ENCRYPTION_ERROR", message);
    }

    public CredentialEncryptionException(String message, Throwable cause) {
        super("CREDENTIAL_ENCRYPTION_ERROR", message);
        initCause(cause);
    }
}
