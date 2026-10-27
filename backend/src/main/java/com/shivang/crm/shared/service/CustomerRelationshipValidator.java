package com.shivang.crm.shared.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * L3.0-P2: single authority for validating customer-domain relationships.
 *
 * <p>Every check is tenant-scoped <em>and</em> deleted-scoped: the repository's
 * existing foreign keys are tenant-blind, so a known UUID from another tenant
 * would otherwise be accepted. Lookups reuse the existing
 * {@code findByIdAndTenantIdAndDeletedFalse} convention rather than introducing
 * any new tenancy mechanism.
 *
 * <p>This is deliberately a relationship-integrity helper, not an authorization
 * layer. Record-level scope remains the responsibility of RecordScopeGuard.
 */
@Component
@RequiredArgsConstructor
public class CustomerRelationshipValidator {

    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final DealRepository dealRepository;
    private final LeadRepository leadRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    /**
     * Validates that {@code accountId} is a live Account inside {@code tenantId}.
     *
     * @throws BusinessException ACCOUNT_NOT_FOUND when the account is absent,
     *         soft-deleted, or owned by another tenant. The same code is used for
     *         all three cases so the message does not disclose cross-tenant
     *         record existence.
     */
    public void requireLiveAccount(UUID accountId, UUID tenantId) {
        if (accountId == null) {
            return;
        }
        if (accountRepository.findByIdAndTenantIdAndDeletedFalse(accountId, tenantId).isEmpty()) {
            throw new BusinessException("ACCOUNT_NOT_FOUND",
                    "Account not found: " + accountId);
        }
    }

    /**
     * Validates that {@code contactId} is a live Contact inside {@code tenantId}.
     *
     * @throws BusinessException CONTACT_NOT_FOUND
     */
    public void requireLiveContact(UUID contactId, UUID tenantId) {
        if (contactId == null) {
            return;
        }
        if (contactRepository.findByIdAndTenantIdAndDeletedFalse(contactId, tenantId).isEmpty()) {
            throw new BusinessException("CONTACT_NOT_FOUND",
                    "Contact not found: " + contactId);
        }
    }

    /**
     * Validates that {@code leadId} is a live Lead inside {@code tenantId}.
     *
     * @throws BusinessException LEAD_NOT_FOUND
     */
    public void requireLiveLead(UUID leadId, UUID tenantId) {
        if (leadId == null) {
            return;
        }
        if (leadRepository.findByIdAndTenantIdAndDeletedFalse(leadId, tenantId).isEmpty()) {
            throw new BusinessException("LEAD_NOT_FOUND",
                    "Lead not found: " + leadId);
        }
    }

    /**
     * Validates that {@code ownerUserId} is an active user inside {@code tenantId}.
     * Mirrors the existing {@code OfferingService.validateOwner} convention.
     *
     * @throws BusinessException OWNER_NOT_FOUND
     */
    public void requireTenantOwner(UUID ownerUserId, UUID tenantId) {
        if (ownerUserId == null) {
            return;
        }
        User owner = userRepository.findByIdAndTenantIdAndDeletedFalse(ownerUserId, tenantId)
                .orElseThrow(() -> new BusinessException("OWNER_NOT_FOUND",
                        "Owner must be an active user of the same tenant: " + ownerUserId));
        if (Boolean.FALSE.equals(owner.getIsActive())) {
            throw new BusinessException("OWNER_NOT_FOUND",
                    "Owner must be an active user of the same tenant: " + ownerUserId);
        }
    }

    /**
     * Validates that a Deal's contact belongs to the Deal's account.
     *
     * <p>Both invariants from the customer identity model are checked here:
     * the contact is live and tenant-scoped, and it belongs to the account the
     * deal is being associated with. Neither value is auto-corrected: a request
     * that would produce an inconsistent pair is rejected rather than silently
     * nulling the contact or moving it to another account.
     *
     * <p>Skipped when either side is null, because a Deal may legitimately exist
     * without an account or without a contact.
     *
     * @throws BusinessException DEAL_CONTACT_ACCOUNT_MISMATCH
     */
    public void requireContactBelongsToAccount(UUID contactId, UUID accountId, UUID tenantId) {
        if (contactId == null || accountId == null) {
            return;
        }
        UUID contactAccountId = contactRepository
                .findByIdAndTenantIdAndDeletedFalse(contactId, tenantId)
                .orElseThrow(() -> new BusinessException("CONTACT_NOT_FOUND",
                        "Contact not found: " + contactId))
                .getAccountId();

        if (!accountId.equals(contactAccountId)) {
            throw new BusinessException("DEAL_CONTACT_ACCOUNT_MISMATCH",
                    "Contact " + contactId + " does not belong to account " + accountId);
        }
    }

    /**
     * L3.3-P1: validates that a Ticket belongs to the given Account.
     *
     * <p>Same invariant {@link #requireContactBelongsToAccount} enforces for the
     * contact side, applied to the ticket side, and for the same reason: a known
     * ticket id from another account or another tenant would otherwise be accepted by
     * the tenant-blind {@code tickets} foreign key.
     *
     * <p>Added here rather than in a Feedback-local helper so that this remains the
     * one place customer-domain cross-references are validated. Ticket creation does
     * not need this today, because a Ticket <em>owns</em> its account rather than
     * pointing at one; it appears here because L3.3 feedback correlates to a ticket
     * that the customer already owns.
     *
     * <p>Null-tolerant: feedback may legitimately exist without a ticket.
     *
     * @throws BusinessException TICKET_NOT_FOUND, or
     *         TICKET_ACCOUNT_MISMATCH when the ticket belongs to another account
     */
    public void requireTicketBelongsToAccount(UUID ticketId, UUID accountId, UUID tenantId) {
        if (ticketId == null || accountId == null) {
            return;
        }
        UUID ticketAccountId = ticketRepository
                .findByIdAndTenantIdAndDeletedFalse(ticketId, tenantId)
                .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND",
                        "Ticket not found: " + ticketId))
                .getAccountId();

        if (!accountId.equals(ticketAccountId)) {
            throw new BusinessException("TICKET_ACCOUNT_MISMATCH",
                    "Ticket " + ticketId + " does not belong to account " + accountId);
        }
    }
}