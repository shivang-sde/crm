package com.shivang.crm.modules.integration.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ConnectorWebhookConfig;
import com.shivang.crm.modules.integration.entity.ConnectorWebhookEvent;
import com.shivang.crm.modules.integration.service.ConnectorInstanceService;
import com.shivang.crm.modules.integration.service.ConnectorWebhookConfigService;
import com.shivang.crm.modules.integration.service.ConnectorWebhookService;
import com.shivang.crm.modules.integration.service.WebhookMappingService;
import com.shivang.crm.modules.integration.service.impl.CallWebhookMappingApplier;
import com.shivang.crm.modules.integration.webhook.HeaderSanitizer;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookMapper;
import com.shivang.crm.modules.integration.webhook.WebhookVerificationService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/webhooks/connectors")
@RequiredArgsConstructor
@Slf4j
public class ConnectorWebhookController {

private final ConnectorInstanceService connectorInstanceService;
    private final ConnectorWebhookConfigService webhookConfigService;
    private final ConnectorWebhookService webhookService;
    private final WebhookVerificationService verificationService;
    private final WebhookMappingService webhookMappingService;
    private final HeaderSanitizer headerSanitizer;
    private final NormalizedCallWebhookMapper normalizedCallWebhookMapper;
    private final CallWebhookMappingApplier callWebhookMappingApplier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/{tenantId}/{providerKey}/{triggerKey}")
    public ResponseEntity<?> receiveWebhook(
            @PathVariable UUID tenantId,
            @PathVariable String providerKey,
            @PathVariable String triggerKey,
            @RequestHeader HttpHeaders headers,
            HttpServletRequest request) throws IOException {

        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());

        Optional<ConnectorInstance> instanceOpt = connectorInstanceService.findActiveByTenantAndProvider(tenantId,
                providerKey);
        if (instanceOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "connector_instance_not_found"));
        }
        ConnectorInstance instance = instanceOpt.get();

        Optional<ConnectorWebhookConfig> configOpt = webhookConfigService
                .findByTenantAndConnector(instance.getTenantId(), instance.getId());
        String verificationStatus = "NOT_CONFIGURED";
        boolean verified = false;

        // Production safety: if connector instance is active, require an active webhook
        // config
        if (Boolean.TRUE.equals(instance.getIsActive())) {
            if (configOpt.isEmpty() || !Boolean.TRUE.equals(configOpt.get().getIsActive())) {
                return ResponseEntity.status(403).body(Map.of("error", "webhook_not_configured"));
            }
        }

        if (configOpt.isPresent()) {
            ConnectorWebhookConfig config = configOpt.get();
            // Only require verification when webhook config is active
            if (Boolean.TRUE.equals(config.getIsActive())) {
                String mode = config.getVerificationMode();
                if (mode == null || mode.isBlank()) {
                    log.warn("Webhook config for tenant={} connector={} has no verification mode. Defaulting to NONE.",
                            instance.getTenantId(), instance.getId());
                    mode = "NONE";
                }

                if ("NONE".equalsIgnoreCase(mode)) {
                    verified = true;
                    verificationStatus = "VERIFIED_NONE";
                } else if ("HMAC_SHA256".equalsIgnoreCase(mode)) {
                    String secret = webhookConfigService.getDecryptedSecret(config);
                    String sigHeader = headers.getFirst("X-Signature");
                    if (sigHeader == null)
                        sigHeader = headers.getFirst("X-Hub-Signature-256");
                    if (sigHeader == null)
                        sigHeader = headers.getFirst("X-SellSpark-Signature");
                    if (sigHeader != null && secret != null) {
                        verified = verificationService.verifyHmacSha256(body, secret, sigHeader);
                        verificationStatus = verified ? "VERIFIED" : "INVALID_SIGNATURE";
                    } else {
                        verificationStatus = "NO_SIGNATURE";
                    }
                } else {
                    verificationStatus = "UNSUPPORTED_MODE";
                }
            } else {
                verificationStatus = "NOT_ACTIVE";
            }
        }

        // parse JSON payload where possible
        Map<String, Object> payloadMap = new HashMap<>();
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(body);
            payloadMap = objectMapper.convertValue(node, Map.class);
            log.info(
        "SellSpark raw identifiers trigger={} "
                + "lead_id={} call_uniqueid={} uniqueid={} call_id={}",
        triggerKey,
        payloadMap.get("lead_id"),
        payloadMap.get("call_uniqueid"),
        payloadMap.get("uniqueid"),
        payloadMap.get("call_id")
);
        } catch (Exception e) {
            // keep empty
        }

        Map<String, Object> sanitizedHeaders = new HashMap<>();
        try {
            sanitizedHeaders = headerSanitizer.sanitize(headers.toSingleValueMap().entrySet().stream().collect(
                    java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> java.util.List.of(e.getValue()))));
        } catch (Exception ex) {
            // fallback
        }

        log.info(
                "Webhook received tenant={} connector={} trigger={} verificationStatus={} payloadKeys={} headerKeys={}",
                instance.getTenantId(), instance.getId(), triggerKey, verificationStatus, payloadMap.keySet(),
                sanitizedHeaders.keySet());

        ConnectorWebhookEvent event = ConnectorWebhookEvent.builder()
                .tenantId(instance.getTenantId())
                .connectorInstance(instance)
                .eventType(triggerKey)
                .verificationStatus(verificationStatus)
                .processingStatus("RECEIVED")
                .eventPayload(payloadMap)
                .eventHeaders(sanitizedHeaders)
                .receivedAt(Instant.now())
                .build();

        // Save initial event (without idempotency/external refs)
        ConnectorWebhookEvent saved;
        try {
            saved = webhookService.save(event);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Unique constraint on idempotency_key -> treat as duplicate
            return ResponseEntity.ok(Map.of("status", "duplicate", "reason", "unique_constraint"));
        }

        // only process when verified or not configured
        if (!verified && configOpt.isPresent()
                && "HMAC_SHA256".equalsIgnoreCase(configOpt.get().getVerificationMode())) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_signature"));
        }

        // Mapping and processing (mapping-driven idempotency)
        try {
            var mappings = webhookMappingService.loadActiveMappings(instance.getTenantId(), instance.getId(),
                    triggerKey);
            var normalized = normalizedCallWebhookMapper.map(mappings, payloadMap);
            if (normalized == null) {
                saved.setProcessingStatus("MAPPING_FAILED");
                webhookService.save(saved);
                return ResponseEntity.ok(Map.of("status", "mapping_failed"));
            }

            // build idempotency from normalized fields (mapping-driven)
            String normalizedEventId = normalized.getExternalEventId();
            String normalizedCallId = normalized.getExternalCallId();
            String normalizedTs = normalized.getEventTimestamp() != null ? normalized.getEventTimestamp().toString()
                    : null;

            log.info(
        "Normalized webhook tenant={} trigger={} externalCallId={} " +
        "correlationKey={} agentId={} direction={}",
        instance.getTenantId(),
        triggerKey,
        normalized.getExternalCallId(),
        normalized.getCorrelationKey(),
        normalized.getAgentId(),
        normalized.getDirection()
);

            String idempotencyKey = null;
            if (normalizedEventId != null && !normalizedEventId.isBlank()) {
                idempotencyKey = "event:" + normalizedEventId;
            } else if (normalizedCallId != null && normalizedTs != null) {
                idempotencyKey = "call:" + normalizedCallId + ":trigger:" + triggerKey + ":time:" + normalizedTs;
            } else if (normalizedCallId != null) {
                idempotencyKey = "call:" + normalizedCallId + ":trigger:" + triggerKey;
            }

            // If normalizedCallId is required for this mapping but missing, mapper should
            // have returned null above.

            // check duplicates by idempotency key
            if (idempotencyKey != null && webhookService
                    .findByConnectorInstanceIdAndIdempotencyKey(instance.getId(), idempotencyKey).isPresent()) {
                saved.setProcessingStatus("DUPLICATE_IGNORED");
                webhookService.save(saved);
                return ResponseEntity.ok(Map.of("status", "duplicate"));
            }

            // persist normalized ids + idempotency on saved event
            saved.setExternalReferenceId(normalized.getExternalCallId());
            saved.setExternalEventId(normalized.getExternalEventId());
            saved.setIdempotencyKey(idempotencyKey);
            webhookService.save(saved);

            String result = "";
            if ("call-connect".equals(triggerKey)) {
                result = callWebhookMappingApplier.applyConnect(tenantId, instance.getId(), normalized,
                        instance.getProvider().getProviderKey());
            } else if ("cdr".equals(triggerKey)) {
                result = callWebhookMappingApplier.applyCdr(instance.getTenantId(), normalized,
                        instance.getProvider().getProviderKey());
            }
            saved.setProcessingStatus(result);
            saved.setProcessedAt(Instant.now());
            webhookService.save(saved);
        } catch (Exception e) {
            saved.setProcessingStatus("PROCESSING_ERROR");
            saved.setErrorMessage(e.getMessage());
            webhookService.save(saved);
        }
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
