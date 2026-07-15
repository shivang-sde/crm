package com.shivang.crm.modules.dialer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.dialer.service.impl.DefaultCallEntityResolutionService;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CallEntityResolutionServiceTest {

    private CallRepository callRepo;
    private LeadRepository leadRepo;
    private ContactRepository contactRepo;
    private AccountRepository accountRepo;
    private DefaultCallEntityResolutionService svc;

    @BeforeEach
    public void setup() {
        callRepo = mock(CallRepository.class);
        leadRepo = mock(LeadRepository.class);
        contactRepo = mock(ContactRepository.class);
        accountRepo = mock(AccountRepository.class);
        svc = new DefaultCallEntityResolutionService(callRepo, leadRepo, contactRepo, accountRepo);
    }

    @Test
    public void resolvesExistingCallLink() {
        UUID tid = UUID.randomUUID();
        Call c = Call.builder().id(UUID.randomUUID()).build();
        CallProviderLink link = CallProviderLink.builder().call(c).build();
        com.shivang.crm.modules.dialer.entity.CallConnectTrigger trigger = com.shivang.crm.modules.dialer.entity.CallConnectTrigger.builder().entityResolveBy("existing_call_link").build();
        when(callRepo.findById(c.getId())).thenReturn(Optional.of(c));
        var res = svc.resolveByTrigger(tid, NormalizedCallWebhookEvent.builder().build(), link, trigger);
        assertTrue(res.resolved());
        assertEquals("call", res.entityType());
    }

    @Test
    public void resolvesByCallerToLead() {
        UUID tid = UUID.randomUUID();
        com.shivang.crm.modules.dialer.entity.CallConnectTrigger trigger = com.shivang.crm.modules.dialer.entity.CallConnectTrigger.builder().entityResolveBy("caller_number").build();
        NormalizedCallWebhookEvent evt = NormalizedCallWebhookEvent.builder().callerNumber("+1 234-567-8900").build();
        Lead l = Lead.builder().id(UUID.randomUUID()).build();
        when(leadRepo.findActiveLeadByPhoneAndTenant("+12345678900", tid)).thenReturn(Optional.of(l));
        var res = svc.resolveByTrigger(tid, evt, null, trigger);
        assertTrue(res.resolved());
        assertEquals("lead", res.entityType());
    }

    @Test
    public void resolvesByCallerToContact() {
        UUID tid = UUID.randomUUID();
        com.shivang.crm.modules.dialer.entity.CallConnectTrigger trigger = com.shivang.crm.modules.dialer.entity.CallConnectTrigger.builder().entityResolveBy("caller_number").build();
        NormalizedCallWebhookEvent evt = NormalizedCallWebhookEvent.builder().callerNumber("(123) 456 7890").build();
        Contact c = Contact.builder().id(UUID.randomUUID()).build();
        when(contactRepo.findByTenantIdAndPhoneAndDeletedFalse(tid, "1234567890")).thenReturn(Optional.of(c));
        var res = svc.resolveByTrigger(tid, evt, null, trigger);
        assertTrue(res.resolved());
        assertEquals("contact", res.entityType());
    }

    @Test
    public void unknownNumberReturnsUnresolved() {
        UUID tid = UUID.randomUUID();
        com.shivang.crm.modules.dialer.entity.CallConnectTrigger trigger = com.shivang.crm.modules.dialer.entity.CallConnectTrigger.builder().entityResolveBy("caller_number").build();
        NormalizedCallWebhookEvent evt = NormalizedCallWebhookEvent.builder().callerNumber("+999").build();
        when(contactRepo.findByTenantIdAndPhoneAndDeletedFalse(any(), any())).thenReturn(Optional.empty());
        when(leadRepo.findActiveLeadByPhoneAndTenant(any(), any())).thenReturn(Optional.empty());
        when(accountRepo.findByTenantIdAndPhoneAndDeletedFalse(any(), any())).thenReturn(Optional.empty());
        var res = svc.resolveByTrigger(tid, evt, null, trigger);
        assertFalse(res.resolved());
    }

    @Test
    public void phoneNormalizationPreservesPlus() {
        String in = "+91 (987) 654-3210";
        String expected = "+919876543210";
        // use reflection to call normalizePhone
        try {
            var m = DefaultCallEntityResolutionService.class.getDeclaredMethod("normalizePhone", String.class);
            m.setAccessible(true);
            String out = (String) m.invoke(svc, in);
            assertEquals(expected, out);
        } catch (Exception ex) {
            fail("reflection failed");
        }
    }
}
