package com.shivang.crm.modules.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.account.service.AccountService;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.contact.service.ContactService;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;

/**
 * L3.0-P4-H: Account, Contact and Deal deletion is a soft delete, so the rows
 * that other records and the timeline point at survive.
 *
 * <p>The assertion that matters is the absence of a physical delete: if any of
 * these services calls {@code repository.delete(...)} the FK either fails or the
 * customer history disappears with the row.
 */
class CustomerDomainSoftDeleteTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    // --- Account ---

    @Test
    void accountDeleteSoftDeletesInsteadOfRemovingTheRow() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountService service = new AccountService(
                accountRepository,
                mock(com.shivang.crm.modules.account.mapper.AccountMapper.class),
                mock(ContactRepository.class),
                mock(com.shivang.crm.modules.contact.mapper.ContactMapper.class),
                mock(com.shivang.crm.modules.rbac.service.PermissionEvaluatorService.class),
                mock(RecordScopeGuard.class),
                mock(CanonicalCrmEventPublisher.class));

        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setTenantId(TENANT_ID);
        account.setOwnerId(USER_ID);
        account.setCreatedBy(USER_ID);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(account.getId(), TENANT_ID))
                .thenReturn(java.util.Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteAccount(account.getId(), TENANT_ID, USER_ID);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getDeleted()).isTrue();
        assertThat(saved.getValue().getDeletedBy()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getUpdatedBy()).isEqualTo(USER_ID);
        // The customer identity itself is unchanged: contacts, deals and
        // entitlements keep resolving against this id.
        assertThat(saved.getValue().getId()).isEqualTo(account.getId());

        verify(accountRepository, never()).delete(any(Account.class));
        verify(accountRepository, never()).deleteAll(any());
    }

    @Test
    void accountDeleteDoesNotMutateItsContacts() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        ContactRepository contactRepository = mock(ContactRepository.class);
        AccountService service = new AccountService(
                accountRepository,
                mock(com.shivang.crm.modules.account.mapper.AccountMapper.class),
                contactRepository,
                mock(com.shivang.crm.modules.contact.mapper.ContactMapper.class),
                mock(com.shivang.crm.modules.rbac.service.PermissionEvaluatorService.class),
                mock(RecordScopeGuard.class),
                mock(CanonicalCrmEventPublisher.class));

        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setTenantId(TENANT_ID);
        account.setOwnerId(USER_ID);
        account.setCreatedBy(USER_ID);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(account.getId(), TENANT_ID))
                .thenReturn(java.util.Optional.of(account));

        service.deleteAccount(account.getId(), TENANT_ID, USER_ID);

        verify(contactRepository, never()).save(any(Contact.class));
        verify(contactRepository, never()).delete(any(Contact.class));
        verify(contactRepository, never()).delete(any(Contact.class));
        verify(contactRepository, never()).deleteAll(any());
    }

    @Test
    void deletingAnAccountInAnotherTenantIsNotFound() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        AccountService service = new AccountService(
                accountRepository,
                mock(com.shivang.crm.modules.account.mapper.AccountMapper.class),
                mock(ContactRepository.class),
                mock(com.shivang.crm.modules.contact.mapper.ContactMapper.class),
                mock(com.shivang.crm.modules.rbac.service.PermissionEvaluatorService.class),
                mock(RecordScopeGuard.class),
                mock(CanonicalCrmEventPublisher.class));

        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(any(), any()))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.deleteAccount(UUID.randomUUID(), UUID.randomUUID(), USER_ID))
                .isInstanceOf(com.shivang.crm.shared.exception.BusinessException.class);
        verify(accountRepository, never()).save(any(Account.class));
    }

    // --- Contact ---

    @Test
    void contactDeleteSoftDeletesInsteadOfRemovingTheRow() {
        ContactRepository contactRepository = mock(ContactRepository.class);
        ContactService service = new ContactService(
                contactRepository,
                mock(RecordScopeGuard.class),
                mock(com.shivang.crm.modules.contact.mapper.ContactMapper.class),
                mock(com.shivang.crm.modules.rbac.service.PermissionEvaluatorService.class),
                mock(CanonicalCrmEventPublisher.class),
                mock(com.shivang.crm.shared.service.CustomerRelationshipValidator.class));

        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setTenantId(TENANT_ID);
        contact.setOwnerId(USER_ID);
        contact.setCreatedBy(USER_ID);
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(contact.getId(), TENANT_ID))
                .thenReturn(java.util.Optional.of(contact));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteContact(contact.getId(), TENANT_ID, USER_ID);

        ArgumentCaptor<Contact> saved = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(saved.capture());
        assertThat(saved.getValue().getDeleted()).isTrue();
        assertThat(saved.getValue().getDeletedBy()).isEqualTo(USER_ID);

        verify(contactRepository, never()).delete(any(Contact.class));
        verify(contactRepository, never()).deleteAll(any());
    }

    // --- Deal ---

    @Test
    void dealDeleteSoftDeletesInsteadOfRemovingTheRow() {
        DealRepository dealRepository = mock(DealRepository.class);
        DealService service = new DealService(
                dealRepository,
                mock(com.shivang.crm.modules.deal.repository.DealCustomFieldRepository.class),
                mock(com.shivang.crm.modules.deal.repository.DealLineItemRepository.class),
                mock(com.shivang.crm.modules.deal.repository.DealStageRepository.class),
                mock(com.shivang.crm.modules.deal.service.DealLineItemService.class),
                mock(com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository.class),
                mock(CanonicalCrmEventPublisher.class),
                mock(com.shivang.crm.modules.deal.mapper.DealMapper.class),
                mock(com.shivang.crm.modules.activity.service.ActivityService.class),
                mock(com.shivang.crm.modules.lead.service.EntityHistoryService.class),
                mock(com.shivang.crm.modules.entitlement.service.EntitlementProvisioningService.class),
                mock(UserRepository.class),
                mock(com.shivang.crm.modules.rbac.service.PermissionEvaluatorService.class),
                mock(RecordScopeGuard.class),
                mock(com.shivang.crm.shared.service.CustomerRelationshipValidator.class));

        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setTenantId(TENANT_ID);
        deal.setOwnerId(USER_ID);
        deal.setCreatedBy(USER_ID);
        DealStage stage = new DealStage();
        stage.setId(UUID.randomUUID());
        stage.setTenantId(TENANT_ID);
        stage.setRecordCategory(RecordCategory.CLOSED_WON);
        deal.setStage(stage);
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(deal.getId(), TENANT_ID))
                .thenReturn(java.util.Optional.of(deal));
        when(dealRepository.save(any(Deal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteDeal(deal.getId(), TENANT_ID, USER_ID);

        ArgumentCaptor<Deal> saved = ArgumentCaptor.forClass(Deal.class);
        verify(dealRepository).save(saved.capture());
        assertThat(saved.getValue().getDeleted()).isTrue();
        assertThat(saved.getValue().getDeletedBy()).isEqualTo(USER_ID);

        verify(dealRepository, never()).delete(any(Deal.class));
        verify(dealRepository, never()).deleteAll(any());
    }

    /**
     * The won Deal's entitlements are the historical record of what the customer
     * was sold. Deleting the Deal must not cascade into them.
     */
    @Test
    void dealDeleteLeavesItsEntitlementsAlone() {
        DealRepository dealRepository = mock(DealRepository.class);
        var entitlementRepository =
                mock(com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository.class);
        DealService service = new DealService(
                dealRepository,
                mock(com.shivang.crm.modules.deal.repository.DealCustomFieldRepository.class),
                mock(com.shivang.crm.modules.deal.repository.DealLineItemRepository.class),
                mock(com.shivang.crm.modules.deal.repository.DealStageRepository.class),
                mock(com.shivang.crm.modules.deal.service.DealLineItemService.class),
                entitlementRepository,
                mock(CanonicalCrmEventPublisher.class),
                mock(com.shivang.crm.modules.deal.mapper.DealMapper.class),
                mock(com.shivang.crm.modules.activity.service.ActivityService.class),
                mock(com.shivang.crm.modules.lead.service.EntityHistoryService.class),
                mock(com.shivang.crm.modules.entitlement.service.EntitlementProvisioningService.class),
                mock(UserRepository.class),
                mock(com.shivang.crm.modules.rbac.service.PermissionEvaluatorService.class),
                mock(RecordScopeGuard.class),
                mock(com.shivang.crm.shared.service.CustomerRelationshipValidator.class));

        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setTenantId(TENANT_ID);
        deal.setOwnerId(USER_ID);
        deal.setCreatedBy(USER_ID);
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(deal.getId(), TENANT_ID))
                .thenReturn(java.util.Optional.of(deal));

        service.deleteDeal(deal.getId(), TENANT_ID, USER_ID);

        verify(entitlementRepository, never())
                .save(any(com.shivang.crm.modules.entitlement.entity.CustomerEntitlement.class));
        verify(entitlementRepository, never())
                .delete(any(com.shivang.crm.modules.entitlement.entity.CustomerEntitlement.class));
        verify(entitlementRepository, never()).deleteAll(any());
    }
}
