package com.shivang.crm.shared.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.exception.BusinessException;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * L3.0-P2: relationship integrity guards.
 *
 * The repository mocks return empty for any id that is not explicitly registered
 * as live in tenantA. That mirrors the real query semantics: a row belonging to
 * tenantB, or soft-deleted, produces no row for a tenantA + deletedFalse lookup.
 */
class CustomerRelationshipValidatorTest {

    private AccountRepository accountRepository;
    private ContactRepository contactRepository;
    private DealRepository dealRepository;
private LeadRepository leadRepository;
    private UserRepository userRepository;
    private TicketRepository ticketRepository;
    private CustomerRelationshipValidator validator;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setup() {
        accountRepository = mock(AccountRepository.class);
        contactRepository = mock(ContactRepository.class);
        dealRepository = mock(DealRepository.class);
leadRepository = mock(LeadRepository.class);
        userRepository = mock(UserRepository.class);
        ticketRepository = mock(TicketRepository.class);
        validator = new CustomerRelationshipValidator(
                accountRepository, contactRepository, dealRepository, leadRepository,
                ticketRepository, userRepository);

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
    }

    /** BusinessException keeps the machine code in getErrorCode(), not the message. */
    private void assertRejected(String expectedCode, ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        org.assertj.core.api.Assertions.assertThat(thrown)
                .isInstanceOf(BusinessException.class);
        org.assertj.core.api.Assertions.assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(expectedCode);
    }

    private UUID liveAccountIn(UUID tenant) {
        UUID id = UUID.randomUUID();
        Account account = new Account();
        account.setId(id);
        account.setTenantId(tenant);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(account));
        return id;
    }

    private UUID liveContactIn(UUID tenant, UUID accountId) {
        UUID id = UUID.randomUUID();
        Contact contact = new Contact();
        contact.setId(id);
        contact.setTenantId(tenant);
        contact.setAccountId(accountId);
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(contact));
        return id;
    }

    private UUID liveLeadIn(UUID tenant) {
        UUID id = UUID.randomUUID();
        Lead lead = new Lead();
        lead.setId(id);
        lead.setTenantId(tenant);
        when(leadRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(lead));
        return id;
    }

    private UUID liveUserIn(UUID tenant) {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setTenantId(tenant);
        user.setIsActive(true);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(user));
        return id;
    }

    // --- null handling ---

    @Test
    void nullReferencesAreNoOps() {
        assertThatNoException().isThrownBy(() -> {
            validator.requireLiveAccount(null, tenantA);
            validator.requireLiveContact(null, tenantA);
            validator.requireLiveLead(null, tenantA);
            validator.requireTenantOwner(null, tenantA);
            validator.requireContactBelongsToAccount(null, null, tenantA);
        });
    }

    // --- Account ---

    @Test
    void sameTenantActiveAccountAccepted() {
        UUID accountId = liveAccountIn(tenantA);
        assertThatNoException().isThrownBy(() -> validator.requireLiveAccount(accountId, tenantA));
    }

    @Test
    void crossTenantAccountRejected() {
        UUID foreignId = UUID.randomUUID();
        Account foreign = new Account();
        foreign.setId(foreignId);
        foreign.setTenantId(tenantB);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(foreignId, tenantB))
                .thenReturn(Optional.of(foreign));

        assertRejected("ACCOUNT_NOT_FOUND", () -> validator.requireLiveAccount(foreignId, tenantA));
    }

    @Test
    void deletedAccountRejected() {
        UUID deletedId = UUID.randomUUID();
        Account deleted = new Account();
        deleted.setId(deletedId);
        deleted.setTenantId(tenantA);
        deleted.softDelete(UUID.randomUUID());
        // Only resolvable without the deleted flag, i.e. via the unscoped path.
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(deletedId, tenantA)).thenReturn(Optional.empty());

        assertRejected("ACCOUNT_NOT_FOUND", () -> validator.requireLiveAccount(deletedId, tenantA));
    }

    @Test
    void unknownAccountRejected() {
        assertRejected("ACCOUNT_NOT_FOUND", () -> validator.requireLiveAccount(UUID.randomUUID(), tenantA));
    }

    // --- Contact ---

    @Test
    void sameTenantActiveContactAccepted() {
        UUID contactId = liveContactIn(tenantA, liveAccountIn(tenantA));
        assertThatNoException().isThrownBy(() -> validator.requireLiveContact(contactId, tenantA));
    }

    @Test
    void crossTenantContactRejected() {
        UUID foreignId = UUID.randomUUID();
        Contact foreign = new Contact();
        foreign.setId(foreignId);
        foreign.setTenantId(tenantB);
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(foreignId, tenantB))
                .thenReturn(Optional.of(foreign));

        assertRejected("CONTACT_NOT_FOUND", () -> validator.requireLiveContact(foreignId, tenantA));
    }

    @Test
    void deletedContactRejected() {
        UUID deletedId = UUID.randomUUID();
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(deletedId, tenantA)).thenReturn(Optional.empty());

        assertRejected("CONTACT_NOT_FOUND", () -> validator.requireLiveContact(deletedId, tenantA));
    }

    // --- Lead ---

    @Test
    void sameTenantActiveLeadAccepted() {
        assertThatNoException().isThrownBy(() -> validator.requireLiveLead(liveLeadIn(tenantA), tenantA));
    }

    @Test
    void crossTenantLeadRejected() {
        UUID foreignId = UUID.randomUUID();
        Lead foreign = new Lead();
        foreign.setId(foreignId);
        foreign.setTenantId(tenantB);
        when(leadRepository.findByIdAndTenantIdAndDeletedFalse(foreignId, tenantB))
                .thenReturn(Optional.of(foreign));

        assertRejected("LEAD_NOT_FOUND", () -> validator.requireLiveLead(foreignId, tenantA));
    }

    @Test
    void deletedLeadRejected() {
        UUID deletedId = UUID.randomUUID();
        when(leadRepository.findByIdAndTenantIdAndDeletedFalse(deletedId, tenantA)).thenReturn(Optional.empty());

        assertRejected("LEAD_NOT_FOUND", () -> validator.requireLiveLead(deletedId, tenantA));
    }

    // --- Owner ---

    @Test
    void sameTenantOwnerAccepted() {
        assertThatNoException().isThrownBy(() -> validator.requireTenantOwner(liveUserIn(tenantA), tenantA));
    }

    @Test
    void crossTenantOwnerRejected() {
        UUID foreignUserId = UUID.randomUUID();
        User foreign = new User();
        foreign.setId(foreignUserId);
        foreign.setTenantId(tenantB);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(foreignUserId, tenantB))
                .thenReturn(Optional.of(foreign));

        assertRejected("OWNER_NOT_FOUND", () -> validator.requireTenantOwner(foreignUserId, tenantA));
    }

    @Test
    void deletedOwnerRejected() {
        UUID deletedUserId = UUID.randomUUID();
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(deletedUserId, tenantA))
                .thenReturn(Optional.empty());

        assertRejected("OWNER_NOT_FOUND", () -> validator.requireTenantOwner(deletedUserId, tenantA));
    }

    @Test
    void inactiveOwnerRejected() {
        UUID inactiveUserId = UUID.randomUUID();
        User inactive = new User();
        inactive.setId(inactiveUserId);
        inactive.setTenantId(tenantA);
        inactive.setIsActive(false);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(inactiveUserId, tenantA))
                .thenReturn(Optional.of(inactive));

        assertRejected("OWNER_NOT_FOUND", () -> validator.requireTenantOwner(inactiveUserId, tenantA));
    }

    // --- Deal contact/account consistency ---

    @Test
    void contactBelongingToSameAccountAccepted() {
        UUID accountId = liveAccountIn(tenantA);
        UUID contactId = liveContactIn(tenantA, accountId);

        assertThatNoException().isThrownBy(
                () -> validator.requireContactBelongsToAccount(contactId, accountId, tenantA));
    }

    @Test
    void contactBelongingToAnotherAccountRejected() {
        UUID accountA = liveAccountIn(tenantA);
        UUID accountB = liveAccountIn(tenantA);
        UUID contactOfB = liveContactIn(tenantA, accountB);

        assertRejected("DEAL_CONTACT_ACCOUNT_MISMATCH",
                () -> validator.requireContactBelongsToAccount(contactOfB, accountA, tenantA));
    }

    @Test
    void crossTenantContactInConsistencyCheckRejected() {
        UUID accountId = liveAccountIn(tenantA);
        UUID foreignContact = UUID.randomUUID();
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(foreignContact, tenantA))
                .thenReturn(Optional.empty());

        assertRejected("CONTACT_NOT_FOUND",
                () -> validator.requireContactBelongsToAccount(foreignContact, accountId, tenantA));
    }

    @Test
    void deletedContactInConsistencyCheckRejected() {
        UUID accountId = liveAccountIn(tenantA);
        UUID deletedContact = UUID.randomUUID();
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(deletedContact, tenantA))
                .thenReturn(Optional.empty());

        assertRejected("CONTACT_NOT_FOUND",
                () -> validator.requireContactBelongsToAccount(deletedContact, accountId, tenantA));
    }

    @Test
    void missingAccountSideSkipsConsistencyCheck() {
        UUID accountId = liveAccountIn(tenantA);
        UUID contactId = liveContactIn(tenantA, accountId);

        // A Deal may legitimately have a contact but no account yet.
        assertThatNoException().isThrownBy(
                () -> validator.requireContactBelongsToAccount(contactId, null, tenantA));
    }
}