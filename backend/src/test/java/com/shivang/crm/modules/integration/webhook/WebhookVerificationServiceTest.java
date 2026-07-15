package com.shivang.crm.modules.integration.webhook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class WebhookVerificationServiceTest {

    private final WebhookVerificationService service = new WebhookVerificationService();

    @Test
    public void verifiesValidSignature() throws Exception {
        String secret = "my-secret-123";
        byte[] body = "{\"event\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] computed = mac.doFinal(body);
        StringBuilder sb = new StringBuilder();
        for (byte b : computed) sb.append(String.format("%02x", b & 0xff));
        String signature = "sha256=" + sb.toString();

        assertTrue(service.verifyHmacSha256(body, secret, signature));
    }

    @Test
    public void rejectsInvalidSignature() {
        String secret = "my-secret-123";
        byte[] body = "{\"event\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        String badSignature = "sha256=deadbeef";
        assertFalse(service.verifyHmacSha256(body, secret, badSignature));
    }
}
