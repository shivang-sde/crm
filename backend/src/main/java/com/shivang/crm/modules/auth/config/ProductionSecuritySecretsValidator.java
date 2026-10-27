package com.shivang.crm.modules.auth.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Fails startup when production security material is absent or is a value that
 * ships in this repository.
 *
 * <p>Two independent layers, because either one alone is insufficient:
 * <ol>
 *   <li>{@code application-prod.yml} uses {@code ${JWT_SECRET}} with no default, so a
 *       missing environment variable already aborts the context during placeholder
 *       resolution. That is the primary guarantee.</li>
 *   <li>This bean is the second layer: it re-checks the resolved values so that a
 *       future edit which re-introduces a fallback default, or a key that is present
 *       but too weak, is caught with an actionable message instead of silently
 *       running on a publicly known key.</li>
 * </ol>
 *
 * <p>Restricted to the {@code prod} profile on purpose: development and test keep
 * their deterministic local secrets so a developer machine needs no key ceremony.
 *
 * <p>Secret material is never included in any message or log emitted here.
 */
@Component
@Profile("prod")
@Slf4j
public class ProductionSecuritySecretsValidator {

    /**
     * Fallback values that must never be active in production. Compared as a
     * constant-time equality so this check does not itself become a timing oracle.
     */
    private static final String SHIPPED_DEFAULT_JWT_SECRET =
            "Y3JtLWFwcGxpY2F0aW9uLXN1cGVyLXNlY3JldC1rZXktdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTY=";
    private static final String SHIPPED_DEFAULT_ENCRYPTION_KEY =
            "XKM1/RmCg8xvU/iYTEY81mZUgZDWeHxwH3Cpqwz1D2w=";

    /** Minimum decoded HMAC key length. HS256 requires 32 bytes; refuse anything smaller. */
    private static final int MIN_JWT_KEY_BYTES = 32;
    /** AES-256 requires exactly 32 bytes. */
    private static final int ENCRYPTION_KEY_BYTES = 32;

    private final String jwtSecret;
    private final String encryptionKey;

    public ProductionSecuritySecretsValidator(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${integration.security.encryption-key}") String encryptionKey) {
        this.jwtSecret = jwtSecret;
        this.encryptionKey = encryptionKey;
    }

    @PostConstruct
    void validate() {
        validateJwtSecret();
        validateEncryptionKey();
        log.info("Production security material validated (presence, format and strength).");
    }

    private void validateJwtSecret() {
        if (isBlank(jwtSecret)) {
            throw new IllegalStateException(
                    "jwt.secret is not configured. Set the JWT_SECRET environment variable to a "
                            + "Base64-encoded random value of at least " + MIN_JWT_KEY_BYTES
                            + " bytes, e.g. `openssl rand -base64 48`.");
        }
        if (constantTimeEquals(jwtSecret.trim(), SHIPPED_DEFAULT_JWT_SECRET)) {
            throw new IllegalStateException(
                    "jwt.secret is still the example value committed to this repository. "
                            + "Set the JWT_SECRET environment variable to a unique random value; "
                            + "anyone holding this key can mint a valid SUPERADMIN token.");
        }
        byte[] decoded = decode(jwtSecret, "jwt.secret");
        if (decoded.length < MIN_JWT_KEY_BYTES) {
            throw new IllegalStateException("jwt.secret must decode to at least " + MIN_JWT_KEY_BYTES
                    + " bytes for HMAC-SHA256 signing; got " + decoded.length + ".");
        }
    }

    private void validateEncryptionKey() {
        if (isBlank(encryptionKey)) {
            throw new IllegalStateException(
                    "integration.security.encryption-key is not configured. Set INTEGRATION_ENCRYPTION_KEY "
                            + "to a Base64-encoded 32-byte value, e.g. `openssl rand -base64 32`.");
        }
        if (constantTimeEquals(encryptionKey.trim(), SHIPPED_DEFAULT_ENCRYPTION_KEY)) {
            throw new IllegalStateException(
                    "integration.security.encryption-key is still the example value committed to this repository. "
                            + "Set INTEGRATION_ENCRYPTION_KEY to a unique random value; every stored connector "
                            + "credential is encrypted with it.");
        }
        byte[] decoded = decode(encryptionKey, "integration.security.encryption-key");
        if (decoded.length != ENCRYPTION_KEY_BYTES) {
            throw new IllegalStateException("integration.security.encryption-key must decode to exactly "
                    + ENCRYPTION_KEY_BYTES + " bytes for AES-256; got " + decoded.length + ".");
        }
    }

    private static byte[] decode(String value, String propertyName) {
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(propertyName + " must be valid Base64.", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Exposed for tests: the set of fallback values that must never be active in prod. */
    public static List<String> shippedDefaults() {
        return Arrays.asList(SHIPPED_DEFAULT_JWT_SECRET, SHIPPED_DEFAULT_ENCRYPTION_KEY);
    }
}
