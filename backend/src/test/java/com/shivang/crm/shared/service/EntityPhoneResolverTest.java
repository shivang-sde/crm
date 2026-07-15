package com.shivang.crm.shared.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.repository.LeadRepository;

class EntityPhoneResolverTest {

    @Test
    void resolvesLeadPhone() {
        LeadRepository leadRepo = mock(LeadRepository.class);
        ContactRepository contactRepo = mock(ContactRepository.class);
        AccountRepository accountRepo = mock(AccountRepository.class);
        DealRepository dealRepo = mock(DealRepository.class);

        UUID tenantId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();

        Lead lead = Lead.builder().id(leadId).phone("+12345").build();
        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));

        EntityPhoneResolver resolver = new EntityPhoneResolver(leadRepo, contactRepo, accountRepo, dealRepo);
        EntityPhoneResolutionResult res = resolver.resolvePhone("LEAD", leadId, tenantId);

        assertThat(res.isFound()).isTrue();
        assertThat(res.getPhone()).isEqualTo("+12345");
    }

    @Test
    void resolvesDealViaContactThenAccount() {
        LeadRepository leadRepo = mock(LeadRepository.class);
        ContactRepository contactRepo = mock(ContactRepository.class);
        AccountRepository accountRepo = mock(AccountRepository.class);
        DealRepository dealRepo = mock(DealRepository.class);

        UUID tenantId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();

        Deal deal = Deal.builder().id(dealId).contactId(contactId).accountId(null).build();
        when(dealRepo.findById(dealId)).thenReturn(Optional.of(deal));

        Contact contact = Contact.builder().id(contactId).phone("+555").build();
        when(contactRepo.findById(contactId)).thenReturn(Optional.of(contact));

        EntityPhoneResolver resolver = new EntityPhoneResolver(leadRepo, contactRepo, accountRepo, dealRepo);
        EntityPhoneResolutionResult res = resolver.resolvePhone("DEAL", dealId, tenantId);

        assertThat(res.isFound()).isTrue();
        assertThat(res.getPhone()).isEqualTo("+555");
        assertThat(res.getResolvedEntityType()).isEqualTo("CONTACT");
    }
}
