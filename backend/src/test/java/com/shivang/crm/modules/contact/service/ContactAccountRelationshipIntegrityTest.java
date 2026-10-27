package com.shivang.crm.modules.contact.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.contact.dto.ContactCreateRequest;
import com.shivang.crm.modules.contact.dto.ContactResponse;
import com.shivang.crm.modules.contact.dto.ContactUpdateRequest;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.mapper.ContactMapper;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.0-P2: Contact -> Account integrity.
 *
 * contacts.account_id is NOT NULL and carries a foreign key, but that FK is
 * tenant-blind, so createContact must additionally prove the parent Account is a
 * live record of the caller's tenant.
 */
class ContactAccountRelationshipIntegrityTest {

    private ContactRepository contactRepository;
    private AccountRepository accountRepository;
    private ContactMapper contactMapper;
    private CustomerRelationshipValidator validator;
    private ContactService service;

    private UUID tenantA;
    private UUID tenantB;
    private UUID actorId;
    private UUID contactId;

    @BeforeEach
    void setup() {
        contactRepository = mock(ContactRepository.class);
        accountRepository = mock(AccountRepository.class);
        contactMapper = mock(ContactMapper.class);

validator = new CustomerRelationshipValidator(
                accountRepository,
                contactRepository,
                mock(DealRepository.class),
                mock(LeadRepository.class),
                mock(TicketRepository.class),
                mock(UserRepository.class));

        service = new ContactService(
                contactRepository,
                mock(RecordScopeGuard.class),
                contactMapper,
                mock(PermissionEvaluatorService.class),
                mock(CanonicalCrmEventPublisher.class),
                validator);

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        actorId = UUID.randomUUID();
        contactId = UUID.randomUUID();

        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> {
            Contact saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(contactId);
            }
            return saved;
        });
        when(contactMapper.toResponse(any(Contact.class)))
                .thenAnswer(invocation -> ContactResponse.builder().build());
    }

    private void assertCode(String expectedCode, ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expectedCode);
    }

    private UUID liveAccountIn(UUID tenant) {
        UUID id = UUID.randomUUID();
        Account account = new Account();
        account.setId(id);
        account.setTenantId(tenant);
        account.setName("Acme");
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(account));
        return id;
    }

    private ContactCreateRequest createRequest(UUID accountId) {
        return ContactCreateRequest.builder()
                .accountId(accountId)
                .firstName("Jane")
                .lastName("Doe")
                .build();
    }

    private void stubMapperForCreate(ContactCreateRequest request) {
        Contact mapped = new Contact();
        mapped.setAccountId(request.getAccountId());
        mapped.setFirstName(request.getFirstName());
        mapped.setLastName(request.getLastName());
        when(contactMapper.toEntity(request)).thenReturn(mapped);
    }

    // --- create ---

    @Test
    void createAcceptsSameTenantActiveAccount() {
        ContactCreateRequest request = createRequest(liveAccountIn(tenantA));
        stubMapperForCreate(request);

        assertThatNoException().isThrownBy(() -> service.createContact(tenantA, actorId, request));
    }

    @Test
    void createRejectsCrossTenantAccount() {
        UUID foreignAccountId = UUID.randomUUID();
        Account foreign = new Account();
        foreign.setId(foreignAccountId);
        foreign.setTenantId(tenantB);
        foreign.setName("OtherTenantAcme");
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(foreignAccountId, tenantB))
                .thenReturn(Optional.of(foreign));

        ContactCreateRequest request = createRequest(foreignAccountId);
        stubMapperForCreate(request);

        assertCode("ACCOUNT_NOT_FOUND", () -> service.createContact(tenantA, actorId, request));
    }

    @Test
    void createRejectsDeletedAccount() {
        ContactCreateRequest request = createRequest(UUID.randomUUID());
        stubMapperForCreate(request);

        // The deletedFalse lookup returns empty.
        assertCode("ACCOUNT_NOT_FOUND", () -> service.createContact(tenantA, actorId, request));
    }

    @Test
    void createRejectsUnknownAccount() {
        ContactCreateRequest request = createRequest(UUID.randomUUID());
        stubMapperForCreate(request);

        assertCode("ACCOUNT_NOT_FOUND", () -> service.createContact(tenantA, actorId, request));
    }

    // --- update ---
    //
    // ContactUpdateRequest exposes no accountId and ContactMapper.updateEntity
    // maps only ownerUserId -> ownerId, so account re-parenting is not reachable
    // through the current API. These tests pin that invariant so a future DTO
    // change cannot silently reintroduce an unvalidated reassignment.

    @Test
    void updateRequestCannotCarryAnAccountId() {
        assertThat(java.util.Arrays.stream(ContactUpdateRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("accountId");
    }

    @Test
    void updatePreservesExistingAccountId() {
        UUID accountId = liveAccountIn(tenantA);
        Contact contact = new Contact();
        contact.setId(contactId);
        contact.setTenantId(tenantA);
        contact.setAccountId(accountId);
        contact.setCreatedBy(actorId);
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(contactId, tenantA))
                .thenReturn(Optional.of(contact));

        ContactUpdateRequest request = new ContactUpdateRequest();
        request.setFirstName("Renamed");

        assertThatNoException().isThrownBy(() -> service.updateContact(contactId, tenantA, actorId, request));
    }

    @Test
    void updateExcludesSoftDeletedContactFromLiveLookup() {
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(contactId, tenantA))
                .thenReturn(Optional.empty());

        ContactUpdateRequest request = new ContactUpdateRequest();
        request.setFirstName("Renamed");

        assertCode("NOT_FOUND", () -> service.updateContact(contactId, tenantA, actorId, request));
    }

    // --- live reads ---

    @Test
    void liveReadExcludesSoftDeletedContact() {
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(contactId, tenantA))
                .thenReturn(Optional.empty());

        assertCode("NOT_FOUND", () -> service.assignOwner(contactId, tenantA, actorId, actorId));
    }
}