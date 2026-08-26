package com.shivang.crm.shared.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.lead.repository.LeadRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntityPhoneResolver {

    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;
    private final DealRepository dealRepository;

    public EntityPhoneResolutionResult resolvePhone(String entityType, UUID entityId, UUID tenantId) {
        if (entityType == null || entityId == null) {
            return EntityPhoneResolutionResult.builder().found(false).build();
        }

        switch (entityType.toUpperCase()) {
            case "LEAD":
                return leadRepository.findByIdAndTenantId(entityId, tenantId)
                    .map(lead -> EntityPhoneResolutionResult.builder().found(true).phone(lead.getPhone()).resolvedEntityType("LEAD").resolvedEntityId(entityId).build())
                    .orElse(EntityPhoneResolutionResult.builder().found(false).build());
            case "CONTACT":
                return contactRepository.findByIdAndTenantId(entityId, tenantId)
                    .map(contact -> EntityPhoneResolutionResult.builder().found(true).phone(contact.getPhone()).resolvedEntityType("CONTACT").resolvedEntityId(entityId).build())
                    .orElse(EntityPhoneResolutionResult.builder().found(false).build());
            case "ACCOUNT":
                return accountRepository.findByIdAndTenantId(entityId, tenantId)
                    .map(account -> EntityPhoneResolutionResult.builder().found(true).phone(account.getPhone()).resolvedEntityType("ACCOUNT").resolvedEntityId(entityId).build())
                    .orElse(EntityPhoneResolutionResult.builder().found(false).build());
            case "DEAL":
                return dealRepository.findByIdAndTenantId(entityId, tenantId).map(deal -> {
                    // Prefer deal.contactId -> accountId; every lookup stays
                    // tenant-scoped so another tenant's record is never read.
                    if (deal.getContactId() != null) {
                        return contactRepository.findByIdAndTenantId(deal.getContactId(), tenantId)
                            .map(contact -> EntityPhoneResolutionResult.builder().found(true).phone(contact.getPhone()).resolvedEntityType("CONTACT").resolvedEntityId(contact.getId()).build())
                            .orElseGet(() -> resolveAccountPhone(tenantId, deal.getAccountId()));
                    } else {
                        return resolveAccountPhone(tenantId, deal.getAccountId());
                    }
                }).orElse(EntityPhoneResolutionResult.builder().found(false).build());
            default:
                return EntityPhoneResolutionResult.builder().found(false).build();
        }
    }

    private EntityPhoneResolutionResult resolveAccountPhone(UUID tenantId, UUID accountId) {
        if (accountId == null) {
            return EntityPhoneResolutionResult.builder().found(false).build();
        }
        return accountRepository.findByIdAndTenantId(accountId, tenantId)
            .map(acc -> EntityPhoneResolutionResult.builder().found(true).phone(acc.getPhone()).resolvedEntityType("ACCOUNT").resolvedEntityId(acc.getId()).build())
            .orElse(EntityPhoneResolutionResult.builder().found(false).build());
    }
}
