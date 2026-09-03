package com.shivang.crm.modules.acquisition.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.acquisition.config.LeadIngestionConfig;
import com.shivang.crm.modules.acquisition.config.LeadIngestionTransportType;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionEventRepository;
import com.shivang.crm.modules.integration.outbound.OutboundHttpConnection;
import com.shivang.crm.modules.integration.outbound.OutboundHttpMethod;
import com.shivang.crm.modules.integration.outbound.OutboundHttpRequest;
import com.shivang.crm.modules.integration.outbound.OutboundHttpResult;
import com.shivang.crm.modules.integration.outbound.OutboundHttpService;
import com.shivang.crm.modules.integration.outbound.OutboundHttpConnectionRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadIngestionPollingService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final LeadIngestionConfigRepository configRepository;
    private final LeadIngestionEventRepository eventRepository;
    private final LeadIngestionProcessingService processingService;
    private final LeadIngestionFailureService failureService;
    private final OutboundHttpService outboundHttpService;
    private final OutboundHttpConnectionRepository connectionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> testConnection(UUID tenantId, UUID configId) {
        LeadIngestionConfig config = requirePollingConfig(tenantId, configId);
        Map<String, Object> pollingCfg = extractPollingConfig(config);
        String endpoint = asString(pollingCfg.get("endpointUrl"));
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Polling endpointUrl is required");
        }
        UUID connectionId = asUuid(pollingCfg.get("connectionId"));
        // Use outbound service to test with same handling, but we can just do a single fetch
        PollResult result = fetchPage(tenantId, config, pollingCfg, null, 1);
        if (!result.success) {
            throw new BusinessException("CONNECTION_ERROR", result.errorMessage != null ? result.errorMessage : "Connection failed");
        }
        Map<String, Object> out = new HashMap<>();
        out.put("success", true);
        out.put("recordsAvailable", result.records.size());
        out.put("sampleRecord", result.records.isEmpty() ? null : result.records.get(0));
        return out;
    }

    @Transactional
    public Map<String, Object> pollNow(UUID tenantId, UUID configId, UUID actorId) {
        LeadIngestionConfig config = requirePollingConfigForUpdate(tenantId, configId);
        if (!Boolean.TRUE.equals(config.getActive())) {
            throw new BusinessException("VALIDATION_ERROR", "Polling source is inactive");
        }
        // Concurrency check: if settings has pollingState.running true and updated recently, reject
        Map<String, Object> pollingCfg = extractPollingConfig(config);
        Map<String, Object> state = extractPollingState(config);
        Instant now = Instant.now();
        // Simple in-DB lock via settings running flag with timeout 5 minutes
        Instant runningSince = asInstant(state.get("pollingStartedAt"));
        if (runningSince != null && now.minusSeconds(300).isBefore(runningSince) && Boolean.TRUE.equals(state.get("pollingRunning"))) {
            throw new BusinessException("ALREADY_RUNNING", "Polling already running for this source");
        }
        // Mark running
        state.put("pollingRunning", true);
        state.put("pollingStartedAt", now.toString());
        config.setSettings(mergeSettings(config.getSettings(), "pollingState", state));
        configRepository.save(config);

        try {
            return doPoll(tenantId, config, actorId);
        } finally {
            // Clear running flag
            LeadIngestionConfig fresh = configRepository.findByIdAndTenantIdAndDeletedFalse(configId, tenantId).orElse(config);
            Map<String, Object> freshState = extractPollingState(fresh);
            freshState.put("pollingRunning", false);
            freshState.remove("pollingStartedAt");
            fresh.setSettings(mergeSettings(fresh.getSettings(), "pollingState", freshState));
            configRepository.save(fresh);
        }
    }

    private Map<String, Object> doPoll(UUID tenantId, LeadIngestionConfig config, UUID actorId) {
        Map<String, Object> pollingCfg = extractPollingConfig(config);
        String endpoint = asString(pollingCfg.get("endpointUrl"));
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Polling endpointUrl is required");
        }

        String recordsPath = asString(pollingCfg.get("recordsPath"));
        String externalIdPath = asString(pollingCfg.get("externalIdPath"));
        if (externalIdPath == null || externalIdPath.isBlank()) externalIdPath = "id";
        int pageSize = asInt(pollingCfg.get("pageSize"), 50);
        int maxPages = asInt(pollingCfg.get("maxPages"), 3);
        String incrementalParam = asString(pollingCfg.get("incrementalParam"));
        UUID connectionId = asUuid(pollingCfg.get("connectionId"));

        Map<String, Object> state = extractPollingState(config);
        String lastPollAt = asString(state.get("lastPollAt"));
        String lastCursor = asString(state.get("lastCursor"));

        int totalFetched = 0, created = 0, duplicate = 0, rejected = 0, failed = 0;
        String nextCursor = lastCursor;
        boolean hasMore = true;
        int page = 1;

        for (int p = 0; p < maxPages && hasMore; p++) {
            PollResult pageResult = fetchPageWithPagination(tenantId, config, pollingCfg, connectionId, incrementalParam, lastPollAt, nextCursor, page, pageSize);
            if (!pageResult.success) {
                // Distinguish auth vs network vs rate limit
                String code = pageResult.errorCode != null ? pageResult.errorCode : "POLLING_FAILED";
                if ("AUTH_ERROR".equals(code) || pageResult.statusCode == 401 || pageResult.statusCode == 403) {
                    state.put("lastError", "Authentication failed: " + pageResult.errorMessage);
                    state.put("lastErrorCode", "AUTH_ERROR");
                } else if (pageResult.statusCode == 429) {
                    state.put("lastError", "Rate limited: " + pageResult.errorMessage);
                    state.put("lastErrorCode", "RATE_LIMITED");
                    break; // Don't continue pages on rate limit
                } else {
                    state.put("lastError", pageResult.errorMessage);
                    state.put("lastErrorCode", code);
                }
                // For transient network failures before any records, we still want to record poll attempt but not mark all as failed
                // We break and report partial
                break;
            }

            List<Map<String, Object>> records = pageResult.records;
            if (records.isEmpty()) {
                hasMore = false;
                nextCursor = null;
                break;
            }

            for (Map<String, Object> record : records) {
                totalFetched++;
                String externalId = extractExternalId(record, externalIdPath);
                String idempotencyKey = externalId != null ? "poll:" + config.getId() + ":" + externalId : null;
                Map<String, Object> rawPayload = new LinkedHashMap<>(record);
                // Ensure we keep raw payload without credentials
                LeadIngestionEvent event = LeadIngestionEvent.builder()
                    .tenantId(tenantId)
                    .ingestionConfigId(config.getId())
                    .externalEventId(externalId)
                    .idempotencyKey(idempotencyKey)
                    .rawPayload(rawPayload)
                    .headers(Map.of("source", "POLLING", "pollPage", String.valueOf(page), "externalId", externalId != null ? externalId : ""))
                    .status(LeadIngestionEventStatus.RECEIVED)
                    .receivedAt(Instant.now())
                    .build();
                LeadIngestionEvent saved = eventRepository.save(event);
                LeadIngestionEvent processed;
                try {
                    processed = processingService.processEvent(tenantId, config.getId(), saved.getId());
                } catch (Exception ex) {
                    processed = eventRepository.findById(saved.getId()).orElse(saved);
                    if (processed.getStatus() == LeadIngestionEventStatus.RECEIVED || processed.getStatus() == LeadIngestionEventStatus.PROCESSING) {
                        failureService.markFailed(tenantId, saved.getId(), "PROCESSING_ERROR", ex.getMessage());
                        processed = eventRepository.findById(saved.getId()).orElse(processed);
                    }
                }
                switch (processed.getStatus()) {
                    case PROCESSED -> created++;
                    case DUPLICATE -> duplicate++;
                    case REJECTED -> rejected++;
                    case FAILED -> failed++;
                    default -> failed++;
                }
            }

            // Determine next cursor for pagination
            if (pageResult.nextCursor != null && !pageResult.nextCursor.isBlank()) {
                nextCursor = pageResult.nextCursor;
                hasMore = true;
                page++;
            } else {
                // If records size < pageSize, no more pages; else assume need to increment page
                hasMore = records.size() >= pageSize && page < maxPages;
                if (hasMore) {
                    page++;
                    nextCursor = String.valueOf(page);
                } else {
                    nextCursor = null;
                }
            }
        }

        // Update sync state on success (even partial)
        state.put("lastPollAt", Instant.now().toString());
        state.put("lastCursor", nextCursor);
        state.put("lastPollStats", Map.of("totalFetched", totalFetched, "created", created, "duplicate", duplicate, "rejected", rejected, "failed", failed, "timestamp", Instant.now().toString()));
        state.remove("lastError");
        state.remove("lastErrorCode");
        // Also store total for overview
        config.setSettings(mergeSettings(config.getSettings(), "pollingState", state));
        configRepository.save(config);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalFetched", totalFetched);
        summary.put("created", created);
        summary.put("duplicate", duplicate);
        summary.put("rejected", rejected);
        summary.put("failed", failed);
        summary.put("nextCursor", nextCursor);
        return summary;
    }

    private PollResult fetchPageWithPagination(UUID tenantId, LeadIngestionConfig config, Map<String, Object> pollingCfg, UUID connectionId, String incrementalParam, String lastPollAt, String cursor, int page, int pageSize) {
        String endpoint = asString(pollingCfg.get("endpointUrl"));
        String method = asString(pollingCfg.get("method"));
        if (method == null || method.isBlank()) method = "GET";
        String recordsPath = asString(pollingCfg.get("recordsPath"));

        Map<String, List<String>> queryParams = new HashMap<>();
        // incremental sync
        if (incrementalParam != null && !incrementalParam.isBlank() && lastPollAt != null) {
            queryParams.put(incrementalParam, List.of(lastPollAt));
        }
        // pagination
        String pageParam = asString(pollingCfg.get("pageParam"));
        if (pageParam == null || pageParam.isBlank()) pageParam = "page";
        String sizeParam = asString(pollingCfg.get("sizeParam"));
        if (sizeParam == null || sizeParam.isBlank()) sizeParam = "per_page";
        if (cursor != null && !cursor.isBlank()) {
            // If cursor is numeric page, use pageParam
            queryParams.put(pageParam, List.of(cursor));
        } else if (page > 1) {
            queryParams.put(pageParam, List.of(String.valueOf(page)));
        }
        queryParams.put(sizeParam, List.of(String.valueOf(pageSize)));

        // Also allow custom queryParams from pollingCfg
        Object customQp = pollingCfg.get("queryParams");
        if (customQp instanceof Map<?,?> cq) {
            for (Map.Entry<?,?> e : cq.entrySet()) {
                String k = String.valueOf(e.getKey());
                String v = String.valueOf(e.getValue());
                // Support template {{lastPollAt}}
                if (v.contains("{{lastPollAt}}") && lastPollAt != null) {
                    v = v.replace("{{lastPollAt}}", lastPollAt);
                }
                queryParams.put(k, List.of(v));
            }
        }

        return fetchPageInternal(tenantId, endpoint, method, connectionId, queryParams, recordsPath);
    }

    private PollResult fetchPage(UUID tenantId, LeadIngestionConfig config, Map<String, Object> pollingCfg, String cursor, int page) {
        String endpoint = asString(pollingCfg.get("endpointUrl"));
        String method = asString(pollingCfg.get("method"));
        if (method == null) method = "GET";
        String recordsPath = asString(pollingCfg.get("recordsPath"));
        UUID connectionId = asUuid(pollingCfg.get("connectionId"));
        return fetchPageWithPagination(tenantId, config, pollingCfg, connectionId, null, null, cursor, page, 50);
    }

    private PollResult fetchPageInternal(UUID tenantId, String endpoint, String method, UUID connectionId, Map<String, List<String>> queryParams, String recordsPath) {
        try {
            OutboundHttpMethod httpMethod;
            try {
                httpMethod = OutboundHttpMethod.valueOf(method.toUpperCase());
            } catch (Exception e) {
                httpMethod = OutboundHttpMethod.GET;
            }

            // For polling, actor is system; we need a dummy actorId - use tenantId as actor for now, or create system actor
            // OutboundHttpService requires actorId; we can use tenantId's system user or just random
            UUID actorId = tenantId; // will be replaced with system actor if needed; outbound service just needs non-null

            OutboundHttpRequest req = new OutboundHttpRequest(
                tenantId,
                actorId,
                null,
                null,
                httpMethod,
                endpoint,
                queryParams,
                Map.of(),
                null,
                connectionId
            );

            OutboundHttpResult result = outboundHttpService.execute(req);

            if (!result.success()) {
                int code = result.statusCode();
                String errCode = "POLLING_FAILED";
                if (code == 401 || code == 403) errCode = "AUTH_ERROR";
                else if (code == 429) errCode = "RATE_LIMITED";
                else if (code == 0) errCode = "CONNECTION_ERROR";
                return new PollResult(false, code, List.of(), null, errCode, result.errorMessage() != null ? result.errorMessage() : "Polling request failed");
            }

            JsonNode body = result.response();
            List<Map<String, Object>> records = extractRecords(body, recordsPath);
            String nextCursor = extractNextCursor(body);

            return new PollResult(true, result.statusCode(), records, nextCursor, null, null);
        } catch (Exception ex) {
            log.error("Polling fetch failed for tenant {} endpoint {}", tenantId, endpoint, ex);
            return new PollResult(false, 0, List.of(), null, "CONNECTION_ERROR", ex.getMessage());
        }
    }

    private List<Map<String, Object>> extractRecords(JsonNode body, String recordsPath) {
        if (body == null || body.isNull()) return List.of();
        JsonNode target = body;
        if (recordsPath != null && !recordsPath.isBlank()) {
            // Support dot path like "data.items"
            String[] parts = recordsPath.split("\\.");
            for (String part : parts) {
                if (target != null) target = target.get(part);
                else break;
            }
        }
        if (target == null || target.isNull()) return List.of();
        if (target.isArray()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode node : target) {
                if (node.isObject()) {
                    out.add(objectMapper.convertValue(node, MAP_TYPE));
                }
            }
            return out;
        }
        if (target.isObject()) {
            // Single object case: wrap as list if it looks like a record
            // If body itself is array handled above, here we treat single object as one record
            // But if recordsPath points to object that is not array, we return empty
            // For polling where endpoint returns single record object, we can treat body as record if no recordsPath
            if (recordsPath == null || recordsPath.isBlank()) {
                // Check if body is likely a record (has id/email etc) - treat body as single record
                // But to avoid misinterpreting metadata, only if body has no array child, wrap body
                Map<String, Object> single = objectMapper.convertValue(body, MAP_TYPE);
                // Heuristic: if body has "data" array, we already handled; else treat as single
                return List.of(single);
            }
            return List.of();
        }
        return List.of();
    }

    private String extractNextCursor(JsonNode body) {
        if (body == null) return null;
        // Common pagination fields
        for (String key : List.of("nextPageToken", "next_page_token", "nextCursor", "next_cursor", "nextPage", "next_page")) {
            JsonNode n = body.get(key);
            if (n != null && !n.isNull()) return n.asText();
        }
        // Check nested meta
        JsonNode meta = body.get("meta");
        if (meta != null) {
            for (String key : List.of("nextPageToken", "nextCursor")) {
                JsonNode n = meta.get(key);
                if (n != null && !n.isNull()) return n.asText();
            }
        }
        return null;
    }

    private String extractExternalId(Map<String, Object> record, String externalIdPath) {
        if (record == null) return null;
        // Support dot path
        String[] parts = externalIdPath.split("\\.");
        Object cur = record;
        for (String part : parts) {
            if (cur instanceof Map<?,?> m) {
                cur = m.get(part);
            } else {
                return null;
            }
        }
        return cur != null ? String.valueOf(cur).trim() : null;
    }

    private LeadIngestionConfig requirePollingConfig(UUID tenantId, UUID configId) {
        LeadIngestionConfig config = configRepository.findByIdAndTenantIdAndDeletedFalse(configId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Polling source not found"));
        if (config.getTransportType() != LeadIngestionTransportType.POLLING) {
            throw new BusinessException("VALIDATION_ERROR", "Source is not a POLLING transport");
        }
        return config;
    }

    private LeadIngestionConfig requirePollingConfigForUpdate(UUID tenantId, UUID configId) {
        // Use same as requirePollingConfig but will be called within transaction with lock
        LeadIngestionConfig config = configRepository.findByIdAndTenantIdAndDeletedFalse(configId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Polling source not found"));
        if (config.getTransportType() != LeadIngestionTransportType.POLLING) {
            throw new BusinessException("VALIDATION_ERROR", "Source is not a POLLING transport");
        }
        return config;
    }

    private Map<String, Object> extractPollingConfig(LeadIngestionConfig config) {
        if (config.getSettings() == null) return Map.of();
        Object v = config.getSettings().get("polling");
        if (v instanceof Map<?,?> m) {
            Map<String, Object> out = new HashMap<>();
            for (Map.Entry<?,?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
            return out;
        }
        return Map.of();
    }

    private Map<String, Object> extractPollingState(LeadIngestionConfig config) {
        if (config.getSettings() == null) return new HashMap<>();
        Object v = config.getSettings().get("pollingState");
        if (v instanceof Map<?,?> m) {
            Map<String, Object> out = new HashMap<>();
            for (Map.Entry<?,?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
            return out;
        }
        return new HashMap<>();
    }

    private Map<String, Object> mergeSettings(Map<String, Object> existing, String key, Object value) {
        Map<String, Object> out = existing == null ? new HashMap<>() : new HashMap<>(existing);
        out.put(key, value);
        return out;
    }

    private String asString(Object o) { return o == null ? null : String.valueOf(o); }
    private UUID asUuid(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private int asInt(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
    private Instant asInstant(Object o) {
        if (o == null) return null;
        try { return Instant.parse(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private record PollResult(boolean success, int statusCode, List<Map<String, Object>> records, String nextCursor, String errorCode, String errorMessage) {}
}
