package com.shivang.crm.modules.entitlement.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.mapper.CustomerEntitlementMapper;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.0-P2: CustomerEntitlement relationship integrity.
 *
 * An entitlement snapshots the Deal's customer references, so provisioning must
 * verify that those references are live, tenant-scoped, and mutually consistent
 * at provisioning time rather than trusting whatever was valid when the Deal
 * was written.
 */
class CustomerEntitlementRelationshipIntegrityTest {

    private CustomerEntitlementRepository entitlementRepository;
    private DealRepository dealRepository;
    private DealLineItemRepository dealLineItemRepository;
    private OfferingRepository offeringRepository;
    private AccountRepository accountRepository;
    private ContactRepository contactRepository;
    private UserRepository userRepository;
    private CustomerRelationshipValidator validator;
    private CustomerEntitlementService service;

    private UUID tenantA;
    private UUID tenantB;
    private UUID actorId;
    private Deal deal;
    private DealLineItemStub lineItem;

    private record DealLineItemStub(com.shivang.crm.modules.deal.entity.DealLineItem entity, UUID offeringId) {
    }

    @BeforeEach
    void setup() {
        entitlementRepository = mock(CustomerEntitlementRepository.class);
        dealRepository = mock(DealRepository.class);
        dealLineItemRepository = mock(DealLineItemRepository.class);
        offeringRepository = mock(OfferingRepository.class);
        accountRepository = mock(AccountRepository.class);
        contactRepository = mock(ContactRepository.class);
        userRepository = mock(UserRepository.class);

validator = new CustomerRelationshipValidator(
      accountRepository, contactRepository, dealRepository,
      mock(LeadRepository.class), mock(TicketRepository.class), userRepository);

        service = new CustomerEntitlementService(
                entitlementRepository,
                mock(CustomerEntitlementMapper.class),
                dealRepository,
                dealLineItemRepository,
                offeringRepository,
                mock(ActivityService.class),
                mock(RecordScopeGuard.class),
                validator,
                mock(com.shivang.crm.shared.event.CanonicalCrmEventPublisher.class));

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        actorId = UUID.randomUUID();

        DealStage wonStage = new DealStage();
        wonStage.setId(UUID.randomUUID());
        wonStage.setTenantId(tenantA);
        wonStage.setName("Closed Won");
        wonStage.setRecordCategory(RecordCategory.CLOSED_WON);

        deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setTenantId(tenantA);
        deal.setName("Won Deal");
        deal.setStage(wonStage);
        deal.setCreatedBy(actorId);

        // The acting user is a live tenantA user by default; individual tests
        // override deal.setOwnerId to model a cross-tenant owner.
        User actor = new User();
        actor.setId(actorId);
        actor.setTenantId(tenantA);
        actor.setIsActive(true);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(actorId, tenantA))
                .thenReturn(Optional.of(actor));
        deal.setOwnerId(actorId);

        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(deal.getId(), tenantA))
                .thenReturn(Optional.of(deal));

        lineItem = liveLineItem();
        when(dealLineItemRepository
                .findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantA, deal.getId()))
                .thenReturn(List.of(lineItem.entity()));
        when(entitlementRepository.existsByTenantIdAndDealLineItemIdAndDeletedFalse(any(), any()))
                .thenReturn(false);
        when(entitlementRepository.save(any(CustomerEntitlement.class))).thenAnswer(invocation -> {
            CustomerEntitlement saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        // A valid same-tenant Account by default; individual tests override it to
        // model a cross-tenant, deleted, or absent parent.
        deal.setAccountId(liveAccountIn(tenantA));
    }

    private void assertCode(String expectedCode, ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        org.assertj.core.api.Assertions.assertThat(thrown).isInstanceOf(BusinessException.class);
        org.assertj.core.api.Assertions
                .assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expectedCode);
    }

    // --- fixtures ---

    private UUID liveAccountIn(UUID tenant) {
        UUID id = UUID.randomUUID();
        Account account = new Account();
        account.setId(id);
        account.setTenantId(tenant);
        account.setName("Acme");
        // Registered only for the requested tenant: a caller's lookup under any
        // other tenant finds nothing, which is how a cross-tenant reference fails.
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(id, tenant)).thenReturn(Optional.of(account));
        return id;
    }

    private UUID liveContactIn(UUID tenant, UUID accountId) {
        UUID id = UUID.randomUUID();
        Contact contact = new Contact();
        contact.setId(id);
        contact.setTenantId(tenant);
        contact.setAccountId(accountId);
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(id, tenant)).thenReturn(Optional.of(contact));
        return id;
    }

    private UUID liveUserIn(UUID tenant) {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setTenantId(tenant);
        user.setIsActive(true);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(id, tenant)).thenReturn(Optional.of(user));
        return id;
    }

    private UUID liveOfferingIn(UUID tenant) {
        UUID id = UUID.randomUUID();
        Offering offering = new Offering();
        offering.setId(id);
        offering.setTenantId(tenant);
        offering.setName("Support Plan");
        when(offeringRepository.findByIdAndTenantIdAndDeletedFalse(id, tenant)).thenReturn(Optional.of(offering));
        return id;
    }

    private DealLineItemStub liveLineItem() {
        UUID offeringId = liveOfferingIn(tenantA);
        com.shivang.crm.modules.deal.entity.DealLineItem item =
                new com.shivang.crm.modules.deal.entity.DealLineItem();
        item.setId(UUID.randomUUID());
        item.setTenantId(tenantA);
        item.setDealId(deal.getId());
        item.setOfferingId(offeringId);
        item.setItemName("Support Plan");
        item.setQuantity(java.math.BigDecimal.ONE);
        item.setUnitPrice(java.math.BigDecimal.TEN);
        item.setServiceStartDate(LocalDate.now().minusDays(1));
        item.setServiceEndDate(LocalDate.now().plusDays(30));
        return new DealLineItemStub(item, offeringId);
    }

    // --- happy path ---

    @Test
    void validRelationshipProvisions() {
        UUID accountId = liveAccountIn(tenantA);
        deal.setAccountId(accountId);
        deal.setContactId(liveContactIn(tenantA, accountId));
        deal.setOwnerId(liveUserIn(tenantA));

        assertThatNoException().isThrownBy(() -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    // --- Deal ---

    @Test
    void softDeletedDealIsNotFound() {
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(deal.getId(), tenantA))
                .thenReturn(Optional.empty());

        assertCode("NOT_FOUND", () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    @Test
    void crossTenantDealIsNotFound() {
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(deal.getId(), tenantA))
                .thenReturn(Optional.empty());

        assertCode("NOT_FOUND", () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    // --- Deal -> Account ---

    @Test
    void crossTenantAccountRejected() {
        deal.setAccountId(liveAccountIn(tenantB));

        assertCode("ACCOUNT_NOT_FOUND", () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    @Test
    void deletedAccountRejected() {
        deal.setAccountId(UUID.randomUUID());

        assertCode("ACCOUNT_NOT_FOUND", () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    // --- Deal -> Contact ---

    @Test
    void crossTenantContactRejected() {
        UUID accountId = liveAccountIn(tenantA);
        deal.setAccountId(accountId);
        deal.setContactId(liveContactIn(tenantB, accountId));

        assertCode("CONTACT_NOT_FOUND", () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    @Test
    void deletedContactRejected() {
        deal.setAccountId(liveAccountIn(tenantA));
        deal.setContactId(UUID.randomUUID());

        assertCode("CONTACT_NOT_FOUND", () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    @Test
    void inconsistentDealContactAccountRejected() {
        UUID accountA = liveAccountIn(tenantA);
        UUID accountB = liveAccountIn(tenantA);
        deal.setAccountId(accountA);
        deal.setContactId(liveContactIn(tenantA, accountB));

        assertCode("DEAL_CONTACT_ACCOUNT_MISMATCH",
                () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    // --- Offering ---

    @Test
    void crossTenantOfferingRejected() {
        // An offering that exists only for tenantB; the tenantA lookup finds nothing.
        UUID foreignOfferingId = liveOfferingIn(tenantB);
        lineItem.entity().setOfferingId(foreignOfferingId);

        assertCode("OFFERING_NOT_FOUND", () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    @Test
    void deletedOfferingRejected() {
        // deletedFalse lookup returns empty.
        lineItem.entity().setOfferingId(UUID.randomUUID());

        assertCode("OFFERING_NOT_FOUND", () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    @Test
    void missingOfferingReferenceRejected() {
        lineItem.entity().setOfferingId(null);

        assertCode("OFFERING_NOT_FOUND", () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    // --- Owner ---

    @Test
    void crossTenantOwnerRejected() {
        deal.setOwnerId(liveUserIn(tenantB));

        assertCode("OWNER_NOT_FOUND", () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    @Test
    void wonDealWithoutAnyCustomerIsRejected() {
        deal.setAccountId(null);
        deal.setContactId(null);

        assertCode("ENTITLEMENT_NO_CUSTOMER", () -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }

    @Test
    void nonWonDealIsSkippedBeforeAnyRelationshipCheck() {
        DealStage openStage = new DealStage();
        openStage.setId(UUID.randomUUID());
        openStage.setTenantId(tenantA);
        openStage.setRecordCategory(RecordCategory.OPEN);
        deal.setStage(openStage);

        assertThatNoException().isThrownBy(() -> service.provisionFromWonDeal(tenantA, deal.getId(), actorId));
    }
}