package com.shivang.crm.modules.records.controller;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.records.entity.RecordWebhook;
import com.shivang.crm.modules.records.entity.WebhookAuthMode;
import com.shivang.crm.modules.records.repository.RecordWebhookRepository;
import com.shivang.crm.modules.records.service.RecordWebhookIdempotencyService;
import com.shivang.crm.modules.records.service.RecordWebhookIngestionService;
import com.shivang.crm.modules.records.service.RecordWebhookService;
import com.shivang.crm.shared.dto.ApiResponse;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/records/webhooks")
@RequiredArgsConstructor
@Slf4j
public class RecordWebhookIngressController {

    private final RecordWebhookRepository webhookRepo;
    private final RecordWebhookService webhookService;
    private final RecordWebhookIdempotencyService idempotencyService;
    private final RecordWebhookIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;

    @PostMapping("/{webhookKey}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ingress(
            @PathVariable String webhookKey,
            @RequestHeader(value = "X-Webhook-API-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKeyHeader,
            @RequestBody(required = false) byte[] rawBody) {

        String bodyStr = rawBody != null ? new String(rawBody, StandardCharsets.UTF_8) : "";

        var webhooks = webhookRepo.findByWebhookKeyAndDeletedFalse(webhookKey);
        RecordWebhook webhook = webhooks.stream()
                .filter(w -> Boolean.TRUE.equals(w.getIsActive()))
                .findFirst()
                .orElse(null);

        if (webhook == null) {
            log.debug("Webhook ingress rejected: unknown or inactive key={}", maskKey(webhookKey));
            return unauthorized();
        }

        WebhookAuthMode mode = webhook.getAuthMode() != null ? webhook.getAuthMode() : WebhookAuthMode.NONE;

        boolean authenticated = false;
        try {
            switch (mode) {
                case NONE:
                    authenticated = true;
                    break;
                case API_KEY:
                    if (apiKey != null && !apiKey.isBlank()) authenticated = webhookService.verifyApiKey(webhook, apiKey.trim());
                    break;
                case HMAC_SHA256:
                    if (signature != null && !signature.isBlank()) authenticated = webhookService.verifyHmac(webhook, bodyStr, signature.trim());
                    break;
                default:
                    authenticated = false;
            }
        } catch (Exception e) {
            log.warn("Webhook ingress auth error for key={} mode={}: {}", maskKey(webhookKey), mode, e.getMessage());
            authenticated = false;
        }

        String payloadHashForFailure = RecordWebhookIdempotencyService.hashPayload(rawBody);
        // Derive idempotencyKey early for failure observability (headers + payload idempotencyKey if JSON parsable)
        String earlyIdempotencyKey = null;
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) earlyIdempotencyKey = idempotencyKeyHeader.trim();
        else if (xIdempotencyKeyHeader != null && !xIdempotencyKeyHeader.isBlank()) earlyIdempotencyKey = xIdempotencyKeyHeader.trim();

        if (!authenticated) {
            log.info("Webhook ingress unauthorized for key={} mode={}", maskKey(webhookKey), mode);
            persistFailure(webhook, payloadHashForFailure, earlyIdempotencyKey, "AUTHENTICATION", "UNAUTHORIZED", "Invalid webhook key or credentials", webhook.getRecordTypeId(), webhook.getMappingProfileId());
            return unauthorized();
        }

        if (rawBody != null && rawBody.length > MAX_PAYLOAD_BYTES) {
            log.warn("Webhook payload too large for key={} size={}", maskKey(webhookKey), rawBody.length);
            persistFailure(webhook, payloadHashForFailure, earlyIdempotencyKey, "VALIDATION", "PAYLOAD_TOO_LARGE", "Payload exceeds 1MB limit", webhook.getRecordTypeId(), webhook.getMappingProfileId());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.<Map<String, Object>>error("PAYLOAD_TOO_LARGE", "Payload exceeds 1MB limit"));
        }

        Map<String, Object> payloadMap;
        try {
            if (rawBody == null || rawBody.length == 0) {
                payloadMap = new HashMap<>();
            } else {
                JsonNode root = objectMapper.readTree(rawBody);
                if (root.isNull() || !root.isObject()) {
                    persistFailure(webhook, payloadHashForFailure, earlyIdempotencyKey, "VALIDATION", "INVALID_PAYLOAD", "Payload must be a JSON object", webhook.getRecordTypeId(), webhook.getMappingProfileId());
                    return badRequest("INVALID_PAYLOAD", "Payload must be a JSON object");
                }
                payloadMap = objectMapper.convertValue(root, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.debug("Invalid JSON payload for webhook key={}: {}", maskKey(webhookKey), e.getMessage());
            persistFailure(webhook, payloadHashForFailure, earlyIdempotencyKey, "VALIDATION", "INVALID_JSON", "Invalid JSON payload", webhook.getRecordTypeId(), webhook.getMappingProfileId());
            return badRequest("INVALID_JSON", "Invalid JSON payload");
        }

        String payloadHash = RecordWebhookIdempotencyService.hashPayload(rawBody);
        String idempotencyKey = null;
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) idempotencyKey = idempotencyKeyHeader.trim();
        else if (xIdempotencyKeyHeader != null && !xIdempotencyKeyHeader.isBlank()) idempotencyKey = xIdempotencyKeyHeader.trim();
        else {
            Object v = payloadMap.get("idempotencyKey");
            if (v == null) v = payloadMap.get("idempotency_key");
            if (v instanceof String s && !s.isBlank()) idempotencyKey = s.trim();
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existingOpt = idempotencyService.findExisting(webhook.getTenantId(), webhook.getId(), idempotencyKey);
            if (existingOpt.isPresent()) {
                var existing = existingOpt.get();
                if (!payloadHash.equals(existing.getPayloadHash())) {
                    log.info("Idempotency key payload mismatch for webhook {} key={}", webhook.getId(), idempotencyKey);
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(ApiResponse.<Map<String, Object>>error("IDEMPOTENCY_PAYLOAD_MISMATCH", "Idempotency key already used with different payload"));
                }
                log.info("Idempotency hit for webhook {} key={} status={}", webhook.getId(), idempotencyKey, existing.getStatus());
                Integer status = existing.getResponseStatus() != null ? existing.getResponseStatus() : 200;
                Map<String, Object> body = existing.getResponseBody() != null ? existing.getResponseBody() : Map.of();
                if (status == 201 || status == 200) {
                    return ResponseEntity.status(HttpStatus.valueOf(status)).body(ApiResponse.success(body));
                } else {
                    String code = existing.getErrorCode() != null ? existing.getErrorCode() : "ERROR";
                    String msg = existing.getErrorMessage() != null ? existing.getErrorMessage() : "Duplicate request";
                    return ResponseEntity.status(HttpStatus.valueOf(status)).body(ApiResponse.<Map<String, Object>>error(code, msg));
                }
            }
        }

        try {
            var result = ingestionService.ingestNew(webhook, payloadMap, rawBody, idempotencyKey, payloadHash);
            Map<String, Object> data = Map.of(
                    "recordId", result.record.getId().toString(),
                    "recordTypeId", result.record.getRecordTypeId().toString(),
                    "status", "CREATED"
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
        } catch (BusinessException e) {
            log.debug("Webhook ingestion failed for {}: {} - {}", webhook.getId(), e.getErrorCode(), e.getMessage());
            String stage = mapFailureStage(e.getErrorCode());
            // Always persist for observability, even without idempotencyKey
            try {
                idempotencyService.createDelivery(webhook.getTenantId(), webhook.getId(), webhook.getWebhookKey(), idempotencyKey, payloadHash, "FAILED", null, 400, null, e.getErrorCode(), e.getMessage(), webhook.getRecordTypeId(), webhook.getMappingProfileId(), null, stage);
            } catch (Exception ex) {
                log.warn("Failed to store failed delivery: {}", ex.getMessage());
            }
            return badRequest(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Webhook ingestion failed for {}", webhook.getId(), e);
            try {
                idempotencyService.createDelivery(webhook.getTenantId(), webhook.getId(), webhook.getWebhookKey(), idempotencyKey, payloadHash, "FAILED", null, 500, null, "INTERNAL_ERROR", "Failed to create record", webhook.getRecordTypeId(), webhook.getMappingProfileId(), null, "EVENT_PUBLICATION");
            } catch (Exception ex) {
                log.warn("Failed to store failed delivery: {}", ex.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Map<String, Object>>error("INTERNAL_ERROR", "Failed to create record"));
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<Map<String, Object>>error("UNAUTHORIZED", "Invalid webhook key or credentials"));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> badRequest(String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, Object>>error(code, message));
    }

    private String mapFailureStage(String errorCode) {
        if (errorCode == null) return "VALIDATION";
        String c = errorCode.toUpperCase();
        if (c.contains("MAPPING") || c.contains("TRANSFORM")) return "MAPPING";
        if (c.contains("RECORD") || c.contains("REQUIRED") || c.contains("UNKNOWN_FIELD")) return "RECORD_CREATION";
        if (c.contains("INVALID_CONFIGURATION") || c.contains("REFERENCE") || c.contains("DUPLICATE")) return "VALIDATION";
        if (c.contains("AUTH") || c.contains("UNAUTHORIZED")) return "AUTHENTICATION";
        if (c.contains("PAYLOAD") || c.contains("JSON")) return "VALIDATION";
        return "VALIDATION";
    }

    private void persistFailure(RecordWebhook webhook, String payloadHash, String idempotencyKey, String stage, String code, String message, java.util.UUID recordTypeId, java.util.UUID mappingProfileId) {
        try {
            idempotencyService.createDelivery(webhook.getTenantId(), webhook.getId(), webhook.getWebhookKey(), idempotencyKey, payloadHash, "FAILED", null, stage.equals("AUTHENTICATION") ? 401 : 400, null, code, message, recordTypeId, mappingProfileId, null, stage);
        } catch (Exception ex) {
            log.warn("Failed to persist failure delivery for webhook {}: {}", webhook.getId(), ex.getMessage());
        }
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 4) return "***";
        return key.substring(0, 2) + "***" + key.substring(key.length() - 2);
    }
}
