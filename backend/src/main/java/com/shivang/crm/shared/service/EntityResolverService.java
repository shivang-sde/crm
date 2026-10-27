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
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.shared.exception.BusinessException;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.repository.TicketRepository;

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
    private final CustomerEntitlementRepository entitlementRepository;
    /** L3.1-P1: Support Ticket is a first-class customer-domain entity. */
    private final TicketRepository ticketRepository;

    /**
     * Validates that an entity exists for the given type and ID within the tenant.
     *
     * <p>L3.0-P2: every branch is now deleted-aware as well as tenant-scoped, so
     * a soft-deleted record is never treated as a live polymorphic link target.
     *
     * @param entityType The type of entity (LEAD, CONTACT, ACCOUNT, DEAL, ENTITLEMENT, TICKET)
     * @param entityId The UUID of the entity
     * @param tenantId The tenant ID for isolation
     * @throws BusinessException if entity doesn't exist or is invalid
     */
    public void validateEntityExists(String entityType, UUID entityId, UUID tenantId) {
        if (entityType == null || entityId == null) {
            return; // Polymorphic linking is optional
        }

        boolean exists = switch (entityType.toUpperCase()) {
            case "LEAD" -> leadRepository.existsByIdAndTenantIdAndDeletedFalse(entityId, tenantId);
            case "CONTACT" -> contactRepository.existsByIdAndTenantIdAndDeletedFalse(entityId, tenantId);
            case "ACCOUNT" -> accountRepository.existsByIdAndTenantIdAndDeletedFalse(entityId, tenantId);
            case "DEAL" -> dealRepository.existsByIdAndTenantIdAndDeletedFalse(entityId, tenantId);
            case "ENTITLEMENT" -> entitlementRepository.existsByIdAndTenantIdAndDeletedFalse(entityId, tenantId);
            case "TICKET" -> ticketRepository.existsByIdAndTenantIdAndDeletedFalse(entityId, tenantId);
            default -> throw new BusinessException("INVALID_ENTITY_TYPE",
                "Invalid entity type: " + entityType + ". Supported types: LEAD, CONTACT, ACCOUNT, DEAL, ENTITLEMENT, TICKET");
        };

        if (!exists) {
            throw new BusinessException("ENTITY_NOT_FOUND",
                entityType + " with ID " + entityId + " not found in tenant " + tenantId);
        }
    }

    /**
     * Resolves the display name for an entity based on its type and ID.
     *
     * <p>L3.0-P2: {@code tenantId} is now required. Customer-domain resolution
     * previously used the unscoped {@code findById}, which could return the name
     * or email of another tenant's record.
     *
     * @param entityType The type of entity
     * @param entityId The UUID of the entity
     * @param tenantId The tenant that owns the resolution
     * @return The display name of the entity, or null if not found
     */
    public String resolveEntityName(String entityType, UUID entityId, UUID tenantId) {
        if (entityType == null || entityId == null) {
            return null;
        }

        return switch (entityType.toUpperCase()) {
            case "LEAD" -> resolveLeadName(tenantId, entityId);
            case "CONTACT" -> resolveContactName(tenantId, entityId);
            case "ACCOUNT" -> resolveAccountName(tenantId, entityId);
            case "DEAL" -> resolveDealName(tenantId, entityId);
            case "ENTITLEMENT" -> resolveEntitlementName(tenantId, entityId);
            case "TICKET" -> resolveTicketName(tenantId, entityId);
            default -> null;
        };
    }

    /**
     * Resolves a contact's full name.
     */
    public String resolveContactName(UUID tenantId, UUID contactId) {
        if (contactId == null) {
            return null;
        }

        Optional<Contact> contactOpt = contactRepository.findByIdAndTenantIdAndDeletedFalse(contactId, tenantId);
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
    public String resolveLeadName(UUID tenantId, UUID leadId) {
        if (leadId == null) {
            return null;
        }

        Optional<Lead> leadOpt = leadRepository.findByIdAndTenantIdAndDeletedFalse(leadId, tenantId);
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
    public String resolveAccountName(UUID tenantId, UUID accountId) {
        if (accountId == null) {
            return null;
        }

        Optional<Account> accountOpt = accountRepository.findByIdAndTenantIdAndDeletedFalse(accountId, tenantId);
        return accountOpt.map(Account::getName).orElse(null);
    }

    /**
     * Resolves a deal's name.
     */
    public String resolveDealName(UUID tenantId, UUID dealId) {
        if (dealId == null) {
            return null;
        }

        Optional<Deal> dealOpt = dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId);
        return dealOpt.map(Deal::getName).orElse(null);
    }

    /**
     * Resolves an entitlement's name.
     */
    public String resolveEntitlementName(UUID tenantId, UUID entitlementId) {
        if (entitlementId == null) {
            return null;
        }

        Optional<CustomerEntitlement> entitlementOpt =
                entitlementRepository.findByIdAndTenantIdAndDeletedFalse(entitlementId, tenantId);
        return entitlementOpt.map(CustomerEntitlement::getName).orElse(null);
    }

    /**
     * L3.1-P1: resolves a support ticket's display label. The subject is the
     * customer-facing summary, so it is the sensible timeline label.
     */
    public String resolveTicketName(UUID tenantId, UUID ticketId) {
        if (ticketId == null) {
            return null;
        }

        return ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId)
                .map(Ticket::getSubject)
                .orElse(null);
    }

   /**
     * Resolves a user''s full name by ID.
     *
     * <p>Not customer-domain: this is a display helper for assignee/owner labels,
     * and the owner of a customer record is already tenant-validated on write.
     */
    public String resolveUserName(UUID userId) {
        return userRepository.findById(userId)
            .map(User::getDisplayName)
            .orElse(null);
    }

}