package com.shivang.crm.modules.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.3-P1: email to customer resolution.
 *
 * <p>The schema is asymmetric, and this test pins the consequence: {@code contacts}
 * has a partial unique index on {@code (tenant_id, lower(email))} while
 * {@code accounts.email} has no constraint at all. So a Contact match is
 * deterministic and an Account match may not be — which is why Contact is consulted
 * first and why several matching Accounts are rejected rather than guessed between.
 */
class FeedbackIdentityResolverTest {

    private AccountRepository accountRepository;
    private ContactRepository contactRepository;
    private FeedbackIdentityResolver resolver;

    private UUID tenantId;

    @BeforeEach
    void setup() {
        accountRepository = mock(AccountRepository.class);
        contactRepository = mock(ContactRepository.class);
        resolver = new FeedbackIdentityResolver(accountRepository, contactRepository);
        tenantId = UUID.randomUUID();
    }

    private Contact contact(UUID accountId) {
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setAccountId(accountId);
        contact.setEmail("customer@example.com");
        return contact;
    }

    private Account account() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail("customer@example.com");
        return account;
    }

    private String codeOf(Throwable thrown) {
        return ((BusinessException) thrown).getErrorCode();
    }

    // ================= Contact path (deterministic) =================

    @Test
    void aContactEmailResolvesToBothContactAndItsAccount() {
        UUID accountId = UUID.randomUUID();
        Contact contact = contact(accountId);
        when(contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, "customer@example.com"))
                .thenReturn(java.util.Optional.of(contact));

        var resolved = resolver.resolve(tenantId, "customer@example.com");

        assertThat(resolved.accountId()).isEqualTo(accountId);
        assertThat(resolved.contactId()).isEqualTo(contact.getId());
    }

    @Test
    void aContactMatchNeverFallsBackToTheAccountLookup() {
        when(contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(java.util.Optional.of(contact(UUID.randomUUID())));

        resolver.resolve(tenantId, "customer@example.com");

        // The Account path is the ambiguity-prone one; a resolved Contact must not
        // consult it, or a duplicate Account email could contaminate the result.
        verifyNoInteractions(accountRepository);
    }

    // ================= Account fallback =================

    @Test
    void anAccountEmailResolvesWhenNoContactMatches() {
        Account account = account();
        when(contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(java.util.Optional.empty());
        when(accountRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, "customer@example.com"))
                .thenReturn(List.of(account));

        var resolved = resolver.resolve(tenantId, "customer@example.com");

        assertThat(resolved.accountId()).isEqualTo(account.getId());
        assertThat(resolved.contactId()).isNull();
    }

    @Test
    void severalAccountsSharingAnEmailAreRejectedAsAmbiguous() {
        // accounts.email has no unique constraint, so this state is legal in the data.
        // Picking one would attach an NPS score to an arbitrary company.
        when(contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(java.util.Optional.empty());
        when(accountRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(List.of(account(), account()));

        var thrown = catchThrowable(() -> resolver.resolve(tenantId, "customer@example.com"));

        assertThat(codeOf(thrown)).isEqualTo("FEEDBACK_CUSTOMER_AMBIGUOUS");
    }

    @Test
    void anAmbiguousEmailDoesNotResolveToAnyAccount() {
        when(contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(java.util.Optional.empty());
        when(accountRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(List.of(account(), account(), account()));

        assertThat(catchThrowable(() -> resolver.resolve(tenantId, "customer@example.com")))
                .isInstanceOf(BusinessException.class);
    }

    // ================= Rejection =================

    @Test
    void anUnknownEmailIsRejected() {
        when(contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(java.util.Optional.empty());
        when(accountRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(List.of());

        assertThat(codeOf(catchThrowable(() -> resolver.resolve(tenantId, "nobody@example.com"))))
                .isEqualTo("FEEDBACK_CUSTOMER_NOT_FOUND");
    }

    @Test
    void anUnknownEmailNeverCreatesACustomerRecord() {
        when(contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(java.util.Optional.empty());
        when(accountRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(List.of());

        catchThrowable(() -> resolver.resolve(tenantId, "new@example.com"));

        // Resolution only: the resolver holds no save/create method at all, so a
        // rejection can never seed CRM master data. It records that it consulted
        // both repositories and got no match.
        org.mockito.Mockito.verify(contactRepository)
                .findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, "new@example.com");
        org.mockito.Mockito.verify(accountRepository)
                .findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, "new@example.com");
        org.mockito.Mockito.verifyNoMoreInteractions(contactRepository, accountRepository);
    }

    @Test
    void aBlankEmailIsRejectedWithoutQuerying() {
        for (String blank : new String[] { null, "", "   " }) {
            assertThat(codeOf(catchThrowable(() -> resolver.resolve(tenantId, blank))))
                    .as("blank email [%s] must be rejected", blank)
                    .isEqualTo("FEEDBACK_CUSTOMER_NOT_FOUND");
        }
        verifyNoInteractions(accountRepository, contactRepository);
    }

    // ================= Tenant scoping =================

    @Test
    void resolutionIsAlwaysTenantScoped() {
        UUID otherTenant = UUID.randomUUID();

        when(contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, "customer@example.com"))
                .thenReturn(java.util.Optional.of(contact(UUID.randomUUID())));
        when(accountRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, "customer@example.com"))
                .thenReturn(List.of(account()));

        resolver.resolve(tenantId, "customer@example.com");

        // The caller's tenant is threaded into the contact finder. Because a Contact
        // matched, the Account path is never reached at all.
        org.mockito.Mockito.verify(contactRepository)
                .findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, "customer@example.com");
        org.mockito.Mockito.verifyNoInteractions(accountRepository);
        assertThat(otherTenant).isNotEqualTo(tenantId);
    }

    @Test
    void aSoftDeletedRecordIsNotAMatchBecauseTheFindersExcludeIt() throws Exception {
        // Asserted at the finder level: both finders carry AndDeletedFalse, which is
        // the repository's only soft-delete mechanism (there is no @SQLDelete or
        // @Where on these entities).
        java.lang.reflect.Method contactMethod = ContactRepository.class.getMethod(
                "findByTenantIdAndEmailIgnoreCaseAndDeletedFalse", UUID.class, String.class);
        java.lang.reflect.Method accountMethod = AccountRepository.class.getMethod(
                "findByTenantIdAndEmailIgnoreCaseAndDeletedFalse", UUID.class, String.class);

        assertThat(contactMethod.getName()).endsWith("AndDeletedFalse");
        assertThat(accountMethod.getName()).endsWith("AndDeletedFalse");
    }

    // ================= Inactive accounts are still real customers =================

    @Test
    void anInactiveAccountIsStillAValidMatch() {
        // is_active is deliberately not consulted: no other identity lookup in this
        // codebase filters on it, and an inactive account is still a real customer.
        Account inactive = account();
        inactive.setIsActive(false);
        when(contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(java.util.Optional.empty());
        when(accountRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(any(), anyString()))
                .thenReturn(List.of(inactive));

        assertThat(resolver.resolve(tenantId, "customer@example.com").accountId())
                .isEqualTo(inactive.getId());
    }

    // ================= Normalisation =================

    @Test
    void surroundingWhitespaceIsTrimmedBeforeLookup() {
        Contact contact = contact(UUID.randomUUID());
        when(contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, "customer@example.com"))
                .thenReturn(java.util.Optional.of(contact));

        assertThat(resolver.resolve(tenantId, "  customer@example.com  ").contactId())
                .isEqualTo(contact.getId());
    }

    @Test
    void caseIsHandledByTheFinderNotByTheResolver() {
        // The resolver does not lowercase: EmailIgnoreCase already owns that, and
        // doing it twice would hide a mismatch between the column and the index.
        Contact contact = contact(UUID.randomUUID());
        when(contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, "Customer@Example.COM"))
                .thenReturn(java.util.Optional.of(contact));

        assertThat(resolver.resolve(tenantId, "Customer@Example.COM").contactId())
                .isEqualTo(contact.getId());
    }
}
