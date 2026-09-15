package com.shivang.crm.modules.records.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.records.dto.RecordWebhookDeliveryDetailResponse;
import com.shivang.crm.modules.records.dto.RecordWebhookDeliveryResponse;
import com.shivang.crm.modules.records.entity.RecordWebhook;
import com.shivang.crm.modules.records.entity.RecordWebhookDelivery;
import com.shivang.crm.modules.records.repository.CrmRecordRepository;
import com.shivang.crm.modules.records.repository.RecordMappingProfileRepository;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.modules.records.repository.RecordWebhookDeliveryRepository;
import com.shivang.crm.modules.records.repository.RecordWebhookRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;
import com.shivang.crm.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecordWebhookDeliveryQueryService {

    private final RecordWebhookDeliveryRepository deliveryRepo;
    private final RecordWebhookRepository webhookRepo;
    private final RecordTypeRepository recordTypeRepo;
    private final RecordMappingProfileRepository mappingProfileRepo;
    private final CrmRecordRepository recordRepo;
    private final WorkflowExecutionRepository executionRepo;

    @Transactional(readOnly = true)
    public Page<RecordWebhookDeliveryResponse> list(UUID tenantId, UUID webhookId, String status, int page, int size) {
        webhookRepo.findByIdAndTenantIdAndDeletedFalse(webhookId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordWebhook", webhookId.toString()));
        Page<RecordWebhookDelivery> p;
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        if (status != null && !status.isBlank()) {
            p = deliveryRepo.findByTenantIdAndWebhookIdAndStatusAndDeletedFalse(tenantId, webhookId, status.trim().toUpperCase(), pr);
        } else {
            p = deliveryRepo.findByTenantIdAndWebhookIdAndDeletedFalse(tenantId, webhookId, pr);
        }
        return p.map(d -> toResponse(d, tenantId));
    }

    @Transactional(readOnly = true)
    public RecordWebhookDeliveryDetailResponse detail(UUID tenantId, UUID webhookId, UUID deliveryId) {
        RecordWebhook webhook = webhookRepo.findByIdAndTenantIdAndDeletedFalse(webhookId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordWebhook", webhookId.toString()));
        RecordWebhookDelivery delivery = deliveryRepo.findByIdAndTenantIdAndDeletedFalse(deliveryId, tenantId)
                .orElseThrow(() -> new NotFoundException("RecordWebhookDelivery", deliveryId.toString()));
        if (!delivery.getWebhookId().equals(webhookId)) {
            throw new NotFoundException("RecordWebhookDelivery", deliveryId.toString());
        }
        // Build enriched detail
        var webhookInfo = RecordWebhookDeliveryDetailResponse.WebhookInfo.builder()
                .id(webhook.getId())
                .key(webhook.getWebhookKey())
                .name(webhook.getName())
                .build();

        var rtInfo = resolveRecordType(delivery.getRecordTypeId(), tenantId);
        var mpInfo = resolveMapping(delivery.getMappingProfileId(), tenantId);

        boolean isReplay = isReplay(delivery);
        var idem = RecordWebhookDeliveryDetailResponse.IdempotencyInfo.builder()
                .applied(isReplay)
                .key(delivery.getIdempotencyKey())
                .payloadHash(delivery.getPayloadHash())
                .build();

        var recordInfo = delivery.getRecordId() != null
                ? RecordWebhookDeliveryDetailResponse.RecordInfo.builder().id(delivery.getRecordId()).build()
                : null;

        var eventInfo = delivery.getEventId() != null
                ? RecordWebhookDeliveryDetailResponse.EventInfo.builder()
                    .eventId(delivery.getEventId())
                    .entityType("RECORD")
                    .eventType("RECEIVED")
                    .build()
                : null;

        // Workflow executions correlated by triggerEventId (eventId)
        List<RecordWebhookDeliveryDetailResponse.ExecutionInfo> execInfos = List.of();
        boolean triggered = false;
        if (delivery.getEventId() != null) {
            var execs = executionRepo.findWithWorkflowByTenantIdAndTriggerEventId(tenantId, delivery.getEventId());
            execInfos = execs.stream().map(e -> RecordWebhookDeliveryDetailResponse.ExecutionInfo.builder()
                    .id(e.getId())
                    .workflowId(e.getWorkflow() != null ? e.getWorkflow().getId() : null)
                    .workflowName(e.getWorkflow() != null ? e.getWorkflow().getName() : null)
                    .workflowVersionId(e.getWorkflowVersion() != null ? e.getWorkflowVersion().getId() : null)
                    .status(e.getStatus() != null ? e.getStatus().name() : null)
                    .triggerEventId(e.getTriggerEventId())
                    .build()).collect(Collectors.toList());
            triggered = !execInfos.isEmpty();
        }

        var workflowInfo = RecordWebhookDeliveryDetailResponse.WorkflowInfo.builder()
                .triggered(triggered)
                .executionCount(execInfos.size())
                .executions(execInfos)
                .build();

        var failure = RecordWebhookDeliveryDetailResponse.FailureInfo.builder()
                .stage(delivery.getFailureStage())
                .code(delivery.getErrorCode())
                .message(sanitize(delivery.getErrorMessage()))
                .build();

        var dl = RecordWebhookDeliveryDetailResponse.Delivery.builder()
                .id(delivery.getId())
                .status(delivery.getStatus())
                .failureStage(delivery.getFailureStage())
                .responseStatus(delivery.getResponseStatus())
                .errorCode(delivery.getErrorCode())
                .errorMessage(sanitize(delivery.getErrorMessage()))
                .payloadHash(delivery.getPayloadHash())
                .idempotencyKey(delivery.getIdempotencyKey())
                .receivedAt(delivery.getReceivedAt())
                .createdAt(delivery.getCreatedAt())
                .build();

        return RecordWebhookDeliveryDetailResponse.builder()
                .delivery(dl)
                .webhook(webhookInfo)
                .recordType(rtInfo)
                .mappingProfile(mpInfo)
                .idempotency(idem)
                .record(recordInfo)
                .event(eventInfo)
                .workflow(workflowInfo)
                .failure(failure)
                .build();
    }

    private RecordWebhookDeliveryResponse toResponse(RecordWebhookDelivery d, UUID tenantId) {
        // Resolve names for list triage (best effort, tenant-scoped)
        String rtKey = null, rtName = null;
        if (d.getRecordTypeId() != null) {
            var rtOpt = recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(d.getRecordTypeId(), tenantId);
            if (rtOpt.isPresent()) { rtKey = rtOpt.get().getKey(); rtName = rtOpt.get().getName(); }
            else { rtKey = d.getRecordTypeId().toString().substring(0, 8); rtName = "Deleted/Unknown"; }
        }
        String mpKey = null, mpName = null, mpMode = null;
        if (d.getMappingProfileId() != null) {
            var mpOpt = mappingProfileRepo.findByIdAndTenantIdAndDeletedFalse(d.getMappingProfileId(), tenantId);
            if (mpOpt.isPresent()) { mpKey = mpOpt.get().getMappingKey(); mpName = mpOpt.get().getName(); mpMode = mpOpt.get().getMode() != null ? mpOpt.get().getMode().name() : null; }
            else { mpKey = d.getMappingProfileId().toString().substring(0, 8); mpName = "Deleted/Unknown"; }
        }
        String webhookName = null;
        var whOpt = webhookRepo.findByIdAndTenantIdAndDeletedFalse(d.getWebhookId(), tenantId);
        if (whOpt.isPresent()) webhookName = whOpt.get().getName();

        // Determine if this delivery was a replay: look for another delivery with same idempotencyKey but earlier createdAt?
        // Simpler: if status SUCCESS and there exists earlier delivery with same key, mark applied = false for newest? For list, we expose idempotencyApplied as false for first, true for replay hit? Actually replay hit is not stored as new delivery (returns cached). So all stored deliveries are NEW, not replays. Replay is visible via idempotency hit path which returns existing delivery, not new. So for list, idempotencyApplied is false.
        // We set false for now; detail will compute applied based on existence of duplicate key count?
        // For observability, we consider a delivery with status SUCCESS and idempotencyKey non-empty as NEW, replay is not a new row.

        return RecordWebhookDeliveryResponse.builder()
                .id(d.getId())
                .tenantId(d.getTenantId())
                .webhookId(d.getWebhookId())
                .webhookKey(d.getWebhookKey())
                .webhookName(webhookName)
                .recordTypeId(d.getRecordTypeId())
                .recordTypeKey(rtKey)
                .recordTypeName(rtName)
                .mappingProfileId(d.getMappingProfileId())
                .mappingProfileKey(mpKey)
                .mappingProfileName(mpName)
                .mappingMode(mpMode)
                .status(d.getStatus())
                .failureStage(d.getFailureStage())
                .errorCode(d.getErrorCode())
                .errorMessage(sanitize(d.getErrorMessage()))
                .payloadHash(d.getPayloadHash())
                .idempotencyKey(d.getIdempotencyKey())
                .recordId(d.getRecordId())
                .eventId(d.getEventId())
                .responseStatus(d.getResponseStatus())
                .receivedAt(d.getReceivedAt())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .idempotencyApplied(false)
                .build();
    }

    private RecordWebhookDeliveryDetailResponse.RecordTypeInfo resolveRecordType(UUID rtId, UUID tenantId) {
        if (rtId == null) return null;
        var opt = recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(rtId, tenantId);
        if (opt.isPresent()) {
            return RecordWebhookDeliveryDetailResponse.RecordTypeInfo.builder()
                    .id(opt.get().getId())
                    .key(opt.get().getKey())
                    .name(opt.get().getName())
                    .build();
        }
        // Deleted/inactive still show id but indicate deleted
        return RecordWebhookDeliveryDetailResponse.RecordTypeInfo.builder()
                .id(rtId)
                .key(rtId.toString().substring(0, 8))
                .name("Deleted/Unknown")
                .build();
    }

    private RecordWebhookDeliveryDetailResponse.MappingInfo resolveMapping(UUID mpId, UUID tenantId) {
        if (mpId == null) return null;
        var opt = mappingProfileRepo.findByIdAndTenantIdAndDeletedFalse(mpId, tenantId);
        if (opt.isPresent()) {
            var mp = opt.get();
            return RecordWebhookDeliveryDetailResponse.MappingInfo.builder()
                    .id(mp.getId())
                    .key(mp.getMappingKey())
                    .name(mp.getName())
                    .mode(mp.getMode() != null ? mp.getMode().name() : null)
                    .build();
        }
        return RecordWebhookDeliveryDetailResponse.MappingInfo.builder()
                .id(mpId)
                .key(mpId.toString().substring(0, 8))
                .name("Deleted/Unknown")
                .mode(null)
                .build();
    }

    private boolean isReplay(RecordWebhookDelivery d) {
        // A delivery is considered replay if another delivery with same tenant/webhook/key exists earlier
        // For simplicity, we treat all stored deliveries as NEW; replay is observed via 200 cached response, not new row.
        // So return false; detail can show idempotency key presence.
        return false;
    }

    private String sanitize(String msg) {
        if (msg == null) return null;
        String lower = msg.toLowerCase();
        if (lower.contains("api-key") || lower.contains("apikey") || lower.contains("hmac") || lower.contains("secret") || lower.contains("authorization") || lower.contains("signature") || lower.contains("credential")) {
            return "Authentication failed";
        }
        if (msg.length() > 500) return msg.substring(0, 500);
        return msg;
    }
}
