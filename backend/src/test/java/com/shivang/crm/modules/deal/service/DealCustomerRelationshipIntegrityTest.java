package com.shivang.crm.modules.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.dto.DealCreateRequest;
import com.shivang.crm.modules.deal.dto.DealUpdateRequest;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.mapper.DealMapper;
import com.shivang.crm.modules.deal.repository.DealCustomFieldRepository;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.deal.repository.DealStageRepository;
import com.shivang.crm.modules.entitlement.service.EntitlementProvisioningService;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.modules.lead.service.EntityHistoryService;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.0-P2: Deal customer-reference integrity on create and update.
 *
 * The real CustomerRelationshipValidator is wired with mocked repositories, so
 * each test covers both the guard semantics and the exact point at which
 * DealService invokes it.
 */
class DealCustomerRelationshipIntegrityTest {

    private DealRepository dealRepository;
    private DealCustomFieldRepository dealCustomFieldRepository;
    private DealLineItemRepository dealLineItemRepository;
    private DealStageRepository dealStageRepository;
    private AccountRepository accountRepository;
    private ContactRepository contactRepository;
private LeadRepository leadRepository;
    private UserRepository userRepository;
    private TicketRepository ticketRepository;
    private DealMapper dealMapper;
    private CustomerRelationshipValidator validator;
    private DealService service;

    private UUID tenantA;
    private UUID tenantB;
    private UUID actorId;
    private DealStage openStage;

    @BeforeEach
    void setup() {
        dealRepository = mock(DealRepository.class);
        dealCustomFieldRepository = mock(DealCustomFieldRepository.class);
        dealLineItemRepository = mock(DealLineItemRepository.class);
        dealStageRepository = mock(DealStageRepository.class);
        dealMapper = mock(DealMapper.class);
        accountRepository = mock(AccountRepository.class);
        contactRepository = mock(ContactRepository.class);
        leadRepository = mock(LeadRepository.class);
        userRepository = mock(UserRepository.class);
        ticketRepository = mock(TicketRepository.class);

        validator = new CustomerRelationshipValidator(
                accountRepository, contactRepository, dealRepository, leadRepository,
                ticketRepository, userRepository);

        service = new DealService(
                dealRepository, dealCustomFieldRepository, dealLineItemRepository, dealStageRepository,
                mock(DealLineItemService.class),
                mock(CustomerEntitlementRepository.class),
                mock(CanonicalCrmEventPublisher.class), dealMapper, mock(ActivityService.class),
                mock(EntityHistoryService.class), mock(EntitlementProvisioningService.class), userRepository,
                mock(PermissionEvaluatorService.class), mock(RecordScopeGuard.class), validator);

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        actorId = UUID.randomUUID();

        when(dealCustomFieldRepository.findActiveFieldsByTenant(any())).thenReturn(List.of());
        when(dealLineItemRepository.existsByTenantIdAndDealIdAndDeletedFalse(any(), any())).thenReturn(false);
        when(dealRepository.save(any(Deal.class))).thenAnswer(invocation -> {
            Deal saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(dealMapper.toResponse(any(Deal.class)))
                .thenAnswer(invocation -> com.shivang.crm.modules.deal.dto.DealResponse.builder().build());

        openStage = new DealStage();
        openStage.setId(UUID.randomUUID());
        openStage.setTenantId(tenantA);
        openStage.setName("Qualification");
        openStage.setDisplayOrder(1);
        openStage.setRecordCategory(RecordCategory.OPEN);
        when(dealStageRepository.findByIdAndTenantId(openStage.getId(), tenantA)).thenReturn(Optional.of(openStage));
    }

    // --- assertion helpers ---

    private void assertRejected(String expectedCode, ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expectedCode);
    }

    // --- fixtures ---

    private UUID liveAccount(UUID tenant) {
        UUID id = UUID.randomUUID();
        Account account = new Account();
        account.setId(id);
        account.setTenantId(tenant);
        account.setName("Acme");
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(account));
        return id;
    }

    private UUID liveContact(UUID tenant, UUID accountId) {
        UUID id = UUID.randomUUID();
        Contact contact = new Contact();
        contact.setId(id);
        contact.setTenantId(tenant);
        contact.setAccountId(accountId);
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(contact));
        return id;
    }

    private UUID liveLead(UUID tenant) {
        UUID id = UUID.randomUUID();
        Lead lead = new Lead();
        lead.setId(id);
        lead.setTenantId(tenant);
        when(leadRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(lead));
        return id;
    }

    private UUID liveUser(UUID tenant) {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setTenantId(tenant);
        user.setIsActive(true);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(user));
        return id;
    }

    private DealCreateRequest createRequest() {
        return DealCreateRequest.builder()
                .name("Test Deal")
                .stageId(openStage.getId())
                .build();
    }

    private void stubMapperForCreate(DealCreateRequest request) {
        Deal mapped = new Deal();
        mapped.setName(request.getName());
        mapped.setAccountId(request.getAccountId());
        mapped.setContactId(request.getContactId());
        mapped.setLeadId(request.getLeadId());
        when(dealMapper.toEntity(request)).thenReturn(mapped);
    }

    private Deal existingDeal() {
        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setTenantId(tenantA);
        deal.setName("Existing Deal");
        deal.setStage(openStage);
        deal.setCreatedBy(actorId);
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(deal.getId(), tenantA)).thenReturn(Optional.of(deal));
        return deal;
    }

    // --- Deal -> Account ---

    @Test
    void createAcceptsSameTenantActiveAccount() {
        DealCreateRequest request = createRequest();
        request.setAccountId(liveAccount(tenantA));
        stubMapperForCreate(request);

        assertThatNoException().isThrownBy(() -> service.createDeal(tenantA, actorId, request));
    }

    @Test
    void createRejectsCrossTenantAccount() {
        UUID foreignAccountId = UUID.randomUUID();
        Account foreign = new Account();
        foreign.setId(foreignAccountId);
        foreign.setTenantId(tenantB);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(foreignAccountId, tenantB))
                .thenReturn(Optional.of(foreign));

        DealCreateRequest request = createRequest();
        request.setAccountId(foreignAccountId);

        assertRejected("ACCOUNT_NOT_FOUND", () -> service.createDeal(tenantA, actorId, request));
        verify(dealRepository, never()).save(any(Deal.class));
    }

    @Test
    void createRejectsDeletedAccount() {
        DealCreateRequest request = createRequest();
        request.setAccountId(UUID.randomUUID());
        stubMapperForCreate(request);

        assertRejected("ACCOUNT_NOT_FOUND", () -> service.createDeal(tenantA, actorId, request));
        verify(dealRepository, never()).save(any(Deal.class));
    }

    // --- Deal -> Contact ---

    @Test
    void createAcceptsSameTenantContactOnSameAccount() {
        UUID accountId = liveAccount(tenantA);
        DealCreateRequest request = createRequest();
        request.setAccountId(accountId);
        request.setContactId(liveContact(tenantA, accountId));
        stubMapperForCreate(request);

        assertThatNoException().isThrownBy(() -> service.createDeal(tenantA, actorId, request));
    }

    @Test
    void createRejectsCrossTenantContact() {
        UUID foreignContactId = UUID.randomUUID();
        Contact foreign = new Contact();
        foreign.setId(foreignContactId);
        foreign.setTenantId(tenantB);
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(foreignContactId, tenantB))
                .thenReturn(Optional.of(foreign));

        DealCreateRequest request = createRequest();
        request.setAccountId(liveAccount(tenantA));
        request.setContactId(foreignContactId);

        assertRejected("CONTACT_NOT_FOUND", () -> service.createDeal(tenantA, actorId, request));
        verify(dealRepository, never()).save(any(Deal.class));
    }

    @Test
    void createRejectsDeletedContact() {
        DealCreateRequest request = createRequest();
        request.setAccountId(liveAccount(tenantA));
        request.setContactId(UUID.randomUUID());
        stubMapperForCreate(request);

        assertRejected("CONTACT_NOT_FOUND", () -> service.createDeal(tenantA, actorId, request));
    }

    @Test
    void createRejectsContactBelongingToAnotherAccount() {
        UUID accountA = liveAccount(tenantA);
        UUID accountB = liveAccount(tenantA);

        DealCreateRequest request = createRequest();
        request.setAccountId(accountA);
        request.setContactId(liveContact(tenantA, accountB));

        assertRejected("DEAL_CONTACT_ACCOUNT_MISMATCH", () -> service.createDeal(tenantA, actorId, request));
        verify(dealRepository, never()).save(any(Deal.class));
    }

    // --- Deal -> Lead ---

    @Test
    void createAcceptsSameTenantLead() {
        DealCreateRequest request = createRequest();
        request.setLeadId(liveLead(tenantA));
        stubMapperForCreate(request);

        assertThatNoException().isThrownBy(() -> service.createDeal(tenantA, actorId, request));
    }

    @Test
    void createRejectsCrossTenantLead() {
        UUID foreignLeadId = UUID.randomUUID();
        Lead foreign = new Lead();
        foreign.setId(foreignLeadId);
        foreign.setTenantId(tenantB);
        when(leadRepository.findByIdAndTenantIdAndDeletedFalse(foreignLeadId, tenantB))
                .thenReturn(Optional.of(foreign));

        DealCreateRequest request = createRequest();
        request.setLeadId(foreignLeadId);

        assertRejected("LEAD_NOT_FOUND", () -> service.createDeal(tenantA, actorId, request));
        verify(dealRepository, never()).save(any(Deal.class));
    }

    @Test
    void createRejectsDeletedLead() {
        DealCreateRequest request = createRequest();
        request.setLeadId(UUID.randomUUID());
        stubMapperForCreate(request);

        assertRejected("LEAD_NOT_FOUND", () -> service.createDeal(tenantA, actorId, request));
    }

    // --- Deal -> Owner ---

    @Test
    void createAcceptsSameTenantOwner() {
        DealCreateRequest request = createRequest();
        request.setOwnerUserId(liveUser(tenantA));
        stubMapperForCreate(request);

        assertThatNoException().isThrownBy(() -> service.createDeal(tenantA, actorId, request));
    }

    @Test
    void createRejectsCrossTenantOwner() {
        UUID foreignOwnerId = UUID.randomUUID();
        User foreign = new User();
        foreign.setId(foreignOwnerId);
        foreign.setTenantId(tenantB);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(foreignOwnerId, tenantB))
                .thenReturn(Optional.of(foreign));

        DealCreateRequest request = createRequest();
        request.setOwnerUserId(foreignOwnerId);

        assertRejected("OWNER_NOT_FOUND", () -> service.createDeal(tenantA, actorId, request));
        verify(dealRepository, never()).save(any(Deal.class));
    }

    @Test
    void createDefaultsOwnerToActorWithoutOwnerLookup() {
        DealCreateRequest request = createRequest();
        stubMapperForCreate(request);

        assertThatNoException().isThrownBy(() -> service.createDeal(tenantA, actorId, request));
        verify(userRepository, never()).findByIdAndTenantIdAndDeletedFalse(any(), any());
    }

    // --- update: the resulting pair, not the supplied fields ---

    @Test
    void updateAcceptsConsistentAccountContactPair() {
        UUID accountId = liveAccount(tenantA);
        UUID contactId = liveContact(tenantA, accountId);
        Deal deal = existingDeal();
        deal.setAccountId(accountId);
        deal.setContactId(contactId);

        DealUpdateRequest request = DealUpdateRequest.builder()
                .name("Renamed")
                .accountId(accountId)
                .contactId(contactId)
                .build();

        assertThatNoException().isThrownBy(() -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    @Test
    void updateRejectsCrossTenantAccount() {
        UUID foreignAccountId = UUID.randomUUID();
        Account foreign = new Account();
        foreign.setId(foreignAccountId);
        foreign.setTenantId(tenantB);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(foreignAccountId, tenantB))
                .thenReturn(Optional.of(foreign));

        Deal deal = existingDeal();
        DealUpdateRequest request = DealUpdateRequest.builder().accountId(foreignAccountId).build();

        assertRejected("ACCOUNT_NOT_FOUND", () -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    @Test
    void updateRejectsDeletedAccount() {
        Deal deal = existingDeal();
        DealUpdateRequest request = DealUpdateRequest.builder().accountId(UUID.randomUUID()).build();

        assertRejected("ACCOUNT_NOT_FOUND", () -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    @Test
    void updateRejectsCrossTenantContact() {
        Deal deal = existingDeal();
        deal.setAccountId(liveAccount(tenantA));

        UUID foreignContactId = UUID.randomUUID();
        Contact foreign = new Contact();
        foreign.setId(foreignContactId);
        foreign.setTenantId(tenantB);
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(foreignContactId, tenantB))
                .thenReturn(Optional.of(foreign));

        DealUpdateRequest request = DealUpdateRequest.builder().contactId(foreignContactId).build();

        assertRejected("CONTACT_NOT_FOUND", () -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    @Test
    void updateRejectsDeletedContact() {
        Deal deal = existingDeal();
        deal.setAccountId(liveAccount(tenantA));
        DealUpdateRequest request = DealUpdateRequest.builder().contactId(UUID.randomUUID()).build();

        assertRejected("CONTACT_NOT_FOUND", () -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    @Test
    void updateRejectsContactBelongingToAnotherAccount() {
        UUID accountA = liveAccount(tenantA);
        UUID accountB = liveAccount(tenantA);
        Deal deal = existingDeal();
        deal.setAccountId(accountA);

        DealUpdateRequest request = DealUpdateRequest.builder()
                .contactId(liveContact(tenantA, accountB))
                .build();

        assertRejected("DEAL_CONTACT_ACCOUNT_MISMATCH", () -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    /**
     * The critical case: the request carries only an account change, and the
     * pre-existing contact would end up belonging to a different account. The
     * resulting pair must be validated, not the individual fields.
     */
    @Test
    void accountReassignmentThatWouldOrphanExistingContactIsRejected() {
        UUID originalAccount = liveAccount(tenantA);
        UUID newAccount = liveAccount(tenantA);
        UUID existingContact = liveContact(tenantA, originalAccount);

        Deal deal = existingDeal();
        deal.setAccountId(originalAccount);
        deal.setContactId(existingContact);

        DealUpdateRequest request = DealUpdateRequest.builder().accountId(newAccount).build();

        assertRejected("DEAL_CONTACT_ACCOUNT_MISMATCH", () -> service.updateDeal(deal.getId(), tenantA, actorId, request));
        verify(dealRepository, never()).save(any(Deal.class));
    }

    @Test
    void accountReassignmentIsAllowedWhenNoContactIsAttached() {
        UUID originalAccount = liveAccount(tenantA);
        UUID newAccount = liveAccount(tenantA);

        Deal deal = existingDeal();
        deal.setAccountId(originalAccount);

        DealUpdateRequest request = DealUpdateRequest.builder().accountId(newAccount).build();

        assertThatNoException().isThrownBy(() -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    @Test
    void updateRejectsCrossTenantOwner() {
        UUID foreignOwnerId = UUID.randomUUID();
        User foreign = new User();
        foreign.setId(foreignOwnerId);
        foreign.setTenantId(tenantB);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(foreignOwnerId, tenantB))
                .thenReturn(Optional.of(foreign));

        Deal deal = existingDeal();
        DealUpdateRequest request = DealUpdateRequest.builder().ownerUserId(foreignOwnerId).build();

        assertRejected("OWNER_NOT_FOUND", () -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    @Test
    void updateAcceptsSameTenantOwner() {
        UUID ownerId = liveUser(tenantA);
        Deal deal = existingDeal();
        DealUpdateRequest request = DealUpdateRequest.builder().ownerUserId(ownerId).build();

        assertThatNoException().isThrownBy(() -> service.updateDeal(deal.getId(), tenantA, actorId, request));
        assertThat(deal.getOwnerId()).isEqualTo(ownerId);
    }

    @Test
    void assignRejectsCrossTenantOwner() {
        UUID foreignOwnerId = UUID.randomUUID();
        User foreign = new User();
        foreign.setId(foreignOwnerId);
        foreign.setTenantId(tenantB);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(foreignOwnerId, tenantB))
                .thenReturn(Optional.of(foreign));
        Deal deal = existingDeal();

        assertRejected("OWNER_NOT_FOUND",
                () -> service.assignDeal(deal.getId(), tenantA, foreignOwnerId, actorId));
        verify(dealRepository, never()).save(any(Deal.class));
    }

    @Test
    void assignRejectsInactiveOwner() {
        UUID ownerId = UUID.randomUUID();
        User inactive = new User();
        inactive.setId(ownerId);
        inactive.setTenantId(tenantA);
        inactive.setIsActive(false);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(ownerId, tenantA))
                .thenReturn(Optional.of(inactive));
        Deal deal = existingDeal();

        assertRejected("OWNER_NOT_FOUND",
                () -> service.assignDeal(deal.getId(), tenantA, ownerId, actorId));
        verify(dealRepository, never()).save(any(Deal.class));
    }

    @Test
    void assignAcceptsLiveSameTenantOwner() {
        UUID ownerId = liveUser(tenantA);
        Deal deal = existingDeal();

        assertThatNoException().isThrownBy(
                () -> service.assignDeal(deal.getId(), tenantA, ownerId, actorId));
        assertThat(deal.getOwnerId()).isEqualTo(ownerId);
    }

    // --- deleted-aware live reads ---

    /**
     * getDealById resolves the caller from the SecurityContext, so this asserts
     * on assignDeal instead, which reaches the same tenant/deleted-scoped
     * lookup without requiring an authentication context.
     */
    @Test
    void liveReadExcludesSoftDeletedDeal() {
        UUID dealId = UUID.randomUUID();
        // The deletedFalse lookup returns nothing, so the record is not found.
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantA)).thenReturn(Optional.empty());

        assertThat(catchThrowable(() -> service.assignDeal(dealId, tenantA, actorId, actorId)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Deal not found");
    }
}