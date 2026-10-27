package com.shivang.crm.modules.feedback.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.3-P1: resolves an external customer's email address to internal CRM identity.
 *
 * <p>This is the single place that turns a customer-supplied string into an Account,
 * and it is deliberately not a general-purpose resolver: it does not fall back, and
 * it never creates master data.
 *
 * <h2>Why Contact is consulted before Account</h2>
 *
 * <p>The asymmetry is a property of the schema, not a preference:
 * <ul>
 *   <li>{@code contacts} has
 *       {@code uq_contact_email ON contacts(tenant_id, lower(email)) WHERE deleted = false}
 *       (V9), so an email matches <b>at most one</b> active Contact per tenant. The
 *       lookup returns {@code Optional} and is deterministic.</li>
 *   <li>{@code accounts.email} has <b>no unique constraint and no index at all</b>.
 *       Several accounts in one tenant may legitimately share an email address, so
 *       an Account match can be ambiguous.</li>
 * </ul>
 *
 * <p>So a Contact match is both more specific (it also yields the Account, and
 * therefore the person who was actually asked) and more reliable. An Account-only
 * match is the fallback for feedback given about a company rather than a person.
 *
 * <h2>Ambiguity is rejected, not resolved arbitrarily</h2>
 *
 * <p>If several active Accounts share the submitted email there is no repository
 * evidence for which one the customer meant. Guessing would attach feedback — and
 * with it an NPS score that will later drive retention decisions — to an arbitrary
 * company. The submission is rejected with a conflict instead, and the operator
 * resolves it by capturing the feedback against a specific customer internally.
 *
 * <p>Soft-deleted records never match: both finders are
 * {@code ...AndDeletedFalse}, mirroring {@code requireLiveAccount}. Note {@code
 * is_active} is deliberately not consulted — no other identity lookup in this
 * codebase filters on it, and an inactive account is still a real customer whose
 * feedback matters.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeedbackIdentityResolver {

    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;

    /**
     * The resolved customer for one submitted email.
     *
     * @param contactId the Contact, when the email matched one; otherwise null
     * @param accountId always non-null
     */
    public record ResolvedCustomer(UUID accountId, UUID contactId) {
    }

    /**
     * @throws BusinessException {@code FEEDBACK_CUSTOMER_NOT_FOUND} when the email
     *         matches no active Account or Contact in this tenant, or
     *         {@code FEEDBACK_CUSTOMER_AMBIGUOUS} when it matches several Accounts
     */
    @Transactional(readOnly = true)
    public ResolvedCustomer resolve(UUID tenantId, String rawEmail) {
        String email = rawEmail == null ? null : rawEmail.trim();
        if (email == null || email.isEmpty()) {
            throw new BusinessException("FEEDBACK_CUSTOMER_NOT_FOUND",
                    "No Account or Contact matches the submitted email address");
        }

        // 1. Contact first: unique per tenant, and it carries its own Account.
        Contact contact = contactRepository
                .findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, email)
                .orElse(null);
        if (contact != null) {
            log.debug("Resolved feedback email to contact {} on account {}", contact.getId(), contact.getAccountId());
            return new ResolvedCustomer(contact.getAccountId(), contact.getId());
        }

        // 2. Fall back to an Account-level match. May legitimately be several.
        List<Account> accounts =
                accountRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, email);

        if (accounts.isEmpty()) {
            throw new BusinessException("FEEDBACK_CUSTOMER_NOT_FOUND",
                    "No Account or Contact matches the submitted email address");
        }
        if (accounts.size() > 1) {
            log.warn("Feedback email matched {} active accounts in tenant {}; rejecting as ambiguous",
                    accounts.size(), tenantId);
            throw new BusinessException("FEEDBACK_CUSTOMER_AMBIGUOUS",
                    "The submitted email address matches more than one customer; "
                            + "submit the feedback against the specific customer instead");
        }

        Account account = accounts.get(0);
        log.debug("Resolved feedback email to account {} (no matching contact)", account.getId());
        return new ResolvedCustomer(account.getId(), null);
    }
}
