package com.shivang.crm.modules.dialer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import com.shivang.crm.modules.dialer.entity.CallConnectTrigger;
import com.shivang.crm.modules.dialer.repository.CallConnectTriggerRepository;
import com.shivang.crm.modules.dialer.service.impl.DefaultCallConnectTriggerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CallConnectTriggerServiceTest {

    private CallConnectTriggerRepository repo;
    private DefaultCallConnectTriggerService svc;

    @BeforeEach
    public void setup() {
        repo = mock(CallConnectTriggerRepository.class);
        svc = new DefaultCallConnectTriggerService(repo);
    }

    @Test
    public void bothDirectionMatchesBoth() {
        UUID tid = UUID.randomUUID();
        CallConnectTrigger t = CallConnectTrigger.builder().tenantId(tid).callDirection("BOTH").isActive(true).priority(10).build();
        when(repo.findByTenantId(tid)).thenReturn(List.of(t));
        var res = svc.findActiveByTenantAndDirection(tid, "INBOUND");
        assertEquals(1, res.size());
    }

    @Test
    public void inboundMatchesInbound() {
        UUID tid = UUID.randomUUID();
        CallConnectTrigger t = CallConnectTrigger.builder().tenantId(tid).callDirection("INBOUND").isActive(true).priority(1).build();
        when(repo.findByTenantId(tid)).thenReturn(List.of(t));
        var res = svc.findActiveByTenantAndDirection(tid, "INBOUND");
        assertEquals(1, res.size());
    }

    @Test
    public void outboundIgnoredForInbound() {
        UUID tid = UUID.randomUUID();
        CallConnectTrigger t = CallConnectTrigger.builder().tenantId(tid).callDirection("OUTBOUND").isActive(true).priority(1).build();
        when(repo.findByTenantId(tid)).thenReturn(List.of(t));
        var res = svc.findActiveByTenantAndDirection(tid, "INBOUND");
        assertEquals(0, res.size());
    }

    @Test
    public void inactiveIgnored() {
        UUID tid = UUID.randomUUID();
        CallConnectTrigger t = CallConnectTrigger.builder().tenantId(tid).callDirection("BOTH").isActive(false).priority(1).build();
        when(repo.findByTenantId(tid)).thenReturn(List.of(t));
        var res = svc.findActiveByTenantAndDirection(tid, "INBOUND");
        assertEquals(0, res.size());
    }

    @Test
    public void priorityOrdering() {
        UUID tid = UUID.randomUUID();
        CallConnectTrigger t1 = CallConnectTrigger.builder().tenantId(tid).callDirection("BOTH").isActive(true).priority(5).build();
        CallConnectTrigger t2 = CallConnectTrigger.builder().tenantId(tid).callDirection("BOTH").isActive(true).priority(1).build();
        when(repo.findByTenantId(tid)).thenReturn(List.of(t1, t2));
        var res = svc.findActiveByTenantAndDirection(tid, "INBOUND");
        assertEquals(2, res.size());
        assertEquals(t2.getPriority(), res.get(0).getPriority());
    }

}
