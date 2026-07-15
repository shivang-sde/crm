package com.shivang.crm.modules.dialer.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.dialer.service.CallEntityResolutionService;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultCallEntityResolutionService implements CallEntityResolutionService {

    private final CallRepository callRepository;
    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;

    private String normalizePhone(String p) {
        if (p == null) return null;
        String s = p.trim().replaceAll("[\\s().-]+", "");
        return s;
    }

    @Override
    public ResolutionResult resolveByTrigger(UUID tenantId, NormalizedCallWebhookEvent event, CallProviderLink link, com.shivang.crm.modules.dialer.entity.CallConnectTrigger trigger) {
        // 1. existing_call_link
        if (trigger.getEntityResolveBy() != null && "existing_call_link".equals(trigger.getEntityResolveBy())) {
            if (link != null && link.getCall() != null) {
                Call c = callRepository.findById(link.getCall().getId()).orElse(null);
                if (c != null) return new ResolutionResult(true, "call", c.getId(), "Resolved via existing call link");
            }
            return new ResolutionResult(false, null, null, "No call link found");
        }

        // 2. external_call_id
        if (trigger.getEntityResolveBy() != null && "external_call_id".equals(trigger.getEntityResolveBy())) {
            if (event.getExternalCallId() != null) {
                var opl = Optional.ofNullable(link);
                if (opl.isPresent() && opl.get().getCall() != null) {
                    var c = callRepository.findById(opl.get().getCall().getId()).orElse(null);
                    if (c != null) return new ResolutionResult(true, "call", c.getId(), "Resolved via external_call_id");
                }
            }
            return new ResolutionResult(false, null, null, "external_call_id not resolved");
        }

        // caller_number / callee_number
        String phone = null;
        if ("caller_number".equals(trigger.getEntityResolveBy())) phone = event.getCallerNumber();
        if ("callee_number".equals(trigger.getEntityResolveBy())) phone = event.getCalleeNumber();

        if (phone != null) {
            String norm = normalizePhone(phone);
            // contact first
            var contact = contactRepository.findByTenantIdAndPhoneAndDeletedFalse(tenantId, norm);
            if (contact.isPresent()) return new ResolutionResult(true, "contact", contact.get().getId(), "Matched contact by phone");
            var lead = leadRepository.findActiveLeadByPhoneAndTenant(norm, tenantId);
            if (lead.isPresent()) return new ResolutionResult(true, "lead", lead.get().getId(), "Matched lead by phone");
            var acct = accountRepository.findByTenantIdAndPhoneAndDeletedFalse(tenantId, norm);
            if (acct.isPresent()) return new ResolutionResult(true, "account", acct.get().getId(), "Matched account by phone");
            return new ResolutionResult(false, null, null, "No entity matched phone");
        }

        // agent_id
        if ("agent_id".equals(trigger.getEntityResolveBy())) {
            if (event.getAgentId() != null) return new ResolutionResult(false, null, null, "Agent resolution not implemented");
            return new ResolutionResult(false, null, null, "No agent id provided");
        }

        return new ResolutionResult(false, null, null, "No resolution rule matched");
    }
}
