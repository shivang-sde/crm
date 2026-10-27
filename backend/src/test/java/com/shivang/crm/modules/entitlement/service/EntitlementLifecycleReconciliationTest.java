package com.shivang.crm.modules.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealLineItem;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.mapper.CustomerEntitlementMapper;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.0-P4-B: reconciliation of entitlements missed by Deals created directly in
 * CLOSED_WON before P4-A.
 *
 * L3.0-P4-G: renewal semantics under the existing entitlement uniqueness model.
 */
class EntitlementLifecycleReconciliationTest {

    private CustomerEntitlementRepository entitlementRepository;
    private DealRepository dealRepository;
    private DealLineItemRepository dealLineItemRepository;
    private OfferingRepository offeringRepository;
    private AccountRepository accountRepositoryRef;
    private UserRepository userRepositoryRef;
    private CustomerEntitlementService service;

    private UUID tenantId;
    private UUID actorId;
    private UUID dealId;

    @BeforeEach
    void setup() {
        entitlementRepository = mock(CustomerEntitlementRepository.class);
        dealRepository = mock(DealRepository.class);
        dealLineItemRepository = mock(DealLineItemRepository.class);
        offeringRepository = mock(OfferingRepository.class);
        accountRepositoryRef = mock(AccountRepository.class);
        userRepositoryRef = mock(UserRepository.class);

        UserRepository userRepository = userRepositoryRef;

        service = new CustomerEntitlementService(
                entitlementRepository, mock(CustomerEntitlementMapper.class),
                dealRepository, dealLineItemRepository, offeringRepository,
                mock(ActivityService.class), mock(RecordScopeGuard.class),
new CustomerRelationshipValidator(
      accountRepositoryRef, mock(ContactRepository.class), dealRepository,
      mock(LeadRepository.class), mock(TicketRepository.class), userRepository),
                mock(com.shivang.crm.shared.event.CanonicalCrmEventPublisher.class));

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        dealId = UUID.randomUUID();

        when(entitlementRepository.save(any(CustomerEntitlement.class))).thenAnswer(inv -> {
            CustomerEntitlement saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    private void stubLiveOwner() {
        var user = new com.shivang.crm.modules.auth.entity.User();
        user.setId(actorId);
        user.setTenantId(tenantId);
        when(userRepositoryRef.findByIdAndTenantIdAndDeletedFalse(actorId, tenantId))
                .thenReturn(Optional.of(user));
    }

    private Deal currentDeal;

    private void stubWonDealQuery(Deal deal) {
        currentDeal = deal;
        when(dealRepository.findByTenantIdAndRecordCategoryAndDeletedFalse(tenantId, RecordCategory.CLOSED_WON))
                .thenReturn(List.of(deal));
    }

    private void assertCode(String expected, ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    private UUID liveAccount(UUID tenant) {
        UUID id = UUID.randomUUID();
        var account = new com.shivang.crm.modules.account.entity.Account();
        account.setId(id);
        account.setTenantId(tenant);
        when(accountRepositoryRef.findByIdAndTenantIdAndDeletedFalse(id, tenant))
                .thenReturn(Optional.of(account));
        return id;
    }

    private Deal wonDeal(UUID accountId, DealLineItem lineItem) {
        DealStage wonStage = new DealStage();
        wonStage.setId(UUID.randomUUID());
        wonStage.setTenantId(tenantId);
        wonStage.setRecordCategory(RecordCategory.CLOSED_WON);

        Deal deal = new Deal();
        deal.setId(dealId);
        deal.setTenantId(tenantId);
        deal.setName("Won");
        deal.setStage(wonStage);
        deal.setAccountId(accountId);
        deal.setOwnerId(actorId);
        deal.setCreatedBy(actorId);

        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId))
                .thenReturn(Optional.of(deal));
        when(dealLineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of(lineItem));
        return deal;
    }

    private DealLineItem lineItemWithLiveOffering() {
        UUID offeringId = UUID.randomUUID();
        var offering = new com.shivang.crm.modules.catalog.entity.Offering();
        offering.setId(offeringId);
        offering.setTenantId(tenantId);
        offering.setName("Plan");
        when(offeringRepository.findByIdAndTenantIdAndDeletedFalse(offeringId, tenantId))
                .thenReturn(Optional.of(offering));

        DealLineItem li = new DealLineItem();
        li.setId(UUID.randomUUID());
        li.setTenantId(tenantId);
        li.setDealId(dealId);
        li.setOfferingId(offeringId);
        li.setItemName("Plan");
        li.setQuantity(java.math.BigDecimal.ONE);
        li.setUnitPrice(java.math.BigDecimal.TEN);
        li.setServiceStartDate(LocalDate.now().minusDays(1));
        li.setServiceEndDate(LocalDate.now().plusDays(30));
        return li;
    }

    private CustomerEntitlement existingEntitlement() {
        CustomerEntitlement entitlement = new CustomerEntitlement();
        entitlement.setId(UUID.randomUUID());
        return entitlement;
    }

    // --- P4-B: reconciliation ---

    @Test
    void reconciliationProvisionsMissingEntitlement() {
        stubLiveOwner();
        stubWonDealQuery(wonDeal(liveAccount(tenantId), lineItemWithLiveOffering()));
        when(entitlementRepository.findByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(List.of());
        when(entitlementRepository.existsByTenantIdAndDealLineItemIdAndDeletedFalse(any(), any()))
                .thenReturn(false);
        // Read once, after provisioning: the gap is only closed if it now reports one.
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(true);

        var report = service.reconcileMissingEntitlementsForWonDeals(tenantId, actorId);

        assertThat(report.reconciled()).containsExactly(dealId);
        assertThat(report.needsManualReview()).isEmpty();
        assertThat(report.isFullyReconciled()).isTrue();
        verify(entitlementRepository).save(any(CustomerEntitlement.class));
    }

    @Test
    void reconciliationIsIdempotentAndSkipsAlreadyProvisionedDeals() {
        stubWonDealQuery(wonDeal(liveAccount(tenantId), lineItemWithLiveOffering()));
        when(entitlementRepository.findByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(List.of(existingEntitlement()));

        assertThat(service.reconcileMissingEntitlementsForWonDeals(tenantId, actorId).reconciled()).isEmpty();
        verify(entitlementRepository, never()).save(any(CustomerEntitlement.class));
    }

    @Test
    void reconciliationSkipsWonDealsWithoutAnyCustomer() {
        stubWonDealQuery(wonDeal(null, lineItemWithLiveOffering()));
        when(entitlementRepository.findByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(List.of());

        var report = service.reconcileMissingEntitlementsForWonDeals(tenantId, actorId);

        assertThat(report.reconciled()).isEmpty();
        // Reported rather than silently dropped: the caller must be able to tell
        // a complete run from an incomplete one.
        assertThat(report.needsManualReview()).containsOnlyKeys(dealId);
        assertThat(report.needsManualReview().get(dealId))
                .contains("no account or contact");
        assertThat(report.isFullyReconciled()).isFalse();
        verify(entitlementRepository, never()).save(any(CustomerEntitlement.class));
    }

    @Test
    void reconciliationIsTenantScoped() {
        UUID otherTenant = UUID.randomUUID();
        wonDeal(UUID.randomUUID(), lineItemWithLiveOffering());
        when(dealRepository.findByTenantIdAndRecordCategoryAndDeletedFalse(any(), any()))
                .thenReturn(List.of());
        when(entitlementRepository.findByTenantIdAndDealIdAndDeletedFalse(any(), any()))
                .thenReturn(List.of());

        // Another tenant's won deals are simply not returned by the tenant-scoped query.
        assertThat(service.reconcileMissingEntitlementsForWonDeals(otherTenant, actorId).isFullyReconciled())
                .isTrue();
        verify(dealRepository).findByTenantIdAndRecordCategoryAndDeletedFalse(
                org.mockito.ArgumentMatchers.eq(otherTenant),
                org.mockito.ArgumentMatchers.eq(RecordCategory.CLOSED_WON));
    }

    @Test
    void reconciliationOfNoWonDealsIsANoOp() {
        when(dealRepository.findByTenantIdAndRecordCategoryAndDeletedFalse(tenantId, RecordCategory.CLOSED_WON))
                .thenReturn(List.of());

        assertThat(service.reconcileMissingEntitlementsForWonDeals(tenantId, actorId).isFullyReconciled())
                .isTrue();
        verify(entitlementRepository, never()).save(any(CustomerEntitlement.class));
    }

    @Test
    void reconciliationDoesNotRunOnStartupOrSchedule() {
        // Guards against a future change wiring reconciliation into application
        // lifecycle. Historical data must only change via an explicit call.
        assertThat(java.util.Arrays.stream(CustomerEntitlementService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .filteredOn(name -> name.toLowerCase().contains("reconcile"))
                .hasSize(1);
    }

    // --- P4-G: renewal semantics ---

    @Test
    void renewalMustUseANewCommercialSource() {
        assertCode("ENTITLEMENT_RENEWAL_REQUIRES_NEW_DEAL",
                () -> service.assertRenewalRequiresNewCommercialSource(dealId, dealId, tenantId));
    }

    @Test
    void renewalDealMustExistInTheSameTenant() {
        assertCode("NOT_FOUND",
                () -> service.assertRenewalRequiresNewCommercialSource(dealId, UUID.randomUUID(), tenantId));
    }

    @Test
    void renewalDealMustBeWon() {
        UUID renewalDealId = UUID.randomUUID();
        DealStage openStage = new DealStage();
        openStage.setId(UUID.randomUUID());
        openStage.setTenantId(tenantId);
        openStage.setRecordCategory(RecordCategory.OPEN);

        Deal renewalDeal = new Deal();
        renewalDeal.setId(renewalDealId);
        renewalDeal.setTenantId(tenantId);
        renewalDeal.setStage(openStage);
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(renewalDealId, tenantId))
                .thenReturn(Optional.of(renewalDeal));

        assertCode("ENTITLEMENT_RENEWAL_DEAL_NOT_WON",
                () -> service.assertRenewalRequiresNewCommercialSource(dealId, renewalDealId, tenantId));
    }

    @Test
    void renewalFromADistinctWonDealIsAccepted() {
        UUID renewalDealId = UUID.randomUUID();
        DealStage wonStage = new DealStage();
        wonStage.setId(UUID.randomUUID());
        wonStage.setTenantId(tenantId);
        wonStage.setRecordCategory(RecordCategory.CLOSED_WON);

        Deal renewalDeal = new Deal();
        renewalDeal.setId(renewalDealId);
        renewalDeal.setTenantId(tenantId);
        renewalDeal.setStage(wonStage);
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(renewalDealId, tenantId))
                .thenReturn(Optional.of(renewalDeal));

        assertThatNoException().isThrownBy(
                () -> service.assertRenewalRequiresNewCommercialSource(dealId, renewalDealId, tenantId));
    }

    /**
     * Locks the chosen semantics: one entitlement per line item, ever. A second
     * entitlement for the same line item is what option A (reuse) would require,
     * and it is what the uniqueness constraint forbids.
     */
    @Test
    void oneEntitlementPerDealLineItemIsTheEnforcedUniquenessModel() {
        DealLineItem li = lineItemWithLiveOffering();

        // The provisioning loop guards on this existence check before creating.
        when(entitlementRepository.existsByTenantIdAndDealLineItemIdAndDeletedFalse(any(), any()))
                .thenReturn(true);

        stubLiveOwner();
        wonDeal(liveAccount(tenantId), li);

        service.provisionFromWonDeal(tenantId, dealId, actorId);

        verify(entitlementRepository, never()).save(any(CustomerEntitlement.class));
    }

    /**
     * The entitlement is a copy of the line item's commercial position, so it
     * must carry the same currency rather than an independent (or absent) one.
     */
    @Test
    void entitlementInheritsTheLineItemCurrencySnapshot() {
        DealLineItem li = lineItemWithLiveOffering();
        li.setCurrencyCode("USD");
        stubLiveOwner();
        wonDeal(liveAccount(tenantId), li);
        when(entitlementRepository.existsByTenantIdAndDealLineItemIdAndDeletedFalse(any(), any()))
                .thenReturn(false);

        service.provisionFromWonDeal(tenantId, dealId, actorId);

        var captor = org.mockito.ArgumentCaptor.forClass(CustomerEntitlement.class);
        verify(entitlementRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void entitlementKeepsNullCurrencyRatherThanInventingOne() {
        DealLineItem li = lineItemWithLiveOffering();
        li.setCurrencyCode(null);
        stubLiveOwner();
        wonDeal(liveAccount(tenantId), li);
        when(entitlementRepository.existsByTenantIdAndDealLineItemIdAndDeletedFalse(any(), any()))
                .thenReturn(false);

        service.provisionFromWonDeal(tenantId, dealId, actorId);

        var captor = org.mockito.ArgumentCaptor.forClass(CustomerEntitlement.class);
        verify(entitlementRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrencyCode()).isNull();
    }

    /**
     * A Deal that cannot be safely provisioned is left alone and reported as
     * unreconciled rather than aborting the run or writing a half-built
     * entitlement.
     */
    @Test
    void unreconcilableDealIsLeftForManualReviewAndDoesNotAbortTheRun() {
        stubLiveOwner();
        // No live account for this deal's account id, so validation fails.
        stubWonDealQuery(wonDeal(UUID.randomUUID(), lineItemWithLiveOffering()));
        when(entitlementRepository.findByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(List.of());

        var report = service.reconcileMissingEntitlementsForWonDeals(tenantId, actorId);
        assertThat(report.reconciled()).isEmpty();
        assertThat(report.needsManualReview()).containsOnlyKeys(dealId);
        assertThat(report.isFullyReconciled()).isFalse();
        verify(entitlementRepository, never()).save(any(CustomerEntitlement.class));
    }

    @Test
    void oneBadDealDoesNotStopReconcilingTheOthers() {
        stubLiveOwner();
        Deal good = wonDeal(liveAccount(tenantId), lineItemWithLiveOffering());
        when(entitlementRepository.findByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(List.of());
        when(entitlementRepository.existsByTenantIdAndDealLineItemIdAndDeletedFalse(any(), any()))
                .thenReturn(false);
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(
                org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(dealId)))
                .thenReturn(true);

        Deal bad = new Deal();
        bad.setId(UUID.randomUUID());
        bad.setTenantId(tenantId);
        DealStage badStage = new DealStage();
        badStage.setId(UUID.randomUUID());
        badStage.setTenantId(tenantId);
        badStage.setRecordCategory(RecordCategory.CLOSED_WON);
        bad.setStage(badStage);
        bad.setAccountId(UUID.randomUUID());
        DealLineItem badLine = lineItemWithLiveOffering();
        badLine.setDealId(bad.getId());
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(bad.getId(), tenantId))
                .thenReturn(Optional.of(bad));
        when(dealLineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, bad.getId()))
                .thenReturn(List.of(badLine));

        when(dealRepository.findByTenantIdAndRecordCategoryAndDeletedFalse(tenantId, RecordCategory.CLOSED_WON))
                .thenReturn(List.of(bad, good));

        var report = service.reconcileMissingEntitlementsForWonDeals(tenantId, actorId);
        assertThat(report.reconciled()).containsExactly(good.getId());
        assertThat(report.needsManualReview()).containsOnlyKeys(bad.getId());
        assertThat(report.isFullyReconciled()).isFalse();
    }

    /**
     * A won Deal with no line items cannot be provisioned. Reporting it as
     * reconciled would be a lie, because provisionFromWonDeal returns silently.
     */
    @Test
    void wonDealWithNoLineItemsIsReportedForManualReviewNotReconciled() {
        stubLiveOwner();
        Deal deal = wonDeal(liveAccount(tenantId), lineItemWithLiveOffering());
        stubWonDealQuery(deal);
        when(dealLineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of());
        when(entitlementRepository.findByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(List.of());

        var report = service.reconcileMissingEntitlementsForWonDeals(tenantId, actorId);

        assertThat(report.reconciled()).isEmpty();
        assertThat(report.needsManualReview().get(dealId)).contains("no active line items");
        verify(entitlementRepository, never()).save(any(CustomerEntitlement.class));
    }

    @Test
    void entitlementStatusModelSupportsTerminalCoverageForRenewal() {
        // RENEWED exists in the status enum precisely because renewal replaces a
        // coverage period rather than extending the original row.
        assertThat(EntitlementStatus.values())
                .contains(EntitlementStatus.RENEWED,
                        EntitlementStatus.ACTIVE,
                        EntitlementStatus.EXPIRED,
                        EntitlementStatus.TERMINATED);
    }
}