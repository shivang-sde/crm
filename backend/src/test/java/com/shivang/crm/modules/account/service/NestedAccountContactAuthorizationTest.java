package com.shivang.crm.modules.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.contact.service.ContactService;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.exception.PermissionDeniedException;

/**
 * L3.0-P3: nested Account operations must evaluate the parent Account's scope.
 *
 * The real RecordScopeGuard is wired with a mocked PermissionEvaluatorService so
 * the actual scope resolution runs. UserUtil.currentUserId() reads the Spring
 * SecurityContext, so an authenticated principal is installed per test.
 */
class NestedAccountContactAuthorizationTest {

    private AccountRepository accountRepository;
    private ContactRepository contactRepository;
    private PermissionEvaluatorService permissionEvaluatorService;
    private UserRepository userRepository;
    private AccountService accountService;
    private ContactService contactService;

    private UUID tenantId;
    private UUID callerId;
    private UUID otherUserId;
    private UUID accountId;
    private UUID contactId;

    @BeforeEach
    void setup() {
        accountRepository = mock(AccountRepository.class);
        contactRepository = mock(ContactRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        userRepository = mock(UserRepository.class);

        RecordScopeGuard guard = new RecordScopeGuard(permissionEvaluatorService, userRepository);

        accountService = new AccountService(
                accountRepository,
                mock(com.shivang.crm.modules.account.mapper.AccountMapper.class),
                contactRepository,
                mock(com.shivang.crm.modules.contact.mapper.ContactMapper.class),
                permissionEvaluatorService,
                guard,
                mock(com.shivang.crm.shared.event.CanonicalCrmEventPublisher.class));

        contactService = new ContactService(
                contactRepository,
                guard,
                mock(com.shivang.crm.modules.contact.mapper.ContactMapper.class),
                permissionEvaluatorService,
                mock(com.shivang.crm.shared.event.CanonicalCrmEventPublisher.class),
                new com.shivang.crm.shared.service.CustomerRelationshipValidator(
                        accountRepository, contactRepository,
                        mock(DealRepository.class), mock(LeadRepository.class),
                        mock(TicketRepository.class), userRepository));

        tenantId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        contactId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(callerId.toString(), null, java.util.List.of()));

        // TEAM membership so TEAM scope has something to resolve.
        // RecordScopeGuard.teamMemberIds delegates to PermissionEvaluatorService.
        when(permissionEvaluatorService.getTeamUserIds(callerId, tenantId))
                .thenReturn(java.util.List.of(otherUserId));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void grantScope(String module, String action, String scope) {
        when(permissionEvaluatorService.getAccessScope(callerId, tenantId, module, action))
                .thenReturn(scope);
    }

    private Account accountOwnedBy(UUID ownerId) {
        Account account = new Account();
        account.setId(accountId);
        account.setTenantId(tenantId);
        account.setName("Acme");
        account.setOwnerId(ownerId);
        account.setCreatedBy(ownerId);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(accountId, tenantId))
                .thenReturn(Optional.of(account));
        return account;
    }

    private Contact contactOwnedBy(UUID ownerId) {
        Contact contact = new Contact();
        contact.setId(contactId);
        contact.setTenantId(tenantId);
        contact.setAccountId(accountId);
        contact.setOwnerId(ownerId);
        contact.setCreatedBy(ownerId);
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(contactId, tenantId))
                .thenReturn(Optional.of(contact));
        return contact;
    }

    // --- the bypass P3 fixes ---

    /**
     * The regression this phase fixes: without a parent scope check, an OWN-scoped
     * caller could act on an Account owned by someone else, because the nested
     * request was authorized only by its own module permission.
     */
    @Test
    void ownScopedCallerCannotMutateNestedResourceOfForeignAccount() {
        accountOwnedBy(otherUserId);
        grantScope("account", "write", "OWN");

        Throwable thrown = catchThrowable(() -> accountService.assertAccountAccessible(tenantId, accountId, "write"));

        assertThat(thrown).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void ownScopedCallerCannotReadNestedResourceOfForeignAccount() {
        accountOwnedBy(otherUserId);
        grantScope("account", "read", "OWN");

        assertThat(catchThrowable(() -> accountService.assertAccountAccessible(tenantId, accountId, "read")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void ownScopedCallerCannotDeleteNestedResourceOfForeignAccount() {
        accountOwnedBy(otherUserId);
        grantScope("account", "delete", "OWN");

        assertThat(catchThrowable(() -> accountService.assertAccountAccessible(tenantId, accountId, "delete")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void ownScopedCallerCannotMutateNestedResourceOfForeignContact() {
        contactOwnedBy(otherUserId);
        grantScope("contact", "write", "OWN");

        assertThat(catchThrowable(() -> contactService.assertContactAccessible(tenantId, contactId, "write")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void ownScopedCallerCannotReadNestedResourceOfForeignContact() {
        contactOwnedBy(otherUserId);
        grantScope("contact", "read", "OWN");

        assertThat(catchThrowable(() -> contactService.assertContactAccessible(tenantId, contactId, "read")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- happy paths ---

    @Test
    void ownScopedCallerCanActOnOwnAccount() {
        accountOwnedBy(callerId);
        grantScope("account", "write", "OWN");

        assertThatNoException().isThrownBy(() -> accountService.assertAccountAccessible(tenantId, accountId, "write"));
    }

    @Test
    void ownScopedCallerCanActOnOwnContact() {
        contactOwnedBy(callerId);
        grantScope("contact", "write", "OWN");

        assertThatNoException().isThrownBy(() -> contactService.assertContactAccessible(tenantId, contactId, "write"));
    }

    @Test
    void allScopedCallerCanActOnAnyAccount() {
        accountOwnedBy(otherUserId);
        grantScope("account", "write", "ALL");

        assertThatNoException().isThrownBy(() -> accountService.assertAccountAccessible(tenantId, accountId, "write"));
    }

    @Test
    void allScopedCallerCanActOnAnyContact() {
        contactOwnedBy(otherUserId);
        grantScope("contact", "delete", "ALL");

        assertThatNoException().isThrownBy(() -> contactService.assertContactAccessible(tenantId, contactId, "delete"));
    }

    @Test
    void teamScopedCallerCanActOnTeamMemberAccount() {
        accountOwnedBy(otherUserId);
        grantScope("account", "write", "TEAM");

        assertThatNoException().isThrownBy(() -> accountService.assertAccountAccessible(tenantId, accountId, "write"));
    }

    @Test
    void teamScopedCallerCanActOnTeamMemberContact() {
        contactOwnedBy(otherUserId);
        grantScope("contact", "write", "TEAM");

        assertThatNoException().isThrownBy(() -> contactService.assertContactAccessible(tenantId, contactId, "write"));
    }

    @Test
    void teamScopedCallerCannotActOnNonTeamAccount() {
        Account account = accountOwnedBy(UUID.randomUUID());
        grantScope("account", "write", "TEAM");

        assertThat(catchThrowable(() -> accountService.assertAccountAccessible(tenantId, account.getId(), "write")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- child module permission is still required ---

    @Test
    void missingAccountWritePermissionIsDeniedEvenForOwnAccount() {
        accountOwnedBy(callerId);
        grantScope("account", "write", "NONE");

        assertThat(catchThrowable(() -> accountService.assertAccountAccessible(tenantId, accountId, "write")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("record-level access");
    }

    @Test
    void missingContactReadPermissionIsDeniedEvenForOwnContact() {
        contactOwnedBy(callerId);
        grantScope("contact", "read", "NONE");

        assertThat(catchThrowable(() -> contactService.assertContactAccessible(tenantId, contactId, "read")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- P2 tenant isolation is preserved underneath ---

    @Test
    void crossTenantAccountIsNotFoundNotMerelyOutOfScope() {
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(accountId, tenantId))
                .thenReturn(Optional.empty());
        grantScope("account", "write", "ALL");

        Throwable thrown = catchThrowable(() -> accountService.assertAccountAccessible(tenantId, accountId, "write"));

        // P2's deleted-aware, tenant-scoped lookup yields NOT_FOUND, so a
        // cross-tenant id is indistinguishable from a missing one.
        assertThat(thrown).isInstanceOf(com.shivang.crm.shared.exception.BusinessException.class);
        assertThat(thrown.getMessage()).contains("Account not found");
    }

    @Test
    void softDeletedAccountIsNotFound() {
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(accountId, tenantId))
                .thenReturn(Optional.empty());
        grantScope("account", "write", "ALL");

        assertThat(catchThrowable(() -> accountService.assertAccountAccessible(tenantId, accountId, "write")))
                .isInstanceOf(com.shivang.crm.shared.exception.BusinessException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void softDeletedContactIsNotFound() {
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(contactId, tenantId))
                .thenReturn(Optional.empty());
        grantScope("contact", "write", "ALL");

        assertThat(catchThrowable(() -> contactService.assertContactAccessible(tenantId, contactId, "write")))
                .isInstanceOf(com.shivang.crm.shared.exception.BusinessException.class)
                .hasMessageContaining("Contact not found");
    }

    // --- nested reads keep their own action ---

    @Test
    void readScopeIsRequiredForNestedReads() {
        // The caller may write but not read; a nested read must still be denied.
        accountOwnedBy(callerId);
        grantScope("account", "write", "ALL");
        grantScope("account", "read", "NONE");

        assertThat(catchThrowable(() -> accountService.assertAccountAccessible(tenantId, accountId, "read")))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatNoException().isThrownBy(() -> accountService.assertAccountAccessible(tenantId, accountId, "write"));
    }

    @Test
    void deleteScopeIsRequiredSeparatelyFromWriteForNestedDeletes() {
        accountOwnedBy(callerId);
        grantScope("account", "write", "ALL");
        grantScope("account", "delete", "NONE");

        assertThat(catchThrowable(() -> accountService.assertAccountAccessible(tenantId, accountId, "delete")))
                .isInstanceOf(PermissionDeniedException.class);
    }
}