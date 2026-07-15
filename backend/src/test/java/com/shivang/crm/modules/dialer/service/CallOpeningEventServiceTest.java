package com.shivang.crm.modules.dialer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.dialer.dto.CallOpeningInstruction;
import com.shivang.crm.modules.dialer.entity.CallOpeningEvent;
import com.shivang.crm.modules.dialer.repository.CallOpeningEventRepository;
import com.shivang.crm.modules.dialer.service.impl.DefaultCallOpeningEventService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CallOpeningEventServiceTest {

    private CallOpeningEventRepository repo;
    private DefaultCallOpeningEventService svc;

    @BeforeEach
    public void setup() {
        repo = mock(CallOpeningEventRepository.class);
        svc = new DefaultCallOpeningEventService(repo);
    }

    @Test
    public void createPendingEvent() {
        UUID tid = UUID.randomUUID();
        CallOpeningInstruction instr = CallOpeningInstruction.builder().actionType("OPEN_PAGE").build();
        CallOpeningEvent e = CallOpeningEvent.builder().tenantId(tid).deliveryStatus("PENDING").createdAt(Instant.now()).build();
        when(repo.save(any())).thenReturn(e);
        var res = svc.createEvent(tid, null, "agent1", null, "ext", "prov", "t1", instr);
        assertEquals("PENDING", res.getDeliveryStatus());
    }

    @Test
    public void findPendingForTenant() {
        UUID tid = UUID.randomUUID();
        when(repo.findByTenantIdAndDeliveryStatus(tid, "PENDING")).thenReturn(List.of(new CallOpeningEvent()));
        var res = svc.findPendingForTenant(tid);
        assertEquals(1, res.size());
    }

    @Test
    public void markDelivered() {
        UUID id = UUID.randomUUID();
        CallOpeningEvent e = CallOpeningEvent.builder().id(id).deliveryStatus("PENDING").build();
        when(repo.findById(id)).thenReturn(Optional.of(e));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        svc.markDelivered(id);
        verify(repo, times(1)).save(any());
    }
}
