package com.shivang.crm.modules.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.dialer.service.CallOpeningDecisionService.DecisionResult;
import com.shivang.crm.modules.dialer.dto.CallOpeningInstruction;
import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.dialer.service.CallOpeningDecisionService;
import com.shivang.crm.modules.dialer.service.CallOpeningEventService;
import com.shivang.crm.modules.dialer.service.CallProviderLinkService;
import com.shivang.crm.modules.integration.service.impl.CallWebhookMappingApplier;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;

class CallWebhookMappingApplierTest {

    private CallProviderLinkService linkService;
    private CallRepository callRepository;
    private ActivityService activityService;
    private CallOpeningDecisionService decisionService;
    private CallOpeningEventService eventService;
    private CallWebhookMappingApplier applier;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        linkService = mock(CallProviderLinkService.class);
        callRepository = mock(CallRepository.class);
        activityService = mock(ActivityService.class);
        decisionService = mock(CallOpeningDecisionService.class);
        eventService = mock(CallOpeningEventService.class);
        applier = new CallWebhookMappingApplier(linkService, callRepository, activityService, decisionService, eventService);
        tenantId = UUID.randomUUID();
    }

    @Test
    void testApplyConnect_OutboundExistingLink() {
        UUID callId = UUID.randomUUID();
        Call call = Call.builder().id(callId).createdBy(UUID.randomUUID()).build();
        CallProviderLink link = CallProviderLink.builder().call(call).correlationKey(callId.toString()).build();

        NormalizedCallWebhookEvent event = NormalizedCallWebhookEvent.builder()
            .correlationKey(callId.toString())
            .externalCallId("ext-123")
            .agentId("agent-1")
            .eventTimestamp(Instant.now())
            .build();

        when(linkService.findByTenantIdAndCorrelationKeyAndDeletedFalse(tenantId, callId.toString()))
            .thenReturn(Optional.of(link));

        CallOpeningInstruction instr = CallOpeningInstruction.builder().actionType("OPEN_PAGE").build();
        when(decisionService.decide(any(), any())).thenReturn(new DecisionResult(instr, true, "trigger", "reason"));

        String result = applier.applyConnect(tenantId, event, "sellspark_voice");

        assertThat(result).isEqualTo("PROCESSED");
        verify(linkService).save(link);
        assertThat(link.getExternalCallId()).isEqualTo("ext-123");
        verify(callRepository).save(call);
        verify(eventService).createEvent(eq(tenantId), eq(call.getCreatedBy()), eq("agent-1"), eq(callId), eq("ext-123"), eq("sellspark_voice"), eq("trigger"), any());
    }

    @Test
    void testApplyCdr_SetsEndTimeAndDuration() {
        UUID callId = UUID.randomUUID();
        Call call = Call.builder().id(callId).build();
        CallProviderLink link = CallProviderLink.builder().call(call).correlationKey(callId.toString()).build();

        Instant endTime = Instant.now();
        NormalizedCallWebhookEvent event = NormalizedCallWebhookEvent.builder()
            .correlationKey(callId.toString())
            .endedAt(endTime)
            .durationSeconds(45)
            .recordingUrl("http://rec")
            .build();

        when(linkService.findByTenantIdAndCorrelationKeyAndDeletedFalse(tenantId, callId.toString()))
            .thenReturn(Optional.of(link));

        String result = applier.applyCdr(tenantId, event, "sellspark_voice");

        assertThat(result).isEqualTo("PROCESSED");
        verify(callRepository).save(call);
        assertThat(call.getEndTime()).isEqualTo(endTime);
        assertThat(call.getDurationSeconds()).isEqualTo(45);
        assertThat(call.getRecordingUrl()).isEqualTo("http://rec");
        assertThat(call.getStatus()).isEqualTo(Call.CallStatus.HELD);
    }
}
