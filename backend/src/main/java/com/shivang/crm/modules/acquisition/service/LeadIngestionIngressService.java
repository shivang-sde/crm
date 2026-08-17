package com.shivang.crm.modules.acquisition.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.acquisition.config.LeadIngestionConfig;
import com.shivang.crm.modules.acquisition.config.LeadIngestionTransportType;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionAcceptedResponse;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionEventRepository;
import com.shivang.crm.modules.integration.webhook.HeaderSanitizer;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LeadIngestionIngressService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final LeadIngestionConfigRepository leadIngestionConfigRepository;
    private final LeadIngestionEventRepository leadIngestionEventRepository;
    private final LeadIngestionProcessingService leadIngestionProcessingService;
    private final HeaderSanitizer headerSanitizer;
    private final ObjectMapper objectMapper;

    public LeadIngestionAcceptedResponse receive(String publicKey, String rawBody, HttpHeaders headers) {
        if (publicKey == null || publicKey.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Webhook endpoint key is required");
        }

        LeadIngestionConfig config = leadIngestionConfigRepository
            .findByPublicKeyAndDeletedFalse(publicKey)
            .orElseThrow(() -> new NotFoundException("Invalid webhook endpoint"));

        validateWebhookEligibility(config);

        Map<String, Object> payload = parsePayload(rawBody);
        Map<String, Object> sanitizedHeaders = sanitizeHeaders(headers);

        String externalEventId = resolveExternalEventId(payload, sanitizedHeaders);
        String idempotencyKey = resolveIdempotencyKey(payload, sanitizedHeaders, externalEventId);

        Optional<LeadIngestionEvent> duplicate = findDuplicateEvent(config, externalEventId, idempotencyKey);
        if (duplicate.isPresent()) {
            LeadIngestionEvent existing = duplicate.get();
            log.info("Duplicate ingestion event ignored for tenant={} configId={} eventId={} existingStatus={}",
                config.getTenantId(),
                config.getId(),
                existing.getId(),
                existing.getStatus());
            return LeadIngestionAcceptedResponse.builder()
                .eventId(existing.getId())
                .status(existing.getStatus())
                .receivedAt(existing.getReceivedAt())
                .build();
        }

        LeadIngestionEvent event = LeadIngestionEvent.builder()
            .tenantId(config.getTenantId())
            .ingestionConfigId(config.getId())
            .externalEventId(externalEventId)
            .idempotencyKey(idempotencyKey)
            .rawPayload(payload)
            .headers(sanitizedHeaders)
            .status(LeadIngestionEventStatus.RECEIVED)
            .receivedAt(Instant.now())
            .build();

        LeadIngestionEvent savedEvent = leadIngestionEventRepository.save(event);
        log.info("Webhook ingestion event captured for tenant={} configId={} eventId={}",
            config.getTenantId(),
            config.getId(),
            savedEvent.getId());

        LeadIngestionEvent processedEvent = leadIngestionProcessingService.processEvent(config.getTenantId(), config.getId(), savedEvent.getId());

        return LeadIngestionAcceptedResponse.builder()
            .eventId(processedEvent.getId())
            .status(processedEvent.getStatus())
            .receivedAt(processedEvent.getReceivedAt())
            .build();
    }

    private Optional<LeadIngestionEvent> findDuplicateEvent(LeadIngestionConfig config, String externalEventId, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<LeadIngestionEvent> byIdempotency = leadIngestionEventRepository
                .findByTenantIdAndIngestionConfigIdAndIdempotencyKeyAndDeletedFalse(config.getTenantId(), config.getId(), idempotencyKey);
            if (byIdempotency.isPresent()) {
                return byIdempotency;
            }
        }

        if (externalEventId != null && !externalEventId.isBlank()) {
            return leadIngestionEventRepository
                .findByTenantIdAndIngestionConfigIdAndExternalEventIdAndDeletedFalse(config.getTenantId(), config.getId(), externalEventId);
        }

        return Optional.empty();
    }

    private void validateWebhookEligibility(LeadIngestionConfig config) {
        if (config.getTransportType() != LeadIngestionTransportType.WEBHOOK) {
            throw new BusinessException("INGESTION_ENDPOINT_NOT_ACCEPTING", "Webhook endpoint is not available");
        }

        if (!Boolean.TRUE.equals(config.getActive())) {
            throw new BusinessException("INGESTION_ENDPOINT_NOT_ACCEPTING", "Webhook endpoint is not available");
        }
    }

    private Map<String, Object> parsePayload(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Webhook payload is required as a JSON object");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("VALIDATION_ERROR", "Invalid JSON payload. Expected a JSON object");
        }

        if (root == null || !root.isObject()) {
            throw new BusinessException("VALIDATION_ERROR", "Webhook payload must be a JSON object");
        }

        return objectMapper.convertValue(root, MAP_TYPE);
    }

    private Map<String, Object> sanitizeHeaders(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> rawHeaders = new HashMap<>();
        headers.forEach(rawHeaders::put);
        return headerSanitizer.sanitize(rawHeaders);
    }

    private String resolveExternalEventId(Map<String, Object> payload, Map<String, Object> sanitizedHeaders) {
        String directHeader = readStringHeader(sanitizedHeaders, "x-external-event-id", "x-event-id", "x-request-id", "x-correlation-id");
        if (directHeader != null && !directHeader.isBlank()) {
            return directHeader;
        }

        if (payload != null) {
            String fromPayload = readStringFromMap(payload, "externalEventId", "eventId", "id", "messageId");
            if (fromPayload != null && !fromPayload.isBlank()) {
                return fromPayload;
            }
        }

        return null;
    }

    private String resolveIdempotencyKey(Map<String, Object> payload, Map<String, Object> sanitizedHeaders, String externalEventId) {
        String directHeader = readStringHeader(sanitizedHeaders, "idempotency-key", "x-idempotency-key", "x-request-id", "x-correlation-id");
        if (directHeader != null && !directHeader.isBlank()) {
            return directHeader;
        }

        if (externalEventId != null && !externalEventId.isBlank()) {
            return "event:" + externalEventId;
        }

        if (payload != null) {
            String fromPayload = readStringFromMap(payload, "idempotencyKey", "idempotency_key", "externalEventId", "eventId");
            if (fromPayload != null && !fromPayload.isBlank()) {
                return fromPayload;
            }
        }

        return null;
    }

    private String readStringHeader(Map<String, Object> headers, String... keys) {
        if (headers == null) {
            return null;
        }
        for (String key : keys) {
            Object value = headers.get(key);
            if (value instanceof String str && !str.isBlank()) {
                return str.trim();
            }
            if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String str) {
                return str.trim();
            }
        }
        return null;
    }

    private String readStringFromMap(Map<String, Object> payload, String... keys) {
        if (payload == null) {
            return null;
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof String str && !str.isBlank()) {
                return str.trim();
            }
        }
        return null;
    }
}
