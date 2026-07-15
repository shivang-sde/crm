package com.shivang.crm.modules.integration.service.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.dialer.service.CallProviderLinkService;
import com.shivang.crm.modules.dialer.service.CallOpeningDecisionService;
import com.shivang.crm.modules.dialer.service.CallOpeningEventService;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;

import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CallWebhookMappingApplier {

    private final CallProviderLinkService linkService;
    private final CallRepository callRepository;
    private final ActivityService activityService;
    private final CallOpeningDecisionService decisionService;
    private final CallOpeningEventService eventService;

    @Transactional
    public String applyConnect(NormalizedCallWebhookEvent event, String providerKey) {
        if (event == null || event.getExternalCallId() == null) return "MISSING_EXTERNAL_CALL_ID";
        CallProviderLink link = linkService.findByExternalCallId(event.getExternalCallId()).orElse(null);
        if (link == null) return "PENDING_CORRELATION";

        Call call = link.getCall();
        // set start time if not set
        if (call.getStartTime() == null && event.getEventTimestamp() != null) call.setStartTime(event.getEventTimestamp());

        // persist updates
        callRepository.save(call);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subType", "CALL_CONNECTED");
        metadata.put("crmCallId", call.getId().toString());
        metadata.put("providerKey", providerKey);
        metadata.put("externalCallId", event.getExternalCallId());
        metadata.put("agentId", event.getAgentId());
        metadata.put("direction", event.getDirection());
        metadata.put("callerNumber", event.getCallerNumber());
        metadata.put("calleeNumber", event.getCalleeNumber());

        // decide opening instruction
        var decision = decisionService.decide(call.getTenantId(), event);
        if (decision.shouldOpen()) {
            // persist event with callId and providerKey
            eventService.createEvent(call.getTenantId(), null, event.getAgentId(), call.getId(), event.getExternalCallId(), providerKey, decision.triggerKey(), decision.instruction());
            metadata.put("openingActionType", decision.instruction().getActionType());
            metadata.put("openingEntityType", decision.instruction().getEntityType());
            metadata.put("openingEntityId", decision.instruction().getEntityId());
            metadata.put("openingResolved", decision.instruction().getResolved());
            metadata.put("openingTriggerKey", decision.triggerKey());
            metadata.put("openingReason", decision.reason());
        }

        activityService.logActivity(call.getTenantId(), call.getId(), "CALL", "CALL", "Call connected from provider", null, metadata);
        return "PROCESSED";
    }

    @Transactional
    public String applyCdr(NormalizedCallWebhookEvent event, String providerKey) {
        if (event == null || event.getExternalCallId() == null) return "MISSING_EXTERNAL_CALL_ID";
        CallProviderLink link = linkService.findByExternalCallId(event.getExternalCallId()).orElse(null);
        if (link == null) return "PENDING_CORRELATION";

        Call call = link.getCall();
        if (event.getDurationSeconds() != null) call.setDurationMinutes(event.getDurationSeconds() / 60);
        if (event.getEndedAt() == null && event.getEventTimestamp() != null) call.setEndTime(event.getEventTimestamp());
        // set status to HELD to mark completed
        call.setStatus(Call.CallStatus.HELD);

        // store recording/disposition into customData
        Map<String, Object> custom = call.getCustomData() == null ? new HashMap<>() : new HashMap<>(call.getCustomData());
        if (event.getRecordingUrl() != null) custom.put("recordingUrl", event.getRecordingUrl());
        if (event.getDisposition() != null) custom.put("disposition", event.getDisposition());
        call.setCustomData(custom);

        callRepository.save(call);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subType", "CALL_COMPLETED");
        metadata.put("crmCallId", call.getId().toString());
        metadata.put("providerKey", providerKey);
        metadata.put("externalCallId", event.getExternalCallId());
        metadata.put("agentId", event.getAgentId());
        metadata.put("duration", event.getDurationSeconds());
        metadata.put("status", event.getProviderStatus());
        metadata.put("recordingUrl", event.getRecordingUrl());
        metadata.put("disposition", event.getDisposition());

        activityService.logActivity(call.getTenantId(), call.getId(), "CALL", "CALL", "Call completed from provider", null, metadata);
        return "PROCESSED";
    }
}
