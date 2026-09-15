package com.shivang.crm.modules.records.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.records.dto.CrmRecordCreateRequest;
import com.shivang.crm.modules.records.dto.CrmRecordResponse;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordMappingProfile;
import com.shivang.crm.modules.records.entity.RecordType;
import com.shivang.crm.modules.records.entity.RecordWebhook;
import com.shivang.crm.modules.records.entity.RecordWebhookDelivery;
import com.shivang.crm.modules.records.repository.RecordFieldRepository;
import com.shivang.crm.modules.records.repository.RecordMappingProfileRepository;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.modules.records.repository.RecordWebhookDeliveryRepository;
import com.shivang.crm.modules.records.repository.RecordWebhookRepository;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordWebhookIngestionService {

    private final RecordWebhookRepository webhookRepo;
    private final RecordTypeRepository recordTypeRepo;
    private final RecordFieldRepository recordFieldRepo;
    private final RecordMappingProfileRepository mappingProfileRepo;
    private final RecordWebhookDeliveryRepository deliveryRepo;
    private final RecordMappingService recordMappingService;
    private final CrmRecordService crmRecordService;
    private final CanonicalCrmEventPublisher eventPublisher;
    private final RecordWebhookIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    /**
     * Transactional ingestion: record + delivery + outbox atomically.
     * Called only after authentication and idempotency miss.
     */
    @Transactional
    public IngestionResult ingestNew(
            RecordWebhook webhook,
            Map<String, Object> payloadMap,
            byte[] rawBody,
            String idempotencyKey,
            String payloadHash) {

        UUID tenantId = webhook.getTenantId();
        UUID webhookId = webhook.getId();
        UUID recordTypeId = webhook.getRecordTypeId();

        RecordType recordType = recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(recordTypeId, tenantId).orElse(null);
        if (recordType == null || Boolean.FALSE.equals(recordType.getIsActive())) {
            throw new BusinessException("INVALID_CONFIGURATION", "Webhook RecordType is inactive or deleted");
        }

        var fields = recordFieldRepo.findByRecordTypeIdAndTenantIdAndDeletedFalseOrderByDisplayOrder(recordTypeId, tenantId);
        Map<UUID, RecordField> fieldById = new HashMap<>();
        for (RecordField f : fields) fieldById.put(f.getId(), f);

        Map<String, Object> canonicalData;
        if (webhook.getMappingProfileId() == null) {
            RecordMappingProfile directProfile = RecordMappingProfile.builder()
                    .mode(com.shivang.crm.modules.records.entity.MappingProfileMode.DIRECT)
                    .configuration(Map.of())
                    .build();
            canonicalData = recordMappingService.map(directProfile, payloadMap, fieldById);
        } else {
            RecordMappingProfile profile = mappingProfileRepo.findByIdAndTenantIdAndDeletedFalse(webhook.getMappingProfileId(), tenantId).orElse(null);
            if (profile == null || Boolean.TRUE.equals(profile.isDeleted()) || Boolean.FALSE.equals(profile.getIsActive())) {
                throw new BusinessException("INVALID_CONFIGURATION", "Webhook mapping profile is inactive or deleted");
            }
            if (!profile.getRecordTypeId().equals(recordTypeId)) {
                throw new BusinessException("INVALID_CONFIGURATION", "Mapping profile does not belong to webhook RecordType");
            }
            canonicalData = recordMappingService.map(profile, payloadMap, fieldById);
        }

        UUID actorUserId = webhook.getCreatedBy() != null ? webhook.getCreatedBy()
                : webhook.getOwnerId() != null ? webhook.getOwnerId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");

        // Create record (joins this transaction, includes validation)
        var createReq = CrmRecordCreateRequest.builder().recordTypeId(recordTypeId).data(canonicalData).build();
        CrmRecordResponse created = crmRecordService.create(tenantId, actorUserId, createReq);

        // Create delivery SUCCESS
        Map<String, Object> responseBody = Map.of(
                "recordId", created.getId().toString(),
                "recordTypeId", recordTypeId.toString(),
                "status", "CREATED"
        );
        RecordWebhookDelivery delivery = RecordWebhookDelivery.builder()
                .tenantId(tenantId)
                .webhookId(webhookId)
                .webhookKey(webhook.getWebhookKey())
                .idempotencyKey(idempotencyKey != null ? idempotencyKey : "no-key-" + UUID.randomUUID())
                .payloadHash(payloadHash)
                .status("SUCCESS")
                .recordId(created.getId())
                .responseStatus(201)
                .responseBody(responseBody)
                .receivedAt(Instant.now())
                .build();
        // Only persist delivery if idempotencyKey present; otherwise still persist for audit? Spec says idempotency only when key present, but we persist delivery only when key present to support replay.
        // For idempotency, we only need to store when key present. For non-idempotent, we could still store but not needed for WF-51/52. We will store only when key present.
        // However for WF-52, we need delivery for idempotency replay, so if key is null we don't store.
        // But for eventing, we need deliveryId for provenance – if no key, deliveryId is null in event? Spec says deliveryId should be in event. For non-idempotent, we could generate a delivery record anyway.
        // For now, only store when idempotencyKey != null.
        // Publish canonical event RECORD.RECEIVED in same transaction (need deliveryId for correlation)
        // For observability, generate deliveryId upfront if not yet persisted
        UUID preGeneratedDeliveryId = UUID.randomUUID();
        String effectiveIdempotencyKey = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : "no-key-" + preGeneratedDeliveryId;
        // Create delivery first to obtain ID for event metadata, but also include operational correlation
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("recordTypeId", recordTypeId.toString());
        metadata.put("webhookId", webhookId.toString());
        // deliveryId will be set after delivery creation; use placeholder then update
        // We create delivery with eventId after publish, so we need two-phase
        // First create delivery without eventId to get ID
        RecordWebhookDelivery savedDelivery;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            savedDelivery = idempotencyService.createDelivery(tenantId, webhookId, webhook.getWebhookKey(), idempotencyKey, payloadHash, "SUCCESS", created.getId(), 201, responseBody, null, null, recordTypeId, webhook.getMappingProfileId(), null, "SUCCESS");
        } else {
            // For non-idempotent, create a transient delivery for event provenance (not persisted for idempotency) but still operationally visible
            // Persist it for observability even without idempotencyKey
            savedDelivery = idempotencyService.createDelivery(tenantId, webhookId, webhook.getWebhookKey(), effectiveIdempotencyKey, payloadHash, "SUCCESS", created.getId(), 201, responseBody, null, null, recordTypeId, webhook.getMappingProfileId(), null, "SUCCESS");
        }
        metadata.put("deliveryId", savedDelivery.getId().toString());
        metadata.put("actorId", actorUserId.toString());
        metadata.put("actorType", "USER");

        CanonicalCrmEvent event = eventPublisher.publishAndReturn(
                tenantId,
                CanonicalCrmEvent.RECORD_ENTITY_TYPE,
                CanonicalCrmEvent.RECEIVED_EVENT_TYPE,
                created.getId(),
                metadata
        );
        // Update delivery with eventId for correlation (same TX)
        savedDelivery.setEventId(event.eventId());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            // For idempotent, update persisted delivery with eventId
            try { deliveryRepo.save(savedDelivery); } catch (Exception ignored) {}
        } else {
            // For non-idempotent we already persisted via createDelivery with generated key, so update eventId
            try { deliveryRepo.save(savedDelivery); } catch (Exception ignored) {}
        }

        return new IngestionResult(created, savedDelivery, event);
    }

    public static class IngestionResult {
        public final CrmRecordResponse record;
        public final RecordWebhookDelivery delivery;
        public final CanonicalCrmEvent event;
        public IngestionResult(CrmRecordResponse r, RecordWebhookDelivery d, CanonicalCrmEvent e) { this.record=r; this.delivery=d; this.event=e; }
    }
}
