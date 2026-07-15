package com.shivang.crm.modules.integration.service;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.dialer.service.CallProviderLinkService;
import com.shivang.crm.modules.integration.service.impl.CallWebhookMappingApplier;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CallWebhookMappingApplierTest {

    private CallProviderLinkService linkService;
    private CallRepository callRepository;
    private ActivityService activityService;
    private com.shivang.crm.modules.dialer.service.CallOpeningDecisionService decisionService;
    private com.shivang.crm.modules.dialer.service.CallOpeningEventService eventService;
    private CallWebhookMappingApplier applier;

    @BeforeEach
    public void setup() {
        linkService = mock(CallProviderLinkService.class);
        callRepository = mock(CallRepository.class);
        activityService = mock(ActivityService.class);
        decisionService = mock(com.shivang.crm.modules.dialer.service.CallOpeningDecisionService.class);
        eventService = mock(com.shivang.crm.modules.dialer.service.CallOpeningEventService.class);
        applier = new CallWebhookMappingApplier(linkService, callRepository, activityService, decisionService, eventService);
    }

    @Test
    public void applyConnect_updatesCallAndCreatesActivity() {
        Call call = Call.builder().id(UUID.randomUUID()).tenantId(UUID.randomUUID()).build();
        CallProviderLink link = CallProviderLink.builder().call(call).build();
        when(linkService.findByExternalCallId("ext-1")).thenReturn(Optional.of(link));
        when(callRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // decision returns no-op
        when(decisionService.decide(eq(call.getTenantId()), any())).thenReturn(
            new com.shivang.crm.modules.dialer.service.CallOpeningDecisionService.DecisionResult(null, false, null, "no trigger")
        );
        NormalizedCallWebhookEvent evt = NormalizedCallWebhookEvent.builder()
            .externalCallId("ext-1")
            .agentId("agent1")
            .callerNumber("+1")
            .calleeNumber("+2")
            .eventTimestamp(Instant.now())
            .build();

        String res = applier.applyConnect(evt, "sellspark_voice");
        assertEquals("PROCESSED", res);
        verify(callRepository, times(1)).save(any());
        verify(activityService, times(1)).logActivity(eq(call.getTenantId()), eq(call.getId()), eq("CALL"), anyString(), anyString(), isNull(), anyMap());
    }

    @Test
    public void applyCdr_updatesCallAndCreatesActivity() {
        Call call = Call.builder().id(UUID.randomUUID()).tenantId(UUID.randomUUID()).build();
        CallProviderLink link = CallProviderLink.builder().call(call).build();
        when(linkService.findByExternalCallId("ext-2")).thenReturn(Optional.of(link));
        when(callRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NormalizedCallWebhookEvent evt = NormalizedCallWebhookEvent.builder()
            .externalCallId("ext-2")
            .agentId("agent9")
            .durationSeconds(90)
            .providerStatus("completed")
            .recordingUrl("https://r")
            .disposition("left-message")
            .eventTimestamp(Instant.now())
            .build();

        String res = applier.applyCdr(evt, "sellspark_voice");
        assertEquals("PROCESSED", res);
        verify(callRepository, times(1)).save(any());
        verify(activityService, times(1)).logActivity(eq(call.getTenantId()), eq(call.getId()), eq("CALL"), anyString(), anyString(), isNull(), anyMap());
    }

    @Test
    public void unknownExternalCallId_returnsPending() {
        when(linkService.findByExternalCallId("none")).thenReturn(Optional.empty());
        NormalizedCallWebhookEvent evt = NormalizedCallWebhookEvent.builder().externalCallId("none").build();
        String res = applier.applyConnect(evt, "sellspark_voice");
        assertEquals("PENDING_CORRELATION", res);
    }
}
