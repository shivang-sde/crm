package com.shivang.crm.modules.acquisition.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

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
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadIngestionIngressService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final LeadIngestionConfigRepository leadIngestionConfigRepository;
    private final LeadIngestionEventRepository leadIngestionEventRepository;
    private final LeadIngestionProcessingService leadIngestionProcessingService;
    private final LeadIngestionFailureService leadIngestionFailureService;
    private final HeaderSanitizer headerSanitizer;
    private final ObjectMapper objectMapper;

    public LeadIngestionAcceptedResponse receive(String publicKey, String rawBody, HttpHeaders headers) {
        return receiveInternal(publicKey, rawBody, headers, LeadIngestionTransportType.WEBHOOK, "Webhook endpoint key is required", "Invalid webhook endpoint");
    }

    public LeadIngestionAcceptedResponse receiveDirect(String publicKey, String rawBody, HttpHeaders headers) {
        return receiveInternal(publicKey, rawBody, headers, LeadIngestionTransportType.API, "Direct API key is required", "Invalid direct API endpoint");
    }

    private LeadIngestionAcceptedResponse receiveInternal(String publicKey, String rawBody, HttpHeaders headers, LeadIngestionTransportType expectedTransport, String missingKeyMessage, String notFoundMessage) {
        if (publicKey == null || publicKey.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", missingKeyMessage);
        }

        LeadIngestionConfig config = leadIngestionConfigRepository
            .findByPublicKeyAndDeletedFalse(publicKey)
            .orElseThrow(() -> new NotFoundException(notFoundMessage));

        if (expectedTransport == LeadIngestionTransportType.WEBHOOK) {
            validateWebhookEligibility(config);
        } else if (expectedTransport == LeadIngestionTransportType.API) {
            validateDirectApiEligibility(config);
        } else {
            validateWebhookEligibility(config);
        }

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

        LeadIngestionEvent savedEvent;
        try {
            savedEvent = leadIngestionEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent duplicate inserted between our check and save — DB unique index enforces idempotency.
            // Resolve to the existing event and return it (idempotent semantics).
            log.warn("Idempotency race detected for tenant={} configId={} idempotencyKey={}: {}",
                config.getTenantId(), config.getId(), idempotencyKey, ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
            Optional<LeadIngestionEvent> existingAfterRace = findDuplicateEvent(config, externalEventId, idempotencyKey);
            if (existingAfterRace.isPresent()) {
                LeadIngestionEvent existing = existingAfterRace.get();
                log.info("Returning existing event after idempotency race for tenant={} configId={} eventId={}",
                    config.getTenantId(), config.getId(), existing.getId());
                return LeadIngestionAcceptedResponse.builder()
                    .eventId(existing.getId())
                    .status(existing.getStatus())
                    .receivedAt(existing.getReceivedAt())
                    .build();
            }
            throw ex;
        }
        log.info("Webhook ingestion event captured for tenant={} configId={} eventId={}",
            config.getTenantId(),
            config.getId(),
            savedEvent.getId());

        LeadIngestionEvent processedEvent;
        try {
            processedEvent = leadIngestionProcessingService.processEvent(config.getTenantId(), config.getId(), savedEvent.getId());
        } catch (BusinessException ex) {
            if ("DUPLICATE".equals(ex.getErrorCode())) {
                // Fallback: if duplicate escaped inner handling (e.g., after transaction boundary),
                // ensure it is recorded as DUPLICATE not FAILED.
                LeadIngestionEvent dup = leadIngestionFailureService.markDuplicate(
                    config.getTenantId(), savedEvent.getId(), ex.getMessage());
                if (dup != null) {
                    processedEvent = dup;
                } else {
                    throw ex;
                }
            } else {
                log.error("Processing failed for ingestion event {} tenant={} configId={}",
                    savedEvent.getId(), config.getTenantId(), config.getId(), ex);
                LeadIngestionEvent failedEvent = leadIngestionFailureService.markFailed(
                    config.getTenantId(), savedEvent.getId(), "PROCESSING_ERROR", ex.getMessage());
                if (failedEvent == null) throw ex;
                processedEvent = failedEvent;
            }
        } catch (DataIntegrityViolationException ex) {
            // Concurrent lead duplicate hit DB constraint outside inner transaction (flush at commit).
            String cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
            boolean isLeadDuplicate = cause != null && (cause.toLowerCase().contains("uq_lead") || cause.toLowerCase().contains("duplicate"));
            if (isLeadDuplicate) {
                LeadIngestionEvent dup = leadIngestionFailureService.markDuplicate(
                    config.getTenantId(), savedEvent.getId(), "A lead with this email or phone already exists");
                if (dup != null) {
                    processedEvent = dup;
                } else {
                    throw ex;
                }
            } else {
                log.error("Processing failed for ingestion event {} tenant={} configId={}",
                    savedEvent.getId(), config.getTenantId(), config.getId(), ex);
                LeadIngestionEvent failedEvent = leadIngestionFailureService.markFailed(
                    config.getTenantId(), savedEvent.getId(), "PROCESSING_ERROR", cause);
                if (failedEvent == null) throw ex;
                processedEvent = failedEvent;
            }
        } catch (Exception ex) {
            log.error("Processing failed for ingestion event {} tenant={} configId={}",
                savedEvent.getId(), config.getTenantId(), config.getId(), ex);
            LeadIngestionEvent failedEvent = leadIngestionFailureService.markFailed(
                config.getTenantId(),
                savedEvent.getId(),
                "PROCESSING_ERROR",
                ex.getMessage()
            );
            if (failedEvent == null) {
                throw ex;
            }
            processedEvent = failedEvent;
        }

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

    private void validateDirectApiEligibility(LeadIngestionConfig config) {
        if (config.getTransportType() != LeadIngestionTransportType.API) {
            throw new BusinessException("INGESTION_ENDPOINT_NOT_ACCEPTING", "Direct API endpoint is not available");
        }

        if (!Boolean.TRUE.equals(config.getActive())) {
            throw new BusinessException("INGESTION_ENDPOINT_NOT_ACCEPTING", "Direct API endpoint is not available");
        }
    }

    private Map<String, Object> parsePayload(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Webhook payload is required as a JSON object");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JacksonException ex) {
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
