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
                return leadRepository.findById(entityId)
                    .map(lead -> EntityPhoneResolutionResult.builder().found(true).phone(lead.getPhone()).resolvedEntityType("LEAD").resolvedEntityId(entityId).build())
                    .orElse(EntityPhoneResolutionResult.builder().found(false).build());
            case "CONTACT":
                return contactRepository.findById(entityId)
                    .map(contact -> EntityPhoneResolutionResult.builder().found(true).phone(contact.getPhone()).resolvedEntityType("CONTACT").resolvedEntityId(entityId).build())
                    .orElse(EntityPhoneResolutionResult.builder().found(false).build());
            case "ACCOUNT":
                return accountRepository.findById(entityId)
                    .map(account -> EntityPhoneResolutionResult.builder().found(true).phone(account.getPhone()).resolvedEntityType("ACCOUNT").resolvedEntityId(entityId).build())
                    .orElse(EntityPhoneResolutionResult.builder().found(false).build());
            case "DEAL":
                return dealRepository.findById(entityId).map(deal -> {
                    // Prefer deal.contactId -> accountId
                    if (deal.getContactId() != null) {
                        Optional.ofNullable(contactRepository.findById(deal.getContactId()))
                            .flatMap(opt -> opt.map(c -> Optional.of(c)).orElse(Optional.empty()));
                        return contactRepository.findById(deal.getContactId())
                            .map(contact -> EntityPhoneResolutionResult.builder().found(true).phone(contact.getPhone()).resolvedEntityType("CONTACT").resolvedEntityId(contact.getId()).build())
                            .orElseGet(() -> accountRepository.findById(deal.getAccountId())
                                .map(acc -> EntityPhoneResolutionResult.builder().found(true).phone(acc.getPhone()).resolvedEntityType("ACCOUNT").resolvedEntityId(acc.getId()).build())
                                .orElse(EntityPhoneResolutionResult.builder().found(false).build()));
                    } else {
                        return accountRepository.findById(deal.getAccountId())
                            .map(acc -> EntityPhoneResolutionResult.builder().found(true).phone(acc.getPhone()).resolvedEntityType("ACCOUNT").resolvedEntityId(acc.getId()).build())
                            .orElse(EntityPhoneResolutionResult.builder().found(false).build());
                    }
                }).orElse(EntityPhoneResolutionResult.builder().found(false).build());
            default:
                return EntityPhoneResolutionResult.builder().found(false).build();
        }
    }
}
