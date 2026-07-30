package com.shivang.crm.modules.integration.service.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.entity.Call.CallStatus;
import com.shivang.crm.modules.call.entity.Call.CallType;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.dialer.dto.CallOpeningInstruction;
import com.shivang.crm.modules.dialer.entity.CallOpeningEvent;
import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.dialer.service.CallOpeningDecisionService;
import com.shivang.crm.modules.dialer.service.CallOpeningEventService;
import com.shivang.crm.modules.dialer.service.CallProviderLinkService;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallWebhookMappingApplier {

    private final CallProviderLinkService linkService;
    private final CallRepository callRepository;
    private final ActivityService activityService;
    private final CallOpeningDecisionService decisionService;
    private final CallOpeningEventService eventService;

    private final ConnectorUserAgentService connectorUserAgentService;

    // ─── Connect Webhook ──────────────────────────────────────────────
    @Transactional
    public String applyConnect(
            UUID tenantId,
            UUID connectorInstanceId,
            NormalizedCallWebhookEvent event,
            String providerKey) {

        if (event == null) {
            return "NULL_EVENT";
        }

        CallProviderLink link = resolveLink(
                tenantId,
                event
        );

        if (link != null) {
            return handleOutboundConnect(
                    tenantId,
                    link,
                    event,
                    providerKey
            );
        }

        String direction = normalizeDirection(
                event.getDirection()
        );

        if ("INBOUND".equals(direction)) {
            return handleInboundConnect(
                    tenantId,
                    connectorInstanceId,
                    event,
                    providerKey
            );
        }

        log.warn(
                "Call-connect could not be correlated and will not create an inbound call. "
                + "tenant={} provider={} direction={} externalCallId={} correlationKey={}",
                tenantId,
                providerKey,
                direction,
                event.getExternalCallId(),
                event.getCorrelationKey()
        );

        return "PENDING_CORRELATION";
    }

    private String handleOutboundConnect(UUID tenantId, CallProviderLink link, NormalizedCallWebhookEvent event,
            String providerKey) {
        Call call = link.getCall();

        // Attach externalCallId to the link if not already set
        if (event.getExternalCallId() != null && link.getExternalCallId() == null) {
            link.setExternalCallId(event.getExternalCallId());
        }
        if (event.getAgentId() != null) {
            link.setExternalAgentId(event.getAgentId());
        }
        linkService.save(link);

        // Update call start time
        Instant startTime = event.getEventTimestamp() != null ? event.getEventTimestamp() : event.getStartedAt();
        if (call.getStartTime() == null && startTime != null) {
            call.setStartTime(startTime);
        }
        callRepository.save(call);

        // Build activity metadata
        Map<String, Object> metadata = buildConnectMetadata(call, event, providerKey);

        // Decide opening instruction — target the user who initiated the call
        UUID targetUserId = call.getCreatedBy();
        var decision = decisionService.decide(tenantId, event, link);
        if (decision.shouldOpen()) {
            var instr = decision.instruction();
            // Ensure the instruction has the CRM callId for active call navigation
            if (instr.getCallId() == null) {
                instr.setCallId(call.getId().toString());
            }
            eventService.createEvent(tenantId, targetUserId, event.getAgentId(), call.getId(),
                    event.getExternalCallId(), providerKey, decision.triggerKey(), instr);
            metadata.put("openingActionType", instr.getActionType());
            metadata.put("openingTriggerKey", decision.triggerKey());
        } else {
            // Even if no trigger matched, create a default OPEN_CALL_LAYOUT event for
            // outbound
            CallOpeningInstruction defaultInstr = CallOpeningInstruction.builder()
                    .actionType("OPEN_CALL_LAYOUT")
                    .callId(call.getId().toString())
                    .externalCallId(event.getExternalCallId())
                    .resolved(true)
                    .reason("Outbound call connected - default opening")
                    .build();
            eventService.createEvent(tenantId, targetUserId, event.getAgentId(), call.getId(),
                    event.getExternalCallId(), providerKey, "call-connect", defaultInstr);
            metadata.put("openingActionType", "OPEN_CALL_LAYOUT");
        }

        activityService.logSystemActivity(
                tenantId,
                call.getId(),
                "CALL",
                "CALL_CONNECTED",
                "Call connected from provider",
                "WEBHOOK:" + providerKey,
                metadata);
        return "PROCESSED";
    }

    private String handleInboundConnect(UUID tenantId, UUID connectorInstanceId, NormalizedCallWebhookEvent event, String providerKey) {
        // Create an inbound Call
        String phone = event.getCallerNumber();
        Instant startTime = event.getEventTimestamp() != null ? event.getEventTimestamp() : event.getStartedAt();

        Call call = Call.builder()
                .tenantId(tenantId)
                .subject("Inbound Call" + (phone != null ? " from " + phone : ""))
                .callType(CallType.INCOMING)
                .status(CallStatus.PLANNED)
                .phoneNumber(phone)
                .startTime(startTime)
                .createdBy(null)
                .updatedBy(null)
                .actorType("SYSTEM")
                .actorSource("WEBHOOK:" + providerKey)
                .build();

        Call savedCall = callRepository.save(call);
        log.info("Created inbound Call {} for tenant {} from connect webhook", savedCall.getId(), tenantId);

        log.info(
                "Saving inbound provider link callId={} externalCallId={}",
                savedCall.getId(),
                event.getExternalCallId()
        );

        // Create provider link
        CallProviderLink link = CallProviderLink.builder()
                .tenantId(tenantId)
                .call(savedCall)
                .externalCallId(event.getExternalCallId())
                .correlationKey(event.getCorrelationKey())
                .externalAgentId(event.getAgentId())
                .linkedAt(Instant.now())
                .metadata(Map.of("providerKey", providerKey, "direction", "inbound"))
                .build();

        CallProviderLink savedLink = linkService.save(link);

        log.info(
                "Inbound provider link saved linkId={} callId={}",
                savedLink.getId(),
                savedCall.getId()
        );

        log.info(
                "Evaluating inbound opening decision callId={}",
                savedCall.getId()
        );

        // Decide opening
        var decision = decisionService.decide(tenantId, event, link);
        CallOpeningInstruction instr;
        if (decision.shouldOpen()) {
            instr = decision.instruction();
            if (instr.getCallId() == null) {
                instr.setCallId(savedCall.getId().toString());
            }
        } else {
            // Default: open active call page for unknown/unresolved
            instr = CallOpeningInstruction.builder()
                    .actionType("OPEN_CALL_LAYOUT")
                    .callId(savedCall.getId().toString())
                    .externalCallId(event.getExternalCallId())
                    .resolved(false)
                    .reason("Inbound call - no trigger matched, opening active call")
                    .build();
        }

        log.info(
                "Inbound opening decision completed callId={} shouldOpen={} "
                + "triggerKey={} reason={}",
                savedCall.getId(),
                decision.shouldOpen(),
                decision.triggerKey(),
                decision.reason()
        );

        // For inbound, userId is null (we don't know which CRM user yet)
        // The opening event will be delivered to all users in the tenant's pending
        // queue
        UUID targetUserId
                = connectorUserAgentService
                        .resolveUserId(
                                tenantId,
                                connectorInstanceId,
                                event.getAgentId(),
                                event.getAgentNumber()
                        )
                        .orElseThrow(()
                                -> new BusinessException(
                                "AGENT_NOT_MAPPED",
                                "No CRM user is mapped to provider agent "
                                + event.getAgentId()
                        )
                        );

        log.info(
                "Resolving inbound target user tenant={} connector={} "
                + "agentId={} agentNumber={}",
                tenantId,
                connectorInstanceId,
                event.getAgentId(),
                event.getAgentNumber()
        );

        log.info(
                "Creating inbound opening event tenant={} user={} callId={} "
                + "agentId={} instruction={}",
                tenantId,
                targetUserId,
                savedCall.getId(),
                event.getAgentId(),
                instr
        );

        CallOpeningEvent openingEvent = eventService.createEvent(
                tenantId,
                targetUserId,
                event.getAgentId(),
                savedCall.getId(),
                event.getExternalCallId(),
                providerKey,
                decision.shouldOpen()
                ? decision.triggerKey()
                : "call-connect",
                instr
        );

        log.info(
                "Inbound opening event created eventId={} tenant={} "
                + "user={} callId={} status={}",
                openingEvent.getId(),
                openingEvent.getTenantId(),
                openingEvent.getUserId(),
                openingEvent.getCallId(),
                openingEvent.getDeliveryStatus()
        );

        Map<String, Object> metadata = buildConnectMetadata(savedCall, event, providerKey);
        activityService.logSystemActivity(
                tenantId,
                savedCall.getId(),
                "CALL",
                "CALL_CONNECTED",
                "Inbound call connected from provider",
                "WEBHOOK:" + providerKey,
                metadata);

        return "PROCESSED_INBOUND";
    }

    // ─── CDR Webhook ──────────────────────────────────────────────────
    @Transactional
    public String applyCdr(UUID tenantId, NormalizedCallWebhookEvent event, String providerKey) {
        if (event == null) {
            return "NULL_EVENT";
        }

        // Correlation: 1. tenant + externalCallId, 2. tenant + correlationKey
        CallProviderLink link = resolveLink(tenantId, event);
        if (link == null) {
            log.warn(
                    "CDR webhook received but no CallProviderLink found for tenant={} externalCallId={} correlationKey={}",
                    tenantId, event.getExternalCallId(), event.getCorrelationKey());
            return "PENDING_CORRELATION";
        }

        Call call = link.getCall();

        // Idempotency: if call already has endTime, skip duplicate CDR
        if (call.getEndTime() != null) {
            log.info("CDR webhook for call {} already completed, skipping duplicate", call.getId());
            return "DUPLICATE_CDR_IGNORED";
        }

        // Update externalCallId on the link if missing
        if (event.getExternalCallId() != null && link.getExternalCallId() == null) {
            link.setExternalCallId(event.getExternalCallId());
            linkService.save(link);
        }

        // Set startTime if connect was missed
        if (call.getStartTime() == null && event.getStartedAt() != null) {
            call.setStartTime(event.getStartedAt());
        }

        // Set endTime
        if (event.getEndedAt() != null) {
            call.setEndTime(event.getEndedAt());
        } else if (event.getEventTimestamp() != null) {
            call.setEndTime(event.getEventTimestamp());
        }

        // Duration: store exact seconds
        if (event.getDurationSeconds() != null) {
            call.setDurationSeconds(event.getDurationSeconds());
            call.setDurationMinutes(event.getDurationSeconds() / 60); // backward compat
        }

        // Recording URL: store in proper column
        if (event.getRecordingUrl() != null) {
            call.setRecordingUrl(event.getRecordingUrl());
        }

        // Also store in customData for backward compatibility
        Map<String, Object> custom = call.getCustomData() == null ? new HashMap<>()
                : new HashMap<>(call.getCustomData());
        if (event.getRecordingUrl() != null) {
            custom.put("recordingUrl", event.getRecordingUrl());
        }
        if (event.getDisposition() != null) {
            custom.put("disposition", event.getDisposition());
        }
        if (event.getProviderStatus() != null) {
            custom.put("providerStatus", event.getProviderStatus());
        }
        call.setCustomData(custom);

        // Set status to HELD to mark completed
        call.setStatus(CallStatus.HELD);

        callRepository.save(call);
        log.info("CDR completed for Call {} - duration={}s recording={}", call.getId(), event.getDurationSeconds(),
                event.getRecordingUrl());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subType", "CALL_COMPLETED");
        metadata.put("crmCallId", call.getId().toString());
        metadata.put("providerKey", providerKey);
        metadata.put("externalCallId", event.getExternalCallId());
        metadata.put("agentId", event.getAgentId());
        metadata.put("durationSeconds", event.getDurationSeconds());
        metadata.put("status", event.getProviderStatus());
        metadata.put("recordingUrl", event.getRecordingUrl());
        metadata.put("disposition", event.getDisposition());

        activityService.logSystemActivity(
                tenantId,
                call.getId(),
                "CALL",
                "CALL_COMPLETED",
                "Call completed from provider",
                "WEBHOOK:" + providerKey,
                metadata);
        return "PROCESSED";
    }

    // ─── Helpers ──────────────────────────────────────────────────────
    /**
     * Resolve CallProviderLink by correlation order: 1. tenant + externalCallId
     * 2. tenant + correlationKey
     */
    private CallProviderLink resolveLink(
            UUID tenantId,
            NormalizedCallWebhookEvent event) {

        String externalCallId = event.getExternalCallId();
        String correlationKey = event.getCorrelationKey();

        log.info(
                "Resolving call link tenant={} externalCallId={} correlationKey={}",
                tenantId,
                externalCallId,
                correlationKey
        );

        if (hasUsableIdentifier(externalCallId)) {
            Optional<CallProviderLink> byExternal
                    = linkService
                            .findByTenantIdAndExternalCallIdAndDeletedFalse(
                                    tenantId,
                                    externalCallId.trim()
                            );

            if (byExternal.isPresent()) {
                log.info(
                        "Resolved provider link {} using externalCallId={}",
                        byExternal.get().getId(),
                        externalCallId
                );

                return byExternal.get();
            }
        }

        if (hasUsableIdentifier(correlationKey)) {
            Optional<CallProviderLink> byCorrelation
                    = linkService
                            .findByTenantIdAndCorrelationKeyAndDeletedFalse(
                                    tenantId,
                                    correlationKey.trim()
                            );

            if (byCorrelation.isPresent()) {
                log.info(
                        "Resolved provider link {} using correlationKey={}",
                        byCorrelation.get().getId(),
                        correlationKey
                );

                return byCorrelation.get();
            }
        }

        log.warn(
                "No provider link matched tenant={} externalCallId={} correlationKey={}",
                tenantId,
                externalCallId,
                correlationKey
        );

        return null;
    }

    private Map<String, Object> buildConnectMetadata(Call call, NormalizedCallWebhookEvent event, String providerKey) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subType", "CALL_CONNECTED");
        metadata.put("crmCallId", call.getId().toString());
        metadata.put("providerKey", providerKey);
        metadata.put("externalCallId", event.getExternalCallId());
        metadata.put("correlationKey", event.getCorrelationKey());
        metadata.put("agentId", event.getAgentId());
        metadata.put("agentNumber", event.getAgentNumber());
        metadata.put("direction", event.getDirection());
        metadata.put("callerNumber", event.getCallerNumber());
        metadata.put("calleeNumber", event.getCalleeNumber());
        return metadata;
    }

    private String normalizeDirection(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }

        return switch (value.trim().toUpperCase()) {
            case "INCOMING", "INBOUND", "IN" ->
                "INBOUND";
            case "OUTGOING", "OUTBOUND", "OUT" ->
                "OUTBOUND";
            default ->
                "UNKNOWN";
        };
    }

    private boolean hasUsableIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim();

        return !normalized.equals("0")
                && !normalized.equalsIgnoreCase("NA")
                && !normalized.equalsIgnoreCase("N/A")
                && !normalized.equalsIgnoreCase("NULL")
                && !normalized.equalsIgnoreCase("UNDEFINED")
                && !normalized.equals("-");
    }

}
