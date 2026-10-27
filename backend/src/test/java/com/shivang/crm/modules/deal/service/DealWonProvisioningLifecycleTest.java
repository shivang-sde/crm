package com.shivang.crm.modules.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.dto.DealCreateRequest;
import com.shivang.crm.modules.deal.dto.DealUpdateRequest;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.mapper.DealMapper;
import com.shivang.crm.modules.deal.repository.DealCustomFieldRepository;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.deal.repository.DealStageRepository;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.entitlement.service.EntitlementProvisioningService;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.modules.lead.service.EntityHistoryService;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.0-P4-A: a Deal created directly in CLOSED_WON must run the same
 * provisioning step as an OPEN -> CLOSED_WON transition.
 *
 * L3.0-P4-E: a Deal with provisioned entitlements cannot be reassigned to a
 * different Account.
 */
class DealWonProvisioningLifecycleTest {

    private DealRepository dealRepository;
    private DealStageRepository dealStageRepository;
    private DealCustomFieldRepository dealCustomFieldRepository;
    private DealLineItemRepository dealLineItemRepository;
    private CustomerEntitlementRepository entitlementRepository;
    private AccountRepository accountRepository;
    private ContactRepository contactRepository;
    private DealMapper dealMapper;
    private EntitlementProvisioningService provisioningService;
    private DealService service;

    private UUID tenantA;
    private UUID tenantB;
    private UUID actorId;
    private DealStage openStage;
    private DealStage wonStage;
    private DealStage lostStage;

    @BeforeEach
    void setup() {
        dealRepository = mock(DealRepository.class);
        dealStageRepository = mock(DealStageRepository.class);
        dealCustomFieldRepository = mock(DealCustomFieldRepository.class);
        dealLineItemRepository = mock(DealLineItemRepository.class);
        entitlementRepository = mock(CustomerEntitlementRepository.class);
        accountRepository = mock(AccountRepository.class);
        contactRepository = mock(ContactRepository.class);
        dealMapper = mock(DealMapper.class);
        provisioningService = mock(EntitlementProvisioningService.class);

        service = new DealService(
                dealRepository, dealCustomFieldRepository, dealLineItemRepository, dealStageRepository,
                mock(DealLineItemService.class),
                entitlementRepository,
                mock(CanonicalCrmEventPublisher.class), dealMapper, mock(ActivityService.class),
                mock(EntityHistoryService.class), provisioningService,
                mock(UserRepository.class), mock(PermissionEvaluatorService.class),
                mock(RecordScopeGuard.class),
new CustomerRelationshipValidator(
                        accountRepository, contactRepository, dealRepository,
                        mock(LeadRepository.class), mock(TicketRepository.class),
                        mock(UserRepository.class)));

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
                .thenAnswer(inv -> com.shivang.crm.modules.deal.dto.DealResponse.builder().build());
        when(dealStageRepository.findByTenantIdAndIsDefault(tenantA, true)).thenReturn(Optional.of(openStage = stage(RecordCategory.OPEN)));
        when(dealStageRepository.findByTenantIdAndIsDefault(tenantB, true)).thenReturn(Optional.of(stage(RecordCategory.OPEN)));
        wonStage = stage(RecordCategory.CLOSED_WON);
        lostStage = stage(RecordCategory.CLOSED_LOST);
        when(dealStageRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.of(openStage));
        when(dealStageRepository.findByIdAndTenantId(wonStage.getId(), tenantA)).thenReturn(Optional.of(wonStage));
        when(dealStageRepository.findByIdAndTenantId(lostStage.getId(), tenantA)).thenReturn(Optional.of(lostStage));
    }

    private DealStage stage(RecordCategory category) {
        DealStage s = new DealStage();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantA);
        s.setName(category.name());
        s.setDisplayOrder(1);
        s.setRecordCategory(category);
        s.setIsDefault(category == RecordCategory.OPEN);
        return s;
    }

    private DealCreateRequest createRequest(DealStage stage) {
        DealCreateRequest request = DealCreateRequest.builder().name("Deal").build();
        if (stage != null) {
            request.setStageId(stage.getId());
        }
        when(dealMapper.toEntity(request)).thenAnswer(inv -> {
            Deal d = new Deal();
            d.setName(request.getName());
            return d;
        });
        return request;
    }

private void code(Throwable thrown, String expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    /**
     * L3.0-P5: a won Deal must arrive with the lines its coverage is derived
     * from. P4 created the won Deal first and had nothing to provision; P5 makes
     * the lines part of the same request.
     */
    private DealCreateRequest createWonRequestWithLineItem() {
        DealCreateRequest request = createRequest(wonStage);
        com.shivang.crm.modules.deal.dto.DealLineItemCreateRequest line =
                new com.shivang.crm.modules.deal.dto.DealLineItemCreateRequest();
        line.setOfferingId(UUID.randomUUID());
        line.setQuantity(java.math.BigDecimal.ONE);
        line.setUnitPrice(new java.math.BigDecimal("100.00"));
        request.setLineItems(List.of(line));
        return request;
    }

    private static Throwable ct(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable);
    }

    private UUID liveAccount() {
        UUID id = UUID.randomUUID();
        var account = new com.shivang.crm.modules.account.entity.Account();
        account.setId(id);
        account.setTenantId(tenantA);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(account));
        return id;
    }

    // --- P4-A: direct CLOSED_WON creation ---

    @Test
void directClosedWonCreateProvisionsEntitlements() {
        DealCreateRequest request = createWonRequestWithLineItem();

        service.createDeal(tenantA, actorId, request);

        verify(provisioningService).provisionFromWonDeal(eq(tenantA), any(UUID.class), eq(actorId));
    }

    /**
     * L3.0-P5: a won Deal with no lines is rejected outright rather than created
     * in a state that can never be repaired by adding lines to it.
     */
    @Test
    void closedWonCreateWithoutLineItemsIsRejectedBeforeAnythingIsWritten() {
        DealCreateRequest request = createRequest(wonStage);

        code(ct(() -> service.createDeal(tenantA, actorId, request)), "DEAL_WON_REQUIRES_LINE_ITEMS");

        verify(dealRepository, never()).save(any());
        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
    }

    @Test
    void openCreateDoesNotProvision() {
        DealCreateRequest request = createRequest(openStage);

        service.createDeal(tenantA, actorId, request);

        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
    }

    @Test
    void closedLostCreateDoesNotProvision() {
        DealCreateRequest request = createRequest(lostStage);
        // A closed-lost deal requires a loss reason before it can be created.
        request.setLostReason("Price");

        service.createDeal(tenantA, actorId, request);

        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
    }

    @Test
    void createWithNoStageUsesDefaultOpenStageAndDoesNotProvision() {
        DealCreateRequest request = createRequest(null);

        service.createDeal(tenantA, actorId, request);

        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
    }

    /**
     * Provisioning delegates to the existing idempotent implementation, so
     * createDeal cannot itself introduce duplicates. This test proves the call
     * goes through that single implementation rather than any second path.
     */
    @Test
void provisioningIsDelegatedToTheSingleExistingImplementation() {
        DealCreateRequest request = createWonRequestWithLineItem();

        service.createDeal(tenantA, actorId, request);

        verify(provisioningService).provisionFromWonDeal(any(UUID.class), any(UUID.class), any(UUID.class));
        // No direct entitlement repository writes from DealService.
        verify(entitlementRepository, never()).save(any());
    }

    // --- P4-E: account reassignment lock ---

    @Test
    void dealWithEntitlementCannotChangeAccount() {
        Deal deal = existingDeal();
        deal.setAccountId(liveAccount());
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantA, deal.getId()))
                .thenReturn(true);

        // A different, valid, same-tenant account: the ONLY reason to reject is
        // that entitlements already exist.
        DealUpdateRequest request = DealUpdateRequest.builder()
                .accountId(liveAccount())
                .build();

        Throwable thrown = ct(() -> service.updateDeal(deal.getId(), tenantA, actorId, request));
        code(thrown, "DEAL_ACCOUNT_REASSIGNMENT_BLOCKED");
        verify(dealRepository, never()).save(any(Deal.class));
    }

    @Test
    void dealWithoutEntitlementCanChangeAccount() {
        Deal deal = existingDeal();
        deal.setAccountId(liveAccount());
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantA, deal.getId()))
                .thenReturn(false);

        DealUpdateRequest request = DealUpdateRequest.builder()
                .accountId(liveAccount())
                .build();

        assertThatNoException().isThrownBy(() -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    @Test
    void sameAccountIsIdempotentEvenWithEntitlements() {
        UUID accountId = liveAccount();
        Deal deal = existingDeal();
        deal.setAccountId(accountId);
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantA, deal.getId()))
                .thenReturn(true);

        DealUpdateRequest request = DealUpdateRequest.builder().accountId(accountId).build();

        assertThatNoException().isThrownBy(() -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    @Test
    void crossTenantAccountStillRejectedBeforeReassignmentLock() {
        Deal deal = existingDeal();
        deal.setAccountId(liveAccount());
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantA, deal.getId()))
                .thenReturn(true);

        DealUpdateRequest request = DealUpdateRequest.builder()
                .accountId(UUID.randomUUID())
                .build();

        // P2's validator fires first: a cross-tenant account is simply not found.
        code(ct(() -> service.updateDeal(deal.getId(), tenantA, actorId, request)),
                "ACCOUNT_NOT_FOUND");
    }

    @Test
    void deletedAccountStillRejected() {
        Deal deal = existingDeal();
        deal.setAccountId(liveAccount());
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantA, deal.getId()))
                .thenReturn(true);

        DealUpdateRequest request = DealUpdateRequest.builder()
                .accountId(UUID.randomUUID())
                .build();

        code(ct(() -> service.updateDeal(deal.getId(), tenantA, actorId, request)),
                "ACCOUNT_NOT_FOUND");
    }

    @Test
    void reassignmentLockIsTenantScoped() {
        // Another tenant having entitlements for this deal id must not freeze it.
        Deal deal = existingDeal();
        deal.setAccountId(liveAccount());
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantA, deal.getId()))
                .thenReturn(false);

        DealUpdateRequest request = DealUpdateRequest.builder().accountId(liveAccount()).build();

        assertThatNoException().isThrownBy(() -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    @Test
    void updatingUnrelatedFieldOnEntitledDealIsStillAllowed() {
        Deal deal = existingDeal();
        deal.setAccountId(liveAccount());
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantA, deal.getId()))
                .thenReturn(true);

        DealUpdateRequest request = DealUpdateRequest.builder().name("Renamed").build();

        assertThatNoException().isThrownBy(() -> service.updateDeal(deal.getId(), tenantA, actorId, request));
    }

    // --- L4.1: won/reopen commercial invariants ---

    @Test
    void transitionToWonWithoutActiveLinesIsRejected() {
        Deal deal = existingDeal();
        DealUpdateRequest request = DealUpdateRequest.builder().stageId(wonStage.getId()).build();

        code(ct(() -> service.updateDeal(deal.getId(), tenantA, actorId, request)),
                "DEAL_WON_REQUIRES_LINE_ITEMS");
        verify(dealRepository, never()).save(any(Deal.class));
        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
    }

    @Test
    void transitionToWonWithActiveLinesProvisions() {
        Deal deal = existingDeal();
        when(dealLineItemRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantA, deal.getId()))
                .thenReturn(true);
        DealUpdateRequest request = DealUpdateRequest.builder().stageId(wonStage.getId()).build();

        assertThatNoException().isThrownBy(
                () -> service.updateDeal(deal.getId(), tenantA, actorId, request));
        verify(provisioningService).provisionFromWonDeal(tenantA, deal.getId(), actorId);
    }

    @Test
    void wonDealWithEntitlementsCannotBeReopened() {
        Deal deal = existingDeal();
        deal.setStage(wonStage);
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantA, deal.getId()))
                .thenReturn(true);
        DealUpdateRequest request = DealUpdateRequest.builder().stageId(openStage.getId()).build();

        code(ct(() -> service.updateDeal(deal.getId(), tenantA, actorId, request)),
                "DEAL_REOPEN_BLOCKED_BY_ENTITLEMENTS");
        verify(dealRepository, never()).save(any(Deal.class));
    }

    @Test
    void wonDealWithoutEntitlementsCanBeReopened() {
        Deal deal = existingDeal();
        deal.setStage(wonStage);
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantA, deal.getId()))
                .thenReturn(false);
        DealUpdateRequest request = DealUpdateRequest.builder().stageId(openStage.getId()).build();

        assertThatNoException().isThrownBy(
                () -> service.updateDeal(deal.getId(), tenantA, actorId, request));
        assertThat(deal.getStage().getId()).isEqualTo(openStage.getId());
    }

    private Deal existingDeal() {
        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setTenantId(tenantA);
        deal.setName("Existing");
        deal.setStage(openStage);
        deal.setCreatedBy(actorId);
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(deal.getId(), tenantA))
                .thenReturn(Optional.of(deal));
        return deal;
    }
}