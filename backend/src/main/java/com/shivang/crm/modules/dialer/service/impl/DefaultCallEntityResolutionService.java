package com.shivang.crm.modules.dialer.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.dialer.entity.CallConnectTrigger;
import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.dialer.service.CallEntityResolutionService;
import com.shivang.crm.modules.dialer.service.CallProviderLinkService;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;
import com.shivang.crm.modules.lead.repository.LeadRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultCallEntityResolutionService implements CallEntityResolutionService {

    private final CallRepository callRepository;
    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;
    private final CallProviderLinkService callProviderLinkService;

    @Override
    public ResolutionResult resolveByTrigger(
            UUID tenantId,
            NormalizedCallWebhookEvent event,
            CallProviderLink link,
            CallConnectTrigger trigger) {

        if (tenantId == null) {
            return unresolved("Tenant id is required");
        }

        if (event == null) {
            return unresolved("Webhook event is required");
        }

        if (trigger == null) {
            return unresolved("Call connect trigger is required");
        }

        String resolveBy = normalizeResolveBy(trigger.getEntityResolveBy());

        if (resolveBy == null) {
            return unresolved("Trigger has no entity resolution rule");
        }

        return switch (resolveBy) {
            case "existing_call_link" ->
                    resolveByExistingCallLink(tenantId, link);

            case "external_call_id" ->
                    resolveByExternalCallId(tenantId, event, link);

            case "caller_number" ->
                    resolveByPhone(
                            tenantId,
                            event.getCallerNumber(),
                            "caller number"
                    );

            case "callee_number" ->
                    resolveByPhone(
                            tenantId,
                            event.getCalleeNumber(),
                            "callee number"
                    );

            case "agent_id" ->
                    resolveByAgentId(event);

            default ->
                    unresolved(
                            "Unsupported entity resolution rule: " + resolveBy
                    );
        };
    }

    private ResolutionResult resolveByExistingCallLink(
            UUID tenantId,
            CallProviderLink link) {

        if (link == null || link.getCall() == null) {
            return unresolved("No existing call link found");
        }

        UUID callId = link.getCall().getId();

        if (callId == null) {
            return unresolved("Existing call link has no call id");
        }

        Call call = callRepository
                .findByIdAndTenantIdAndDeletedFalse(callId, tenantId)
                .orElse(null);

        if (call == null) {
            return unresolved(
                    "Linked call was not found for the current tenant"
            );
        }

        return resolved(
                "call",
                call.getId(),
                "Resolved via existing call link"
        );
    }

    private ResolutionResult resolveByExternalCallId(
            UUID tenantId,
            NormalizedCallWebhookEvent event,
            CallProviderLink suppliedLink) {

        String externalCallId = trimToNull(event.getExternalCallId());

        if (externalCallId == null) {
            return unresolved("No external call id was provided");
        }

        /*
         * The applier may already have resolved the provider link.
         * Reuse it when it belongs to the same external call.
         */
        CallProviderLink resolvedLink = suppliedLink;

        if (resolvedLink == null
                || resolvedLink.getCall() == null
                || !externalCallId.equals(resolvedLink.getExternalCallId())) {

            resolvedLink = callProviderLinkService
                    .findByTenantIdAndExternalCallIdAndDeletedFalse(
                            tenantId,
                            externalCallId
                    )
                    .orElse(null);
        }

        if (resolvedLink == null || resolvedLink.getCall() == null) {
            return unresolved(
                    "No call provider link matched the external call id"
            );
        }

        UUID callId = resolvedLink.getCall().getId();

        if (callId == null) {
            return unresolved(
                    "Resolved provider link has no CRM call id"
            );
        }

        Call call = callRepository
                .findByIdAndTenantIdAndDeletedFalse(callId, tenantId)
                .orElse(null);

        if (call == null) {
            return unresolved(
                    "CRM call matched by external call id was not found for the current tenant"
            );
        }

        return resolved(
                "call",
                call.getId(),
                "Resolved via external call id"
        );
    }

    private ResolutionResult resolveByPhone(
            UUID tenantId,
            String rawPhone,
            String sourceDescription) {

        String normalizedPhone = normalizePhone(rawPhone);

        if (normalizedPhone == null) {
            return unresolved(
                    "No " + sourceDescription + " was provided"
            );
        }

        /*
         * Priority:
         * 1. Contact
         * 2. Lead
         * 3. Account
         */
        var contact = contactRepository
                .findByTenantIdAndPhoneAndDeletedFalse(
                        tenantId,
                        normalizedPhone
                );

        if (contact.isPresent()) {
            return resolved(
                    "contact",
                    contact.get().getId(),
                    "Matched contact by " + sourceDescription
            );
        }

        var lead = leadRepository
                .findActiveLeadByPhoneAndTenant(
                        normalizedPhone,
                        tenantId
                );

        if (lead.isPresent()) {
            return resolved(
                    "lead",
                    lead.get().getId(),
                    "Matched lead by " + sourceDescription
            );
        }

        var account = accountRepository
                .findByTenantIdAndPhoneAndDeletedFalse(
                        tenantId,
                        normalizedPhone
                );

        if (account.isPresent()) {
            return resolved(
                    "account",
                    account.get().getId(),
                    "Matched account by " + sourceDescription
            );
        }

        return unresolved(
                "No contact, lead, or account matched " + sourceDescription
        );
    }

    private ResolutionResult resolveByAgentId(
            NormalizedCallWebhookEvent event) {

        String agentId = trimToNull(event.getAgentId());

        if (agentId == null) {
            return unresolved("No provider agent id was provided");
        }

        /*
         * You currently do not have a provider-agent-to-CRM-user mapping table.
         * Keep this unresolved instead of incorrectly resolving the agent as an entity.
         */
        return unresolved(
                "Agent resolution is not implemented for provider agent id: "
                        + agentId
        );
    }

    private String normalizeResolveBy(String value) {
        String normalized = trimToNull(value);

        if (normalized == null) {
            return null;
        }

        return normalized.toLowerCase();
    }

    /**
     * Normalizes common Indian phone-number formats:
     *
     * +91 87895 68736 -> 8789568736
     * 918789568736    -> 8789568736
     * 08789568736     -> 8789568736
     * 87895-68736     -> 9555969516
     */
    private String normalizePhone(String phone) {
        String normalized = trimToNull(phone);

        if (normalized == null) {
            return null;
        }

        normalized = normalized.replaceAll("\\D", "");

        if (normalized.length() == 12
                && normalized.startsWith("91")) {
            normalized = normalized.substring(2);
        }

        if (normalized.length() == 11
                && normalized.startsWith("0")) {
            normalized = normalized.substring(1);
        }

        return normalized.isBlank() ? null : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResolutionResult resolved(
            String entityType,
            UUID entityId,
            String reason) {

        return new ResolutionResult(
                true,
                entityType,
                entityId,
                reason
        );
    }

    private ResolutionResult unresolved(String reason) {
        return new ResolutionResult(
                false,
                null,
                null,
                reason
        );
    }
}