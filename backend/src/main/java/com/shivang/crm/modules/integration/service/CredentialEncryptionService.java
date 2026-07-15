package com.shivang.crm.modules.integration.service;

public interface CredentialEncryptionService {
    String encrypt(String value);
    String decrypt(String encryptedValue);
}
