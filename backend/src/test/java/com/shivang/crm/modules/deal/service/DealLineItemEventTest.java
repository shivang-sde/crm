package com.shivang.crm.modules.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.deal.dto.DealLineItemCreateRequest;
import com.shivang.crm.modules.deal.dto.DealLineItemUpdateRequest;
import com.shivang.crm.modules.deal.dto.DealUpdateRequest;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealLineItem;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.ForecastCategory;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.mapper.DealLineItemMapper;
import com.shivang.crm.modules.deal.mapper.DealMapper;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.0-P6: the DEAL_LINE_ITEM event family.
 *
 * <p>DealLineItemService is the sole producer of these three events, so the tests
 * exercise the public entry points and assert on what reaches the publisher. The
 * Deal mutation paths are covered in DealUpdatedEventTest.
 */
class DealLineItemEventTest {

    private CanonicalCrmEventPublisher publisher;
    private DealLineItemRepository lineItemRepository;
    private DealRepository dealRepository;
    private OfferingRepository offeringRepository;
    private DealLineItemService service;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID actorId;
    private UUID dealId;
    private UUID lineItemId;
    private UUID offeringId;
    private Offering offering;

    @BeforeEach
    void setup() {
        publisher = mock(CanonicalCrmEventPublisher.class);
        lineItemRepository = mock(DealLineItemRepository.class);
        dealRepository = mock(DealRepository.class);
        offeringRepository = mock(OfferingRepository.class);

        service = new DealLineItemService(lineItemRepository, dealRepository, offeringRepository,
                mock(DealLineItemMapper.class),
                mock(com.shivang.crm.modules.activity.service.ActivityService.class),
                mock(com.shivang.crm.modules.rbac.service.RecordScopeGuard.class), publisher);

        tenantId = UUID.randomUUID();
        otherTenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        lineItemId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null, java.util.List.of()));

        offering = new Offering();
        offering.setId(UUID.randomUUID());
        offering.setTenantId(tenantId);
        offering.setName("Plan");
        offering.setCode("PLAN");
        offering.setOfferingType(OfferingType.SUBSCRIPTION);
        offering.setBillingType(BillingType.ONE_TIME);
        offering.setActive(true);
        offering.setCurrencyCode("USD");
        offering.setDefaultPrice(new BigDecimal("1000.00"));
        offeringId = offering.getId();
        when(offeringRepository.findByIdAndTenantIdAndDeletedFalse(offeringId, tenantId))
                .thenReturn(Optional.of(offering));

        openDeal();
        when(lineItemRepository.save(any(DealLineItem.class))).thenAnswer(inv -> {
            DealLineItem li = inv.getArgument(0);
            if (li.getId() == null) {
                li.setId(lineItemId);
            }
            return li;
        });
        when(lineItemRepository.sumLineTotalsByTenantIdAndDealIdAndDeletedFalse(any(), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void openDeal() {
        DealStage stage = new DealStage();
        stage.setId(UUID.randomUUID());
        stage.setTenantId(tenantId);
        stage.setName("Negotiation");
        stage.setRecordCategory(RecordCategory.OPEN);

        Deal deal = new Deal();
        deal.setId(dealId);
        deal.setTenantId(tenantId);
        deal.setName("Deal");
        deal.setStage(stage);
        deal.setCreatedBy(actorId);
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId))
                .thenReturn(Optional.of(deal));
    }

    private DealLineItemCreateRequest createRequest() {
        DealLineItemCreateRequest request = new DealLineItemCreateRequest();
        request.setOfferingId(offeringId);
        request.setQuantity(new BigDecimal("2"));
        request.setUnitPrice(new BigDecimal("1000.00"));
        return request;
    }

    private DealLineItem existingLine() {
        DealLineItem li = new DealLineItem();
        li.setId(lineItemId);
        li.setTenantId(tenantId);
        li.setDealId(dealId);
        li.setOfferingId(offeringId);
        li.setItemName("Plan");
        li.setQuantity(new BigDecimal("2"));
        li.setUnitPrice(new BigDecimal("1000.00"));
        li.setCurrencyCode("USD");
        when(lineItemRepository.findByIdAndTenantIdAndDealIdAndDeletedFalse(lineItemId, tenantId, dealId))
                .thenReturn(Optional.of(li));
        return li;
    }

    private List<String> publishedNames() {
        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        verify(publisher, atLeast(0)).publish(any(UUID.class), entityType.capture(),
                eventType.capture(), any(), any());
        return java.util.stream.IntStream.range(0, entityType.getAllValues().size())
                .mapToObj(i -> entityType.getAllValues().get(i) + "." + eventType.getAllValues().get(i))
                .toList();
    }

    private Map<String, Object> metadataOf(String name) {
        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(publisher, atLeast(0)).publish(any(UUID.class), entityType.capture(),
                eventType.capture(), any(), metadata.capture());
        List<String> names = java.util.stream.IntStream.range(0, entityType.getAllValues().size())
                .mapToObj(i -> entityType.getAllValues().get(i) + "." + eventType.getAllValues().get(i))
                .toList();
        return metadata.getAllValues().get(names.indexOf(name));
    }

    // --- create ---

    @Test
    void creatingALineItemPublishesCreatedExactlyOnce() {
        service.createLineItem(tenantId, dealId, actorId, createRequest());

        assertThat(publishedNames()).containsExactly("DEAL_LINE_ITEM.CREATED");
    }

    @Test
    void createdMetadataCarriesDealOfferingAndCurrency() {
        service.createLineItem(tenantId, dealId, actorId, createRequest());

        assertThat(metadataOf("DEAL_LINE_ITEM.CREATED"))
                .containsEntry("dealId", dealId.toString())
                .containsEntry("offeringId", offeringId.toString())
                .containsEntry("currencyCode", "USD")
                .containsEntry("actorId", actorId.toString())
                .containsEntry("actorType", "USER");
    }

    @Test
    void createdMetadataTargetsTheLineItemId() {
        service.createLineItem(tenantId, dealId, actorId, createRequest());

        ArgumentCaptor<UUID> entityId = ArgumentCaptor.forClass(UUID.class);
        verify(publisher).publish(any(), eq("DEAL_LINE_ITEM"), eq("CREATED"), entityId.capture(), any());
        assertThat(entityId.getValue()).isEqualTo(lineItemId);
    }

    // --- update ---

    @Test
    void updatingALineItemPublishesUpdatedExactlyOnce() {
        existingLine();
        DealLineItemUpdateRequest request = new DealLineItemUpdateRequest();
        request.setQuantity(new BigDecimal("5"));

        service.updateLineItem(tenantId, dealId, lineItemId, actorId, request);

        assertThat(publishedNames()).containsExactly("DEAL_LINE_ITEM.UPDATED");
    }

    @Test
    void updatedMetadataNamesTheChangedFields() {
        existingLine();
        DealLineItemUpdateRequest request = new DealLineItemUpdateRequest();
        request.setQuantity(new BigDecimal("5"));
        request.setUnitPrice(new BigDecimal("1200.00"));

        service.updateLineItem(tenantId, dealId, lineItemId, actorId, request);

        Map<String, Object> metadata = metadataOf("DEAL_LINE_ITEM.UPDATED");
        assertThat((java.util.List<String>) metadata.get("changedFields"))
                .containsExactly("quantity", "unitPrice");
        assertThat(metadata)
                .containsEntry("dealId", dealId.toString())
                .containsEntry("offeringId", offeringId.toString());
    }

    @Test
    void anUpdateThatChangesNothingPublishesNothing() {
        existingLine();

        service.updateLineItem(tenantId, dealId, lineItemId, actorId, new DealLineItemUpdateRequest());

        // The row is still saved and the parent amount recalculated, but there is
        // no domain mutation to announce.
        assertThat(publishedNames()).isEmpty();
    }

    @Test
    void submittingTheSameValuePublishesNothing() {
        existingLine();
        DealLineItemUpdateRequest request = new DealLineItemUpdateRequest();
        request.setQuantity(new BigDecimal("2"));

        service.updateLineItem(tenantId, dealId, lineItemId, actorId, request);

        assertThat(publishedNames()).isEmpty();
    }

    // --- delete ---

    @Test
    void deletingALineItemPublishesDeletedExactlyOnce() {
        DealLineItem li = existingLine();

        service.deleteLineItem(tenantId, dealId, lineItemId, actorId);

        assertThat(publishedNames()).containsExactly("DEAL_LINE_ITEM.DELETED");
        assertThat(li.getDeleted()).isTrue();
    }

    @Test
    void deletedMetadataStillIdentifiesTheLineItem() {
        existingLine();

        service.deleteLineItem(tenantId, dealId, lineItemId, actorId);

        assertThat(metadataOf("DEAL_LINE_ITEM.DELETED"))
                .containsEntry("dealId", dealId.toString())
                .containsEntry("offeringId", offeringId.toString())
                .containsEntry("actorId", actorId.toString());

        ArgumentCaptor<UUID> entityId = ArgumentCaptor.forClass(UUID.class);
        verify(publisher).publish(any(), eq("DEAL_LINE_ITEM"), eq("DELETED"), entityId.capture(), any());
        assertThat(entityId.getValue()).isEqualTo(lineItemId);
    }

    // --- failed mutations publish nothing ---

    @Test
    void aFailedCreatePublishesNothing() {
        DealLineItemCreateRequest invalid = createRequest();
        invalid.setQuantity(BigDecimal.ZERO);

        Throwable thrown = catchThrowable(() -> service.createLineItem(tenantId, dealId, actorId, invalid));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo("INVALID_QUANTITY");
        verifyNoInteractions(publisher);
    }

    @Test
    void aFailedUpdatePublishesNothing() {
        existingLine();
        DealLineItemUpdateRequest invalid = new DealLineItemUpdateRequest();
        // discount above subtotal
        invalid.setDiscountAmount(new BigDecimal("9999.00"));

        Throwable thrown = catchThrowable(() -> service.updateLineItem(tenantId, dealId, lineItemId, actorId, invalid));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo("INVALID_DISCOUNT");
        verifyNoInteractions(publisher);
    }

    @Test
    void aClosedDealRejectsMutationAndPublishesNothing() {
        // P5 removed the P4 transitional exception, so a closed Deal cannot be
        // opened up to be sold, and therefore publishes no line events either.
        DealStage wonStage = new DealStage();
        wonStage.setId(UUID.randomUUID());
        wonStage.setTenantId(tenantId);
        wonStage.setRecordCategory(RecordCategory.CLOSED_WON);
        Deal won = new Deal();
        won.setId(dealId);
        won.setTenantId(tenantId);
        won.setStage(wonStage);
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId))
                .thenReturn(Optional.of(won));

        Throwable thrown = catchThrowable(() -> service.createLineItem(tenantId, dealId, actorId, createRequest()));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo("DEAL_CLOSED_LINE_ITEM_MUTATION");
        verifyNoInteractions(publisher);
    }

    // --- tenant isolation ---

    @Test
    void lineEventsCarryTheTenantTheMutationRanIn() {
        service.createLineItem(tenantId, dealId, actorId, createRequest());

        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        verify(publisher, atLeast(1)).publish(tenant.capture(), anyString(), anyString(), any(), any());
        assertThat(tenant.getAllValues()).containsOnly(tenantId);
        assertThat(tenant.getAllValues()).doesNotContain(otherTenantId);
    }

    @Test
    void anotherTenantCannotMutateOrEmit() {
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, otherTenantId))
                .thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() ->
                service.createLineItem(otherTenantId, dealId, actorId, createRequest()));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo("DEAL_NOT_FOUND");
        verifyNoInteractions(publisher);
    }

    // --- metadata hygiene ---

    @Test
    void lineEventsCarryNoSecretsAndNoEntityDump() {
        service.createLineItem(tenantId, dealId, actorId, createRequest());

        Map<String, Object> metadata = metadataOf("DEAL_LINE_ITEM.CREATED");
        assertThat(metadata).doesNotContainKeys("password", "credentials", "token", "secret", "customData");
        assertThat(metadata).containsOnlyKeys("actorId", "actorType", "source",
                "dealId", "offeringId", "quantity", "unitPrice", "lineTotal", "currencyCode");
    }

    @Test
    void aLineItemWithoutACurrencyOmitsTheKeyRatherThanWritingNull() {
        offering.setCurrencyCode(null);

        service.createLineItem(tenantId, dealId, actorId, createRequest());

        assertThat(metadataOf("DEAL_LINE_ITEM.CREATED")).doesNotContainKey("currencyCode");
    }

    @Test
    void everyLineEventTargetsTheDealLineItemEntityType() {
        existingLine();
        service.deleteLineItem(tenantId, dealId, lineItemId, actorId);

        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        verify(publisher, atLeast(1)).publish(any(), entityType.capture(), anyString(), any(), any());
        assertThat(entityType.getAllValues()).containsOnly("DEAL_LINE_ITEM");
    }
}
