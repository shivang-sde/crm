package com.shivang.crm.modules.integration.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

@Service
public class WebhookVerificationService {

    public boolean verifyHmacSha256(byte[] body, String secret, String signatureHeader) {
        if (secret == null || secret.isEmpty() || signatureHeader == null || signatureHeader.isEmpty()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(body);
            String hex = bytesToHex(computed);
            String sig = signatureHeader.trim();
            if (sig.startsWith("sha256=")) sig = sig.substring(7);
            // allow raw hex or prefixed
            // constant-time compare
            return MessageDigest.isEqual(hex.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
