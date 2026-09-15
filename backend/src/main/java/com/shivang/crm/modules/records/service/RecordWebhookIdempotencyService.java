package com.shivang.crm.modules.records.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.records.entity.RecordWebhookDelivery;
import com.shivang.crm.modules.records.repository.RecordWebhookDeliveryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordWebhookIdempotencyService {

    private final RecordWebhookDeliveryRepository deliveryRepo;

    public Optional<RecordWebhookDelivery> findExisting(UUID tenantId, UUID webhookId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
        return deliveryRepo.findByTenantIdAndWebhookIdAndIdempotencyKeyAndDeletedFalse(tenantId, webhookId, idempotencyKey.trim());
    }

    @Transactional
    public RecordWebhookDelivery createDelivery(UUID tenantId, UUID webhookId, String webhookKey,
                                                String idempotencyKey, String payloadHash,
                                                String status, UUID recordId,
                                                Integer responseStatus, Map<String, Object> responseBody,
                                                String errorCode, String errorMessage) {
        return createDelivery(tenantId, webhookId, webhookKey, idempotencyKey, payloadHash, status, recordId, responseStatus, responseBody, errorCode, errorMessage, null, null, null, null);
    }

    @Transactional
    public RecordWebhookDelivery createDelivery(UUID tenantId, UUID webhookId, String webhookKey,
                                                String idempotencyKey, String payloadHash,
                                                String status, UUID recordId,
                                                Integer responseStatus, Map<String, Object> responseBody,
                                                String errorCode, String errorMessage,
                                                UUID recordTypeId, UUID mappingProfileId, UUID eventId, String failureStage) {
        String safeKey = idempotencyKey != null ? idempotencyKey.trim() : "";
        if (safeKey.isEmpty()) safeKey = "no-key-" + UUID.randomUUID();
        RecordWebhookDelivery delivery = RecordWebhookDelivery.builder()
                .tenantId(tenantId)
                .webhookId(webhookId)
                .webhookKey(webhookKey)
                .idempotencyKey(safeKey)
                .payloadHash(payloadHash)
                .status(status)
                .recordId(recordId)
                .responseStatus(responseStatus)
                .responseBody(responseBody)
                .errorCode(errorCode)
                .errorMessage(sanitizeErrorMessage(errorMessage))
                .recordTypeId(recordTypeId)
                .mappingProfileId(mappingProfileId)
                .eventId(eventId)
                .failureStage(failureStage)
                .receivedAt(Instant.now())
                .build();
        try {
            return deliveryRepo.saveAndFlush(delivery);
        } catch (DataIntegrityViolationException ex) {
            // Race: another thread inserted same key concurrently
            log.warn("Idempotency race for tenant={} webhook={} key={}: {}", tenantId, webhookId, idempotencyKey, ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
            return findExisting(tenantId, webhookId, idempotencyKey).orElseThrow(() -> ex);
        }
    }

    public static String hashPayload(byte[] rawBody) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawBody != null ? rawBody : new byte[0]);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash payload", e);
        }
    }

    public static String resolveIdempotencyKey(Map<String, String> headers, Map<String, Object> payload) {
        // Check headers first (case-insensitive)
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                String k = e.getKey();
                if (k == null) continue;
                String lower = k.toLowerCase();
                if (lower.equals("idempotency-key") || lower.equals("x-idempotency-key")) {
                    String v = e.getValue();
                    if (v != null && !v.isBlank()) return v.trim();
                }
            }
        }
        // Fallback to payload field
        if (payload != null) {
            Object v = payload.get("idempotencyKey");
            if (v == null) v = payload.get("idempotency_key");
            if (v instanceof String s && !s.isBlank()) return s.trim();
        }
        return null;
    }

    public static String hashPayload(String rawBody) {
        return hashPayload(rawBody != null ? rawBody.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    }

    private String sanitizeErrorMessage(String msg) {
        if (msg == null) return null;
        String lower = msg.toLowerCase();
        // Redact secrets
        if (lower.contains("api-key") || lower.contains("apikey") || lower.contains("hmac") || lower.contains("secret") || lower.contains("authorization") || lower.contains("signature") || lower.contains("credential")) {
            return "Authentication failed";
        }
        // Truncate
        if (msg.length() > 500) return msg.substring(0, 500);
        return msg;
    }
}
