package com.shivang.crm.modules.dialer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.dialer.dto.CallOpeningInstruction;
import com.shivang.crm.modules.dialer.entity.CallConnectTrigger;
import com.shivang.crm.modules.dialer.service.impl.DefaultCallOpeningDecisionService;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CallOpeningDecisionServiceTest {

    private CallConnectTriggerService triggerService;
    private CallEntityResolutionService resolver;
    private DefaultCallOpeningDecisionService svc;

    @BeforeEach
    public void setup() {
        triggerService = mock(CallConnectTriggerService.class);
        resolver = mock(CallEntityResolutionService.class);
        svc = new DefaultCallOpeningDecisionService(triggerService, resolver);
    }

    @Test
    public void selectsTriggerAndBuildsInstruction() {
        UUID tid = UUID.randomUUID();
        CallConnectTrigger t = CallConnectTrigger.builder().triggerKey("t1").openActionType("OPEN_PAGE").callDirection("INBOUND").isActive(true).targetRoute("/leads/{id}").config(java.util.Map.of("layoutId", "layout1")).build();
        when(triggerService.findActiveByTenantAndDirection(eq(tid), any())).thenReturn(java.util.List.of(t));
        when(resolver.resolveByTrigger(eq(tid), any(), any(), eq(t))).thenReturn(new CallEntityResolutionService.ResolutionResult(true, "lead", UUID.randomUUID(), "matched"));
        var dec = svc.decide(tid, NormalizedCallWebhookEvent.builder().externalCallId("ext").build());
        assertTrue(dec.shouldOpen());
        CallOpeningInstruction instr = dec.instruction();
        assertEquals("OPEN_PAGE", instr.getActionType());
        assertEquals("lead", instr.getEntityType());
        assertNotNull(instr.getRoute());
        assertTrue(instr.getResolved());
    }

    @Test
    public void noTriggersReturnsFallback() {
        UUID tid = UUID.randomUUID();
        when(triggerService.findActiveByTenantAndDirection(eq(tid), any())).thenReturn(java.util.List.of());
        var dec = svc.decide(tid, NormalizedCallWebhookEvent.builder().externalCallId("ext").build());
        assertFalse(dec.shouldOpen());
        assertEquals("NONE", dec.instruction().getActionType());
    }
}
