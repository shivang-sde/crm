package com.shivang.crm.modules.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.deal.dto.DealLineItemCreateRequest;
import com.shivang.crm.modules.deal.dto.DealLineItemUpdateRequest;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealLineItem;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.mapper.DealLineItemMapper;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.0-P4-C: line items are part of the commercial opportunity and may only be
 * mutated while the Deal is open, or while a closed Deal's provisioning is
 * still pending.
 *
 * L3.0-P4-F: currency is snapshotted from the Offering and preserved.
 */
class DealLineItemLifecycleTest {

    private DealLineItemRepository lineItemRepository;
    private DealRepository dealRepository;
    private OfferingRepository offeringRepository;
    private DealLineItemMapper lineItemMapper;
    private DealLineItemService service;

    private UUID tenantId;
    private UUID actorId;
    private UUID dealId;
    private UUID lineItemId;

    @BeforeEach
    void setup() {
        lineItemRepository = mock(DealLineItemRepository.class);
        dealRepository = mock(DealRepository.class);
        offeringRepository = mock(OfferingRepository.class);
        lineItemMapper = mock(DealLineItemMapper.class);

        service = new DealLineItemService(
                lineItemRepository, dealRepository, offeringRepository,
                lineItemMapper, mock(ActivityService.class), mock(RecordScopeGuard.class),
                mock(com.shivang.crm.shared.event.CanonicalCrmEventPublisher.class));

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        lineItemId = UUID.randomUUID();

        // ensureDealAccessible resolves the caller from the SecurityContext.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        actorId.toString(), null, java.util.List.of()));

        when(lineItemMapper.toResponse(any(DealLineItem.class)))
                .thenAnswer(inv -> com.shivang.crm.modules.deal.dto.DealLineItemResponse.builder().build());
        when(lineItemRepository.save(any(DealLineItem.class))).thenAnswer(inv -> {
            DealLineItem li = inv.getArgument(0);
            if (li.getId() == null) {
                li.setId(lineItemId);
            }
            return li;
        });
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void dealInCategory(RecordCategory category) {
        DealStage stage = new DealStage();
        stage.setId(UUID.randomUUID());
        stage.setTenantId(tenantId);
        stage.setName(category.name());
        stage.setRecordCategory(category);

        Deal deal = new Deal();
        deal.setId(dealId);
        deal.setTenantId(tenantId);
        deal.setName("Deal");
        deal.setStage(stage);
        deal.setCreatedBy(actorId);

        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId))
                .thenReturn(Optional.of(deal));
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
        when(offeringRepository.findByIdAndTenantIdAndDeletedFalse(offering.getId(), tenantId))
                .thenReturn(Optional.of(offering));
        return offering;
    }

    private DealLineItemCreateRequest createRequest(UUID offeringId) {
        DealLineItemCreateRequest request = new DealLineItemCreateRequest();
        request.setOfferingId(offeringId);
        request.setQuantity(BigDecimal.ONE);
        return request;
    }

    private void assertCode(String expected, ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    // --- OPEN permits mutation ---

    @Test
    void openDealPermitsLineItemCreate() {
        dealInCategory(RecordCategory.OPEN);
        Offering offering = liveOffering("USD");

        assertThatNoException().isThrownBy(
                () -> service.createLineItem(tenantId, dealId, actorId, createRequest(offering.getId())));
        verify(lineItemRepository).save(any(DealLineItem.class));
    }

    @Test
    void openDealPermitsLineItemUpdateAndDelete() {
        dealInCategory(RecordCategory.OPEN);
        existingLineItem();

        assertThatNoException().isThrownBy(
                () -> service.updateLineItem(tenantId, dealId, lineItemId, actorId, new DealLineItemUpdateRequest()));
        assertThatNoException().isThrownBy(
                () -> service.deleteLineItem(tenantId, dealId, lineItemId, actorId));
    }

    // --- CLOSED_WON rejects ---

    @Test
    void closedWonDealRejectsCreate() {
        dealInCategory(RecordCategory.CLOSED_WON);
        Offering offering = liveOffering("USD");

        assertCode("DEAL_CLOSED_LINE_ITEM_MUTATION",
                () -> service.createLineItem(tenantId, dealId, actorId, createRequest(offering.getId())));
        verify(lineItemRepository, never()).save(any(DealLineItem.class));
    }

    @Test
    void closedWonDealRejectsUpdate() {
        dealInCategory(RecordCategory.CLOSED_WON);
        existingLineItem();

        assertCode("DEAL_CLOSED_LINE_ITEM_MUTATION",
                () -> service.updateLineItem(tenantId, dealId, lineItemId, actorId, new DealLineItemUpdateRequest()));
        verify(lineItemRepository, never()).save(any(DealLineItem.class));
    }

    @Test
    void closedWonDealRejectsDelete() {
        dealInCategory(RecordCategory.CLOSED_WON);
        existingLineItem();

        assertCode("DEAL_CLOSED_LINE_ITEM_MUTATION",
                () -> service.deleteLineItem(tenantId, dealId, lineItemId, actorId));
        verify(lineItemRepository, never()).save(any(DealLineItem.class));
    }

    // --- CLOSED_LOST rejects ---

    @Test
    void closedLostDealRejectsAllMutations() {
        dealInCategory(RecordCategory.CLOSED_LOST);
        Offering offering = liveOffering("USD");
        existingLineItem();

        assertCode("DEAL_CLOSED_LINE_ITEM_MUTATION",
                () -> service.createLineItem(tenantId, dealId, actorId, createRequest(offering.getId())));
        assertCode("DEAL_CLOSED_LINE_ITEM_MUTATION",
                () -> service.updateLineItem(tenantId, dealId, lineItemId, actorId, new DealLineItemUpdateRequest()));
        assertCode("DEAL_CLOSED_LINE_ITEM_MUTATION",
                () -> service.deleteLineItem(tenantId, dealId, lineItemId, actorId));
    }

    // --- the P4 transitional exception is gone ---

    /**
     * P4 allowed a closed Deal whose provisioning was still pending to accept a
     * line item, because there was no other way to give a directly-created won
     * Deal its lines. P5 removed that window: creation now carries the lines, so
     * a closed Deal is always complete, and "no entitlements yet" is no longer a
     * licence to sell to it.
     */
    @Test
    void closedDealRejectsCreateEvenWhenNoEntitlementsExistYet() {
        dealInCategory(RecordCategory.CLOSED_WON);
        Offering offering = liveOffering("USD");

        // Nothing about entitlement state is consulted any more, so this is the
        // same rejection a fully provisioned won Deal gets.
        assertCode("DEAL_CLOSED_LINE_ITEM_MUTATION",
                () -> service.createLineItem(tenantId, dealId, actorId, createRequest(offering.getId())));
        verify(lineItemRepository, never()).save(any(DealLineItem.class));
    }

    @Test
    void closedDealRejectsDeleteEvenWhenNoEntitlementsExistYet() {
        dealInCategory(RecordCategory.CLOSED_WON);
        existingLineItem();

        assertCode("DEAL_CLOSED_LINE_ITEM_MUTATION",
                () -> service.deleteLineItem(tenantId, dealId, lineItemId, actorId));
        verify(lineItemRepository, never()).save(any(DealLineItem.class));
    }

    @Test
    void closedWonDealWithAnUnprovisionedLineStillCannotBeRepairedByAddingALine() {
        // The P4 exception existed precisely so this repair was possible. It must
        // now be rejected; such a Deal is reconciled by the P4-B service, not by
        // quietly re-opening a won opportunity for sale.
        dealInCategory(RecordCategory.CLOSED_WON);
        existingLineItem();
        Offering offering = liveOffering("USD");

        assertCode("DEAL_CLOSED_LINE_ITEM_MUTATION",
                () -> service.updateLineItem(tenantId, dealId, lineItemId, actorId, new DealLineItemUpdateRequest()));
        assertCode("DEAL_CLOSED_LINE_ITEM_MUTATION",
                () -> service.createLineItem(tenantId, dealId, actorId, createRequest(offering.getId())));
    }

    // --- reads stay allowed ---

    @Test
    void closedDealStillAllowsReadingLineItems() {
        dealInCategory(RecordCategory.CLOSED_WON);
        when(lineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of());
        when(lineItemMapper.toResponseList(any())).thenReturn(List.of());

        assertThatNoException().isThrownBy(() -> service.listLineItems(tenantId, dealId));
    }

    // --- a deleted deal cannot bypass the rule ---

    @Test
    void deletedDealCannotBeUsedToMutateLineItems() {
        // P2's deleted-aware lookup returns nothing for a soft-deleted Deal, so the
        // lifecycle check can never be skipped by deleting the parent.
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId))
                .thenReturn(Optional.empty());
        Offering offering = liveOffering("USD");

        assertCode("DEAL_NOT_FOUND",
                () -> service.createLineItem(tenantId, dealId, actorId, createRequest(offering.getId())));
        verify(lineItemRepository, never()).save(any(DealLineItem.class));
    }

    @Test
    void crossTenantDealCannotBeUsedToMutateLineItems() {
        dealInCategory(RecordCategory.OPEN);
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, UUID.randomUUID()))
                .thenReturn(Optional.empty());
        Offering offering = liveOffering("USD");

        assertCode("DEAL_NOT_FOUND",
                () -> service.createLineItem(UUID.randomUUID(), dealId, actorId, createRequest(offering.getId())));
    }

    // --- P4-F: currency propagation ---

    @Test
    void newLineItemInheritsOfferingCurrency() {
        dealInCategory(RecordCategory.OPEN);
        Offering offering = liveOffering("EUR");

        service.createLineItem(tenantId, dealId, actorId, createRequest(offering.getId()));

        var captor = org.mockito.ArgumentCaptor.forClass(DealLineItem.class);
        verify(lineItemRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    void offeringWithoutCurrencyYieldsNullCurrency() {
        dealInCategory(RecordCategory.OPEN);
        Offering offering = liveOffering(null);

        service.createLineItem(tenantId, dealId, actorId, createRequest(offering.getId()));

        var captor = org.mockito.ArgumentCaptor.forClass(DealLineItem.class);
        verify(lineItemRepository).save(captor.capture());
        // Nullability is preserved, not defaulted to an invented currency.
        assertThat(captor.getValue().getCurrencyCode()).isNull();
    }

    @Test
    void aLineInADifferentCurrencyIsRejected() {
        dealInCategory(RecordCategory.OPEN);
        when(lineItemRepository.findDistinctCurrencyCodesByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(List.of("USD"));
        Offering offering = liveOffering("EUR");

        assertCode("DEAL_CURRENCY_MISMATCH",
                () -> service.createLineItem(tenantId, dealId, actorId, createRequest(offering.getId())));
        verify(lineItemRepository, never()).save(any(DealLineItem.class));
    }

    @Test
    void aLineInTheDealCurrencyIsAccepted() {
        dealInCategory(RecordCategory.OPEN);
        when(lineItemRepository.findDistinctCurrencyCodesByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(List.of("USD"));
        Offering offering = liveOffering("USD");

        assertThatNoException().isThrownBy(
                () -> service.createLineItem(tenantId, dealId, actorId, createRequest(offering.getId())));
    }

    @Test
    void knownAndUnknownCurrenciesCannotBeMixed() {
        dealInCategory(RecordCategory.OPEN);
        when(lineItemRepository.findDistinctCurrencyCodesByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(java.util.Arrays.asList((String) null));
        Offering offering = liveOffering("USD");

        assertCode("DEAL_CURRENCY_MISMATCH",
                () -> service.createLineItem(tenantId, dealId, actorId, createRequest(offering.getId())));
    }

    @Test
    void currencyIsNotMutableThroughUpdate() {
        dealInCategory(RecordCategory.OPEN);
        DealLineItem existing = existingLineItemEntity("USD");
        when(lineItemRepository.findByIdAndTenantIdAndDealIdAndDeletedFalse(lineItemId, tenantId, dealId))
                .thenReturn(Optional.of(existing));

        DealLineItemUpdateRequest request = new DealLineItemUpdateRequest();
        request.setQuantity(new BigDecimal("3"));

        service.updateLineItem(tenantId, dealId, lineItemId, actorId, request);

        assertThat(existing.getCurrencyCode()).isEqualTo("USD");
    }

    // --- monetary calculation semantics unchanged ---

    @Test
    void lineTotalCalculationRemainsAmountBased() {
        dealInCategory(RecordCategory.OPEN);
        Offering offering = liveOffering("USD");

        DealLineItemCreateRequest request = createRequest(offering.getId());
        request.setUnitPrice(new BigDecimal("100.00"));
        request.setQuantity(new BigDecimal("2"));
        request.setDiscountAmount(new BigDecimal("20.00"));
        request.setTaxAmount(new BigDecimal("10.00"));

        service.createLineItem(tenantId, dealId, actorId, request);

        var captor = org.mockito.ArgumentCaptor.forClass(DealLineItem.class);
        verify(lineItemRepository).save(captor.capture());
        // 2 * 100 - 20 + 10 = 190
        assertThat(captor.getValue().getLineTotal()).isEqualByComparingTo("190.00");
    }

    @Test
    void discountAboveSubtotalStillRejected() {
        dealInCategory(RecordCategory.OPEN);
        Offering offering = liveOffering("USD");

        DealLineItemCreateRequest request = createRequest(offering.getId());
        request.setUnitPrice(new BigDecimal("100.00"));
        request.setQuantity(new BigDecimal("1"));
        request.setDiscountAmount(new BigDecimal("500.00"));

        assertCode("INVALID_DISCOUNT",
                () -> service.createLineItem(tenantId, dealId, actorId, request));
    }

    // --- currency is read-only across the whole API surface ---

    /**
     * The currency is a snapshot of the Offering, so it is readable but never
     * writable. Exposing it on a request DTO would let a client price a line in
     * a currency the Offering was never sold in.
     */
    @Test
    void lineItemResponseExposesCurrencyButNeitherRequestDtoDoes() {
        assertThat(hasProperty("currencyCode", com.shivang.crm.modules.deal.dto.DealLineItemResponse.class))
                .isTrue();
        assertThat(hasProperty("currencyCode", DealLineItemCreateRequest.class)).isFalse();
        assertThat(hasProperty("currencyCode", DealLineItemUpdateRequest.class)).isFalse();
    }

    private static boolean hasProperty(String name, Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .anyMatch(f -> f.getName().equals(name));
    }

    @Test
    void updatingALineKeepsItsOriginalCurrencySnapshot() {
        dealInCategory(RecordCategory.OPEN);
        existingLineItem();
        // The Offering's currency has moved since the line was sold.
        liveOffering("EUR");

        DealLineItemUpdateRequest request = new DealLineItemUpdateRequest();
        request.setUnitPrice(new BigDecimal("120.00"));

        service.updateLineItem(tenantId, dealId, lineItemId, actorId, request);

        ArgumentCaptor<DealLineItem> saved = ArgumentCaptor.forClass(DealLineItem.class);
        verify(lineItemRepository).save(saved.capture());
        // Price changed, currency did not: the snapshot is immutable even though
        // the Offering's currency has since moved.
        assertThat(saved.getValue().getUnitPrice()).isEqualByComparingTo("120.00");
        assertThat(saved.getValue().getCurrencyCode()).isEqualTo("USD");
    }

    // --- helpers ---

    private void existingLineItem() {
        when(lineItemRepository.findByIdAndTenantIdAndDealIdAndDeletedFalse(lineItemId, tenantId, dealId))
                .thenReturn(Optional.of(existingLineItemEntity("USD")));
    }

    private DealLineItem existingLineItemEntity(String currency) {
        DealLineItem li = new DealLineItem();
        li.setId(lineItemId);
        li.setTenantId(tenantId);
        li.setDealId(dealId);
        li.setOfferingId(UUID.randomUUID());
        li.setQuantity(BigDecimal.ONE);
        li.setUnitPrice(new BigDecimal("100.00"));
        li.setCurrencyCode(currency);
        return li;
    }
}