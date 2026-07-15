package com.shivang.crm.shared.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.shared.exception.BusinessException;

import com.shivang.crm.modules.auth.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EntityResolverService {

      private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;
    private final DealRepository dealRepository;

    /**
     * Validates that an entity exists for the given type and ID within the tenant.
     * 
     * @param entityType The type of entity (LEAD, CONTACT, ACCOUNT, DEAL)
     * @param entityId The UUID of the entity
     * @param tenantId The tenant ID for isolation
     * @throws BusinessException if entity doesn't exist or is invalid
     */
    public void validateEntityExists(String entityType, UUID entityId, UUID tenantId) {
        if (entityType == null || entityId == null) {
            return; // Polymorphic linking is optional
        }

        boolean exists = switch (entityType.toUpperCase()) {
            case "LEAD" -> leadRepository.existsByIdAndTenantId(entityId, tenantId);
            case "CONTACT" -> contactRepository.existsByIdAndTenantId(entityId, tenantId);
            case "ACCOUNT" -> accountRepository.existsByIdAndTenantId(entityId, tenantId);
            case "DEAL" -> dealRepository.existsByIdAndTenantId(entityId, tenantId);
            default -> throw new BusinessException("INVALID_ENTITY_TYPE", 
                "Invalid entity type: " + entityType + ". Supported types: LEAD, CONTACT, ACCOUNT, DEAL");
        };

        if (!exists) {
            throw new BusinessException("ENTITY_NOT_FOUND", 
                entityType + " with ID " + entityId + " not found in tenant " + tenantId);
        }
    }

    /**
     * Resolves the display name for an entity based on its type and ID.
     * 
     * @param entityType The type of entity
     * @param entityId The UUID of the entity
     * @return The display name of the entity, or null if not found
     */
    public String resolveEntityName(String entityType, UUID entityId) {
        if (entityType == null || entityId == null) {
            return null;
        }

        return switch (entityType.toUpperCase()) {
            case "LEAD" -> resolveLeadName(entityId);
            case "CONTACT" -> resolveContactName(entityId);
            case "ACCOUNT" -> resolveAccountName(entityId);
            case "DEAL" -> resolveDealName(entityId);
            default -> null;
        };
    }

    /**
     * Resolves a contact's full name.
     */
    public String resolveContactName(UUID contactId) {
        if (contactId == null) {
            return null;
        }
        
        Optional<Contact> contactOpt = contactRepository.findById(contactId);
        return contactOpt.map(contact -> {
            StringBuilder name = new StringBuilder();
            if (contact.getFirstName() != null) {
                name.append(contact.getFirstName());
            }
            if (contact.getLastName() != null) {
                if (name.length() > 0) {
                    name.append(" ");
                }
                name.append(contact.getLastName());
            }
            return name.length() > 0 ? name.toString() : contact.getEmail();
        }).orElse(null);
    }

    /**
     * Resolves a lead's name.
     */
    public String resolveLeadName(UUID leadId) {
        if (leadId == null) {
            return null;
        }
        
        Optional<Lead> leadOpt = leadRepository.findById(leadId);
        return leadOpt.map(lead -> {
            StringBuilder name = new StringBuilder();
            if (lead.getFirstName() != null) {
                name.append(lead.getFirstName());
            }
            if (lead.getLastName() != null) {
                if (name.length() > 0) {
                    name.append(" ");
                }
                name.append(lead.getLastName());
            }
            return name.length() > 0 ? name.toString() : lead.getCompany();
        }).orElse(null);
    }

    /**
     * Resolves an account's name.
     */
    public String resolveAccountName(UUID accountId) {
        if (accountId == null) {
            return null;
        }
        
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        return accountOpt.map(Account::getName).orElse(null);
    }

    /**
     * Resolves a deal's name.
     */
    public String resolveDealName(UUID dealId) {
        if (dealId == null) {
            return null;
        }
        
        Optional<Deal> dealOpt = dealRepository.findById(dealId);
        return dealOpt.map(Deal::getName).orElse(null);
    }

   /**
    * Resolves a user's full name by ID.
    */
    public String resolveUserName(UUID userId) {
        return userRepository.findById(userId)
            .map(User::getDisplayName)
            .orElse(null);
    }

}
