package com.shivang.crm.modules.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.dto.DealCreateRequest;
import com.shivang.crm.modules.deal.dto.DealLineItemCreateRequest;
import com.shivang.crm.modules.deal.dto.DealLineItemResponse;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.mapper.DealLineItemMapper;
import com.shivang.crm.modules.deal.mapper.DealMapper;
import com.shivang.crm.modules.deal.repository.DealCustomFieldRepository;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.deal.repository.DealStageRepository;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.entitlement.service.EntitlementProvisioningService;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.lead.service.EntityHistoryService;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.0-P5: Deal creation carrying its initial line items, in one transaction.
 *
 * <p>DealLineItemService is deliberately a real object here rather than a mock, so
 * that the nested path is exercised through the same validation a standalone line
 * item goes through. Everything outward of that (repositories, provisioning) is
 * mocked so the ordering and the failure propagation can be asserted precisely.
 */
class DealAtomicCreationTest {

    private DealRepository dealRepository;
    private DealStageRepository stageRepository;
    private DealLineItemRepository lineItemRepository;
    private OfferingRepository offeringRepository;
    private AccountRepository accountRepository;
    private CustomerRelationshipValidator validator;
    private EntitlementProvisioningService provisioningService;
    private DealService dealService;
    private DealLineItemService lineItemService;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID actorId;
    private UUID dealId;
    private UUID accountId;
private DealStage openStage;
    private DealStage wonStage;
    private DealStage lostStage;
    private List<RecordCategory> savedCategories;

    @BeforeEach
    void setup() {
        dealRepository = mock(DealRepository.class);
        stageRepository = mock(DealStageRepository.class);
        lineItemRepository = mock(DealLineItemRepository.class);
        offeringRepository = mock(OfferingRepository.class);
        accountRepository = mock(AccountRepository.class);
        provisioningService = mock(EntitlementProvisioningService.class);
        validator = mock(CustomerRelationshipValidator.class);

        DealMapper dealMapper = mock(DealMapper.class);
        DealLineItemMapper lineItemMapper = mock(DealLineItemMapper.class);
        ActivityService activityService = mock(ActivityService.class);

        lineItemService = new DealLineItemService(
                lineItemRepository, dealRepository, offeringRepository,
                lineItemMapper, activityService, mock(RecordScopeGuard.class),
                mock(com.shivang.crm.shared.event.CanonicalCrmEventPublisher.class));

        dealService = new DealService(
                dealRepository,
                mock(DealCustomFieldRepository.class),
                lineItemRepository,
                stageRepository,
                lineItemService,
                mock(CustomerEntitlementRepository.class),
                mock(CanonicalCrmEventPublisher.class),
                dealMapper,
                activityService,
                mock(EntityHistoryService.class),
                provisioningService,
                mock(UserRepository.class),
                mock(PermissionEvaluatorService.class),
                mock(RecordScopeGuard.class),
                validator);

tenantId = UUID.randomUUID();
        otherTenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        savedCategories = new ArrayList<>();

        openStage = stage(RecordCategory.OPEN, true);
        wonStage = stage(RecordCategory.CLOSED_WON, false);
        lostStage = stage(RecordCategory.CLOSED_LOST, false);

        when(stageRepository.findByIdAndTenantId(any(), eq(tenantId))).thenReturn(Optional.empty());
        when(stageRepository.findByTenantIdAndIsDefault(tenantId, true)).thenReturn(Optional.of(openStage));
        when(stageRepository.findByIdAndTenantId(openStage.getId(), tenantId)).thenReturn(Optional.of(openStage));
        when(stageRepository.findByIdAndTenantId(wonStage.getId(), tenantId)).thenReturn(Optional.of(wonStage));
        when(stageRepository.findByIdAndTenantId(lostStage.getId(), tenantId)).thenReturn(Optional.of(lostStage));

        when(dealMapper.toEntity(any(DealCreateRequest.class))).thenAnswer(inv -> {
            DealCreateRequest request = inv.getArgument(0);
            Deal deal = new Deal();
            deal.setName(request.getName());
            deal.setAmount(request.getAmount());
            deal.setProbability(request.getProbability());
            return deal;
        });

// One reference standing in for the persistence context's first-level cache:
// whatever was last saved is what a subsequent read sees, which is how JPA
// behaves inside one transaction.
java.util.concurrent.atomic.AtomicReference<Deal> stored = new java.util.concurrent.atomic.AtomicReference<>();

        when(dealRepository.save(any(Deal.class))).thenAnswer(inv -> {
            Deal deal = inv.getArgument(0);
            if (deal.getId() == null) {
                deal.setId(dealId);
            }
            deal.setUpdatedAt(java.time.Instant.now());
            savedCategories.add(deal.getRecordCategory());
            stored.set(deal);
            return deal;
        });
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId))
                .thenAnswer(inv -> Optional.of(stored.get()));
        when(lineItemRepository.sumLineTotalsByTenantIdAndDealIdAndDeletedFalse(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(lineItemRepository.save(any())).thenAnswer(inv -> {
            com.shivang.crm.modules.deal.entity.DealLineItem line = inv.getArgument(0);
            if (line.getId() == null) {
                line.setId(UUID.randomUUID());
            }
            if (line.getLineTotal() == null) {
                line.setLineTotal(BigDecimal.ZERO);
            }
            return line;
        });
        when(lineItemMapper.toResponse(any())).thenAnswer(inv -> DealLineItemResponse.builder().build());

        Account account = new Account();
        account.setId(accountId);
        account.setTenantId(tenantId);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(accountId, tenantId))
                .thenReturn(Optional.of(account));
    }

    // --- helpers ---

    private DealStage stage(RecordCategory category, boolean isDefault) {
        DealStage s = new DealStage();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setName(category.name());
        s.setRecordCategory(category);
        s.setIsDefault(isDefault);
        s.setDefaultProbability(category == RecordCategory.CLOSED_WON ? 100 : 50);
        return s;
    }

    private Offering liveOffering(String currency) {
        Offering offering = new Offering();
        offering.setId(UUID.randomUUID());
        offering.setTenantId(tenantId);
        offering.setName("Plan");
        offering.setCode("PLAN");
        offering.setOfferingType(OfferingType.SUBSCRIPTION);
        offering.setBillingType(BillingType.ONE_TIME);
        offering.setActive(true);
        offering.setCurrencyCode(currency);
        offering.setDefaultPrice(new BigDecimal("1000.00"));
        when(offeringRepository.findByIdAndTenantIdAndDeletedFalse(offering.getId(), tenantId))
                .thenReturn(Optional.of(offering));
        return offering;
    }

    private DealLineItemCreateRequest line(UUID offeringId, String quantity, String unitPrice, String tax) {
        DealLineItemCreateRequest request = new DealLineItemCreateRequest();
        request.setOfferingId(offeringId);
        request.setQuantity(new BigDecimal(quantity));
        request.setUnitPrice(new BigDecimal(unitPrice));
        request.setTaxAmount(new BigDecimal(tax));
        return request;
    }

    private DealCreateRequest request(DealStage stage, DealLineItemCreateRequest... lines) {
        DealCreateRequest request = DealCreateRequest.builder().name("Deal").build();
        if (stage != null) {
            request.setStageId(stage.getId());
        }
        request.setAccountId(accountId);
        request.setOwnerUserId(actorId);
        if (lines.length > 0) {
            request.setLineItems(List.of(lines));
        }
        return request;
    }

    private void code(Throwable thrown, String expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    // --- OPEN deals: unchanged behaviour ---

    @Test
    void openDealWithInitialLineItemsIsCreated() {
        Offering offering = liveOffering("USD");

dealService.createDeal(tenantId, actorId, request(openStage, line(offering.getId(), "2", "1000.00", "180.00")));

        // Recalculating the parent amount re-saves the Deal, so the Deal row is
        // written more than once; what matters is that it and the line land together.
        verify(dealRepository, atLeastOnce()).save(any(Deal.class));
        verify(lineItemRepository).save(any());
        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
    }

    @Test
    void openDealWithoutLineItemsStillWorks() {
        dealService.createDeal(tenantId, actorId, request(openStage));

        verify(dealRepository).save(any(Deal.class));
        verify(lineItemRepository, never()).save(any());
        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
    }

    @Test
    void openDealWithSeveralInitialLineItemsCreatesAllOfThem() {
        Offering first = liveOffering("USD");
        Offering second = liveOffering("EUR");

        dealService.createDeal(tenantId, actorId, request(openStage,
                line(first.getId(), "2", "1000.00", "180.00"),
                line(second.getId(), "1", "500.00", "0.00")));

        verify(lineItemRepository, times(2)).save(any());
    }

    // --- CLOSED_WON: born complete ---

    @Test
    void closedWonDealWithInitialLineItemsIsCreatedAndProvisions() {
        Offering offering = liveOffering("USD");

        dealService.createDeal(tenantId, actorId, request(wonStage,
                line(offering.getId(), "2", "1000.00", "180.00")));

        verify(lineItemRepository).save(any());
        verify(provisioningService).provisionFromWonDeal(tenantId, dealId, actorId);
    }

    @Test
    void linesAreCreatedBeforeEntitlementsAreProvisioned() {
        // Ordering is the whole point: provisioning derives entitlements from the
        // lines that exist when it runs, so it must not run first.
        Offering offering = liveOffering("USD");

        dealService.createDeal(tenantId, actorId, request(wonStage,
                line(offering.getId(), "2", "1000.00", "180.00")));

        InOrder order = Mockito.inOrder(lineItemRepository, provisioningService, dealRepository);
        order.verify(dealRepository).save(any(Deal.class));
        order.verify(lineItemRepository).save(any());
        order.verify(provisioningService).provisionFromWonDeal(tenantId, dealId, actorId);
    }

    @Test
    void closedWonDealProvisionsAfterEveryInitialLineItem() {
        Offering first = liveOffering("USD");
        Offering second = liveOffering("USD");

        dealService.createDeal(tenantId, actorId, request(wonStage,
                line(first.getId(), "1", "1000.00", "0.00"),
                line(second.getId(), "1", "250.00", "0.00")));

        InOrder order = Mockito.inOrder(lineItemRepository, provisioningService);
        order.verify(lineItemRepository).save(any());
        order.verify(lineItemRepository).save(any());
        order.verify(provisioningService).provisionFromWonDeal(tenantId, dealId, actorId);
    }

    @Test
    void closedWonDealIsPersistedInItsRequestedStateWithoutAnIntermediateOpenStage() {
        // The Deal row must be written already won. Simulating
        // OPEN -> add lines -> CLOSED_WON would mean an intermediate commit that
        // other readers could observe, which is what P5 exists to avoid.
        Offering offering = liveOffering("USD");

        dealService.createDeal(tenantId, actorId, request(wonStage,
                line(offering.getId(), "1", "1000.00", "0.00")));

// Every write of the Deal row carried the won category. Simulating
        // OPEN -> add lines -> CLOSED_WON would show OPEN here, and would mean an
        // intermediate commit another reader could observe.
        assertThat(savedCategories).isNotEmpty();
        assertThat(savedCategories).allMatch(RecordCategory.CLOSED_WON::equals);
    }

    @Test
    void anOpenDealIsOnlyEverWrittenAsOpen() {
        Offering offering = liveOffering("USD");

        dealService.createDeal(tenantId, actorId, request(openStage,
                line(offering.getId(), "1", "1000.00", "0.00")));

        assertThat(savedCategories).isNotEmpty();
        assertThat(savedCategories).allMatch(RecordCategory.OPEN::equals);
    }

    // --- Section 10: CLOSED_WON with no lines ---

    @Test
    void closedWonDealWithoutLineItemsIsRejected() {
        code(catchThrowable(() -> dealService.createDeal(tenantId, actorId, request(wonStage))),
                "DEAL_WON_REQUIRES_LINE_ITEMS");
    }

    @Test
    void closedWonDealWithAnEmptyLineItemListIsRejected() {
        DealCreateRequest request = request(wonStage);
        request.setLineItems(List.of());

        code(catchThrowable(() -> dealService.createDeal(tenantId, actorId, request)),
                "DEAL_WON_REQUIRES_LINE_ITEMS");
    }

    @Test
    void rejectingAnIncompleteWonDealWritesNothingAtAll() {
        code(catchThrowable(() -> dealService.createDeal(tenantId, actorId, request(wonStage))),
                "DEAL_WON_REQUIRES_LINE_ITEMS");

        verify(dealRepository, never()).save(any());
        verify(lineItemRepository, never()).save(any());
        verifyNoInteractions(provisioningService);
    }

    @Test
    void closedLostDealWithoutLineItemsIsStillAllowed() {
        // A lost deal has no commercial coverage to provision, so it does not need
        // lines. Only a won Deal is constrained.
        DealCreateRequest request = request(lostStage);
        request.setLostReason("Price");

        dealService.createDeal(tenantId, actorId, request);

        verify(dealRepository).save(any(Deal.class));
        verify(lineItemRepository, never()).save(any());
        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
    }

    // --- Section 19: rollback ---

    @Test
    void anInvalidFirstLineRollsBackTheWholeCreation() {
        // quantity must be greater than zero
        Offering offering = liveOffering("USD");
        DealLineItemCreateRequest invalid = line(offering.getId(), "0", "1000.00", "0.00");

        Throwable thrown = catchThrowable(() ->
                dealService.createDeal(tenantId, actorId, request(openStage, invalid)));

        code(thrown, "INVALID_QUANTITY");
        verify(lineItemRepository, never()).save(any());
        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
    }

    @Test
    void anInvalidSecondLineRollsBackTheFirstOne() {
        // This is the partial-commercial-state case: line one is valid and would
        // be written, line two is not. The exception must escape createDeal so the
        // surrounding transaction discards the Deal and line one together.
        Offering first = liveOffering("USD");
        Offering second = liveOffering("USD");

        Throwable thrown = catchThrowable(() -> dealService.createDeal(tenantId, actorId, request(openStage,
                line(first.getId(), "2", "1000.00", "180.00"),
                line(second.getId(), "0", "500.00", "0.00"))));

        code(thrown, "INVALID_QUANTITY");

        // One line was saved before the failure; only the transaction boundary
        // guarantees it does not survive. Assert the boundary holds, below.
        verify(lineItemRepository, times(1)).save(any());
        assertThatThrownPropagatesOutOfCreateDeal(thrown);
    }

    @Test
    void anInvalidLineOnAWonDealPreventsProvisioningEntirely() {
        Offering offering = liveOffering("USD");
        DealLineItemCreateRequest invalid = line(offering.getId(), "1", "1000.00", "0.00");
        // discount above subtotal
        invalid.setDiscountAmount(new BigDecimal("5000.00"));

        Throwable thrown = catchThrowable(() -> dealService.createDeal(tenantId, actorId, request(wonStage, invalid)));

        code(thrown, "INVALID_DISCOUNT");
        // A won Deal must never reach provisioning without complete lines, or it
        // would be born half-sold.
        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
        verify(lineItemRepository, never()).save(any());
    }

    @Test
    void aFailureRaisedByProvisioningEscapesCreateDealSoNothingCommits() {
        Offering offering = liveOffering("USD");
        org.mockito.Mockito.doThrow(new BusinessException("ENTITLEMENT_NO_CUSTOMER", "boom"))
                .when(provisioningService).provisionFromWonDeal(any(), any(), any());

        Throwable thrown = catchThrowable(() -> dealService.createDeal(tenantId, actorId, request(wonStage,
                line(offering.getId(), "1", "1000.00", "0.00"))));

        code(thrown, "ENTITLEMENT_NO_CUSTOMER");
        verify(lineItemRepository).save(any());
        assertThatThrownPropagatesOutOfCreateDeal(thrown);
    }

    @Test
    void noOrphanEntitlementsAreRequestedWhenCreationFailsBeforeProvisioning() {
        Offering offering = liveOffering("USD");
        DealLineItemCreateRequest invalid = line(offering.getId(), "-1", "1000.00", "0.00");

        catchThrowable(() -> dealService.createDeal(tenantId, actorId, request(wonStage, invalid)));

        verifyNoInteractions(provisioningService);
    }

    /**
     * Without a runnable database the rollback itself cannot be observed, so the
     * two things that make it happen are asserted directly: the failure escapes
     * the transaction boundary, and the boundary declares default rollback.
     */
    private void assertThatThrownPropagatesOutOfCreateDeal(Throwable thrown) {
        assertThat(thrown).isInstanceOf(RuntimeException.class);
        // BusinessException is a RuntimeException, which is what Spring's default
        // rollback rule keys on.
        assertThat(RuntimeException.class).isAssignableFrom(thrown.getClass());
    }

@Test
    void createDealIsTransactionalWithDefaultRollback() {
        // The annotation is on the class, which is what puts createDeal inside a
        // transaction at all.
        Transactional classLevel = DealService.class.getAnnotation(Transactional.class);
        assertThat(classLevel).isNotNull();
        assertThat(classLevel.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(classLevel.rollbackFor()).isEmpty();
        assertThat(classLevel.noRollbackFor()).isEmpty();

        // And the method must not opt out of it.
        Method createDeal = assertDoesNotThrow(() -> DealService.class.getMethod(
                "createDeal", UUID.class, UUID.class, DealCreateRequest.class));
        Transactional onMethod = createDeal.getAnnotation(Transactional.class);
        assertThat(onMethod == null || onMethod.propagation() == Propagation.REQUIRED)
                .as("createDeal must not narrow or widen the transaction")
                .isTrue();
        if (onMethod != null) {
            assertThat(onMethod.noRollbackFor()).isEmpty();
        }
    }

    @Test
    void theNestedLineItemPathJoinsRatherThanStartsItsOwnTransaction() {
        // persistLineItem inherits the class-level @Transactional (REQUIRED), so it
        // joins the Deal creation transaction instead of committing on its own.
        Transactional classLevel = DealLineItemService.class.getAnnotation(Transactional.class);
        assertThat(classLevel).isNotNull();
        assertThat(classLevel.propagation()).isEqualTo(Propagation.REQUIRED);

        Method persist = assertDoesNotThrow(() -> DealLineItemService.class.getDeclaredMethod(
                "persistLineItem", UUID.class, UUID.class, UUID.class, DealLineItemCreateRequest.class));
        Transactional onMethod = persist.getAnnotation(Transactional.class);
        assertThat(onMethod == null || onMethod.propagation() == Propagation.REQUIRED)
                .as("the nested line path must not start an independent transaction")
                .isTrue();
    }

    @Test
    void theNestedPathIsPackagePrivateSoOnlyDealCreationCanReachIt() {
        Method persist = assertDoesNotThrow(() -> DealLineItemService.class.getDeclaredMethod(
                "persistLineItem", UUID.class, UUID.class, UUID.class, DealLineItemCreateRequest.class));

        int modifiers = persist.getModifiers();
        boolean reachableOutsidePackage = java.lang.reflect.Modifier.isPublic(modifiers)
                || java.lang.reflect.Modifier.isProtected(modifiers);
        assertThat(reachableOutsidePackage)
                .as("persistLineItem bypasses authorization, so it must not be publicly callable")
                .isFalse();
    }

    // --- tenant isolation ---

    @Test
    void anOfferingFromAnotherTenantIsRejected() {
        UUID foreignOfferingId = UUID.randomUUID();
        when(offeringRepository.findByIdAndTenantIdAndDeletedFalse(foreignOfferingId, tenantId))
                .thenReturn(Optional.empty());

        code(catchThrowable(() -> dealService.createDeal(tenantId, actorId, request(wonStage,
                line(foreignOfferingId, "1", "1000.00", "0.00")))), "OFFERING_NOT_FOUND");

        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
    }

    @Test
    void anInactiveOfferingIsRejected() {
        Offering inactive = liveOffering("USD");
        inactive.setActive(false);

        code(catchThrowable(() -> dealService.createDeal(tenantId, actorId, request(wonStage,
                line(inactive.getId(), "1", "1000.00", "0.00")))), "OFFERING_INACTIVE");
    }

    @Test
    void customerRelationshipValidationRunsBeforeAnythingIsWritten() {
        // A foreign account must be refused by the shared P2 validator, before the
        // nested path has written a single row.
        org.mockito.Mockito.doThrow(new BusinessException("ACCOUNT_NOT_FOUND", "nope"))
                .when(validator).requireLiveAccount(accountId, tenantId);

        code(catchThrowable(() -> dealService.createDeal(tenantId, actorId, request(wonStage,
                line(liveOffering("USD").getId(), "1", "1000.00", "0.00")))), "ACCOUNT_NOT_FOUND");

        verify(dealRepository, never()).save(any());
        verify(lineItemRepository, never()).save(any());
        verifyNoInteractions(provisioningService);
    }

    @Test
    void aForeignOwnerIsRejected() {
        org.mockito.Mockito.doThrow(new BusinessException("OWNER_NOT_FOUND", "nope"))
                .when(validator).requireTenantOwner(actorId, tenantId);

        code(catchThrowable(() -> dealService.createDeal(tenantId, actorId, request(wonStage,
                line(liveOffering("USD").getId(), "1", "1000.00", "0.00")))), "OWNER_NOT_FOUND");

        verify(dealRepository, never()).save(any());
    }

    // --- currency: Offering is the authority ---

    @Test
    void aClientSuppliedCurrencyCannotOverrideTheOffering() {
        // DealLineItemCreateRequest has no currencyCode field at all, so there is
        // nothing for a client to set. The snapshot comes from the Offering.
        Offering offering = liveOffering("GBP");
        DealLineItemCreateRequest request = line(offering.getId(), "1", "1000.00", "0.00");
        request.setDescription("Annual");

        dealService.createDeal(tenantId, actorId, request(wonStage, request));

        org.mockito.ArgumentCaptor<com.shivang.crm.modules.deal.entity.DealLineItem> captor =
                org.mockito.ArgumentCaptor.forClass(com.shivang.crm.modules.deal.entity.DealLineItem.class);
        verify(lineItemRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrencyCode()).isEqualTo("GBP");
    }

    @Test
    void anOfferingWithoutACurrencyProducesANullSnapshot() {
        Offering offering = liveOffering(null);

        dealService.createDeal(tenantId, actorId, request(wonStage,
                line(offering.getId(), "1", "1000.00", "0.00")));

        org.mockito.ArgumentCaptor<com.shivang.crm.modules.deal.entity.DealLineItem> captor =
                org.mockito.ArgumentCaptor.forClass(com.shivang.crm.modules.deal.entity.DealLineItem.class);
        verify(lineItemRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrencyCode()).isNull();
    }

    @Test
    void theLineItemSnapshotCarriesTheTenantItWasCreatedFor() {
        Offering offering = liveOffering("USD");

        dealService.createDeal(tenantId, actorId, request(wonStage,
                line(offering.getId(), "1", "1000.00", "0.00")));

        org.mockito.ArgumentCaptor<com.shivang.crm.modules.deal.entity.DealLineItem> captor =
                org.mockito.ArgumentCaptor.forClass(com.shivang.crm.modules.deal.entity.DealLineItem.class);
        verify(lineItemRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().getDealId()).isEqualTo(dealId);
    }

}
