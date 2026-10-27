package com.shivang.crm.modules.feedback.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * L3.5: single-use public token generation and hashing.
 *
 * <p>32 random bytes give 256 bits of entropy; base64url without padding keeps
 * the token URL-safe and is the only form ever returned to the client. The
 * database stores SHA-256 hex. A fast hash is appropriate here because the input
 * is high-entropy random data, not a user-chosen password.
 */
@Component
public class FeedbackTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    public String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public String tokenPath(String rawToken) {
        return "/feedback/" + rawToken;
    }
}
