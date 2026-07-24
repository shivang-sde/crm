package com.shivang.crm.modules.integration.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.shivang.crm.modules.integration.exception.CredentialEncryptionException;
import com.shivang.crm.modules.integration.service.CredentialEncryptionService;

@Service
public class DefaultCredentialEncryptionService implements CredentialEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey secretKey;

    public DefaultCredentialEncryptionService(@Value("${integration.security.encryption-key:}") String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new CredentialEncryptionException("Missing encryption key configuration: integration.security.encryption-key");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encryptionKey);
        } catch (IllegalArgumentException ex) {
            throw new CredentialEncryptionException("Encryption key must be a Base64-encoded 32-byte value", ex);
        }
        if (keyBytes.length != 32) {
            throw new CredentialEncryptionException("Encryption key must decode to a 32-byte value for AES-256");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (javax.crypto.IllegalBlockSizeException | javax.crypto.BadPaddingException | java.security.InvalidKeyException | java.security.InvalidAlgorithmParameterException | java.security.NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException ex) {
            throw new CredentialEncryptionException("Failed to encrypt secret", ex);
        }
    }

    @Override
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedValue);
            if (payload.length < IV_LENGTH_BYTES + 1) {
                throw new IllegalArgumentException("Encrypted payload is too short");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[payload.length - IV_LENGTH_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(payload, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | javax.crypto.IllegalBlockSizeException | javax.crypto.BadPaddingException | java.security.InvalidKeyException | java.security.InvalidAlgorithmParameterException | java.security.NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException ex) {
            throw new CredentialEncryptionException("Failed to decrypt secret", ex);
        }
    }

    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        return "***" + value.substring(value.length() - 4);
    }
}
