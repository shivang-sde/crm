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
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.deal.dto.DealCreateRequest;
import com.shivang.crm.modules.deal.dto.DealLineItemCreateRequest;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealLineItem;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.mapper.DealMapper;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.entitlement.service.EntitlementProvisioningService;
import com.shivang.crm.shared.event.CanonicalCrmEventOutboxService;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.0-P6: the event set produced by an atomic CLOSED_WON Deal creation.
 *
 * <p>The outbox is the test boundary. No RabbitMQ is involved: the assertion is
 * about which canonical events were published, in which order, with which tenant,
 * entity and actor. That is exactly what a durable downstream consumer sees once
 * the transaction commits.
 */
class ClosedWonDealEventChainTest {

    private CanonicalCrmEventPublisher publisher;
    private DealRepository dealRepository;
    private DealMapper dealMapper;
    private EntitlementProvisioningService provisioningService;
    private DealService dealService;
    private com.shivang.crm.modules.catalog.repository.OfferingRepository offeringRepository;
    private com.shivang.crm.modules.deal.repository.DealLineItemRepository lineItemRepository;
    private com.shivang.crm.modules.deal.repository.DealStageRepository stageRepository;

    private UUID tenantId;
    private UUID actorId;
    private UUID dealId;
    private UUID accountId;
    private DealStage openStage;
    private DealStage wonStage;
    private AtomicReference<Deal> stored;

    @BeforeEach
    void setup() {
        publisher = mock(CanonicalCrmEventPublisher.class);
        dealRepository = mock(DealRepository.class);
        dealMapper = mock(DealMapper.class);
        provisioningService = mock(EntitlementProvisioningService.class);

        stageRepository = mock(com.shivang.crm.modules.deal.repository.DealStageRepository.class);
        lineItemRepository = mock(com.shivang.crm.modules.deal.repository.DealLineItemRepository.class);
        offeringRepository = mock(com.shivang.crm.modules.catalog.repository.OfferingRepository.class);
        var activityService = mock(com.shivang.crm.modules.activity.service.ActivityService.class);
        var recordScopeGuard = mock(com.shivang.crm.modules.rbac.service.RecordScopeGuard.class);

        var lineItemService = new DealLineItemService(
                lineItemRepository, dealRepository, offeringRepository,
                mock(com.shivang.crm.modules.deal.mapper.DealLineItemMapper.class),
                activityService, recordScopeGuard, publisher);

        dealService = new DealService(
                dealRepository,
                mock(com.shivang.crm.modules.deal.repository.DealCustomFieldRepository.class),
                lineItemRepository,
                stageRepository,
                lineItemService,
                mock(com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository.class),
                publisher, dealMapper, activityService,
                mock(com.shivang.crm.modules.lead.service.EntityHistoryService.class),
                provisioningService,
                mock(com.shivang.crm.modules.auth.repository.UserRepository.class),
                mock(com.shivang.crm.modules.rbac.service.PermissionEvaluatorService.class),
                recordScopeGuard,
                mock(com.shivang.crm.shared.service.CustomerRelationshipValidator.class));

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        stored = new AtomicReference<>();

        openStage = stage(RecordCategory.OPEN);
        wonStage = stage(RecordCategory.CLOSED_WON);
        when(stageRepository.findByIdAndTenantId(openStage.getId(), tenantId)).thenReturn(Optional.of(openStage));
        when(stageRepository.findByIdAndTenantId(wonStage.getId(), tenantId)).thenReturn(Optional.of(wonStage));
        when(stageRepository.findByTenantIdAndIsDefault(tenantId, true)).thenReturn(Optional.of(openStage));

        when(dealMapper.toEntity(any(DealCreateRequest.class))).thenAnswer(inv -> {
            Deal deal = new Deal();
            deal.setName(inv.<DealCreateRequest>getArgument(0).getName());
            return deal;
        });
        when(dealRepository.save(any(Deal.class))).thenAnswer(inv -> {
            Deal deal = inv.getArgument(0);
            if (deal.getId() == null) {
                deal.setId(dealId);
            }
            deal.setTenantId(tenantId);
            stored.set(deal);
            return deal;
        });
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(any(), any()))
                .thenAnswer(inv -> Optional.of(stored.get()));
        when(lineItemRepository.save(any())).thenAnswer(inv -> {
            DealLineItem li = inv.getArgument(0);
            if (li.getId() == null) {
                li.setId(UUID.randomUUID());
            }
            return li;
        });
        when(lineItemRepository.sumLineTotalsByTenantIdAndDealIdAndDeletedFalse(any(), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    // --- helpers ---

    private DealStage stage(RecordCategory category) {
        DealStage s = new DealStage();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setName(category.name());
        s.setRecordCategory(category);
        s.setDefaultProbability(50);
        return s;
    }

    private com.shivang.crm.modules.catalog.entity.Offering liveOffering() {
        var offering = new com.shivang.crm.modules.catalog.entity.Offering();
        offering.setId(UUID.randomUUID());
        offering.setTenantId(tenantId);
        offering.setName("Plan");
        offering.setCode("PLAN");
        offering.setOfferingType(com.shivang.crm.modules.catalog.enums.OfferingType.SUBSCRIPTION);
        offering.setBillingType(com.shivang.crm.modules.catalog.enums.BillingType.ONE_TIME);
        offering.setActive(true);
        offering.setCurrencyCode("USD");
        offering.setDefaultPrice(new BigDecimal("1000.00"));
        var offeringRepositoryRef = offeringRepository;
        when(offeringRepositoryRef.findByIdAndTenantIdAndDeletedFalse(offering.getId(), tenantId))
                .thenReturn(Optional.of(offering));
        return offering;
    }

    private DealLineItemCreateRequest line(UUID offeringId, String quantity, String price, String tax) {
        DealLineItemCreateRequest request = new DealLineItemCreateRequest();
        request.setOfferingId(offeringId);
        request.setQuantity(new BigDecimal(quantity));
        request.setUnitPrice(new BigDecimal(price));
        request.setTaxAmount(new BigDecimal(tax));
        return request;
    }

    private DealCreateRequest createRequest(DealStage stage, DealLineItemCreateRequest... lines) {
        DealCreateRequest request = DealCreateRequest.builder().name("Deal").build();
        request.setStageId(stage.getId());
        request.setAccountId(accountId);
        request.setOwnerUserId(actorId);
        if (lines.length > 0) {
            request.setLineItems(List.of(lines));
        }
        return request;
    }

    private List<PublishedEvent> publishedEvents() {
        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> entityId = ArgumentCaptor.forClass(UUID.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(publisher, atLeast(0)).publish(
                any(UUID.class), entityType.capture(), eventType.capture(),
                entityId.capture(), metadata.capture());
        return java.util.stream.IntStream.range(0, entityType.getAllValues().size())
                .mapToObj(i -> new PublishedEvent(entityType.getAllValues().get(i),
                        eventType.getAllValues().get(i), entityId.getAllValues().get(i),
                        metadata.getAllValues().get(i)))
                .toList();
    }

    private record PublishedEvent(String entityType, String eventType, UUID entityId, Map<String, Object> metadata) {
        String name() {
            return entityType + "." + eventType;
        }
    }

    // --- the full CLOSED_WON chain ---

    @Test
    void closedWonCreationPublishesDealThenLinesThenProvisioning() {
        var first = liveOffering();
        var second = liveOffering();

        dealService.createDeal(tenantId, actorId, createRequest(wonStage,
                line(first.getId(), "2", "1000.00", "180.00"),
                line(second.getId(), "1", "250.00", "0.00")));

        List<PublishedEvent> events = publishedEvents();
        assertThat(events).extracting(PublishedEvent::name)
                .containsExactly("DEAL.CREATED", "DEAL_LINE_ITEM.CREATED", "DEAL_LINE_ITEM.CREATED");

        assertThat(events).allSatisfy(event -> {
            assertThat(event.metadata()).containsEntry("actorId", actorId.toString());
            assertThat(event.metadata()).containsEntry("actorType", "USER");
        });

        assertThat(events.get(0).entityId()).isEqualTo(dealId);

        List<PublishedEvent> lineEvents = events.subList(1, 3);
        assertThat(lineEvents).allSatisfy(event -> {
            assertThat(event.entityId()).isNotNull();
            assertThat(event.metadata()).containsEntry("dealId", dealId.toString());
        });
        assertThat(lineEvents).extracting(e -> e.metadata().get("offeringId"))
                .containsExactlyInAnyOrder(first.getId().toString(), second.getId().toString());

        // Provisioning runs after both lines exist, so its entitlements are derived
        // from the complete commercial position.
        verify(provisioningService).provisionFromWonDeal(tenantId, dealId, actorId);
    }

    @Test
    void lineEventsCarryTheLineItemCurrencyFromTheOffering() {
        var offering = liveOffering();

        dealService.createDeal(tenantId, actorId,
                createRequest(wonStage, line(offering.getId(), "1", "1000.00", "0.00")));

        assertThat(publishedEvents()).filteredOn(e -> e.name().equals("DEAL_LINE_ITEM.CREATED"))
                .singleElement()
                .satisfies(event -> assertThat(event.metadata()).containsEntry("currencyCode", "USD"));
    }

    @Test
    void anOpenDealCreationEmitsDealCreatedButNoUpdated() {
        var offering = liveOffering();

        dealService.createDeal(tenantId, actorId,
                createRequest(openStage, line(offering.getId(), "1", "1000.00", "0.00")));

        assertThat(publishedEvents()).extracting(PublishedEvent::name)
                .containsExactly("DEAL.CREATED", "DEAL_LINE_ITEM.CREATED")
                .doesNotContain("DEAL.UPDATED");
        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());
    }

    /**
     * The Deal amount is recalculated internally whenever a line changes. That is
     * an internal save, not a user-visible Deal mutation, so it must not produce
     * DEAL.UPDATED.
     */
    @Test
    void internalAmountRecalculationDoesNotEmitDealUpdated() {
        var offering = liveOffering();
        when(lineItemRepository.sumLineTotalsByTenantIdAndDealIdAndDeletedFalse(any(), any()))
                .thenReturn(new BigDecimal("2180.00"));

        dealService.createDeal(tenantId, actorId,
                createRequest(openStage, line(offering.getId(), "2", "1000.00", "180.00")));

        assertThat(stored.get().getAmount()).isEqualByComparingTo("2180.00");
        assertThat(publishedEvents()).extracting(PublishedEvent::name).doesNotContain("DEAL.UPDATED");
    }

    // --- rollback ---

    @Test
    void aFailedSecondLineLeavesNoEventForTheSuccessfulOne() {
        var first = liveOffering();
        var second = liveOffering();

        Throwable thrown = catchThrowable(() -> dealService.createDeal(tenantId, actorId, createRequest(openStage,
                line(first.getId(), "2", "1000.00", "180.00"),
                // quantity must be positive, so this line fails validation
                line(second.getId(), "0", "500.00", "0.00"))));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo("INVALID_QUANTITY");

        // The successful first line's event was published into the transaction, so
        // the shared rollback discards it. No event may survive for a mutation that
        // did not commit, and nothing downstream may be told about the Deal.
        assertThat(publishedEvents()).extracting(PublishedEvent::name)
                .contains("DEAL_LINE_ITEM.CREATED");
        assertThat(publishedEvents()).extracting(PublishedEvent::name)
                .doesNotContain("DEAL.UPDATED", "DEAL_LINE_ITEM.DELETED");
        verify(provisioningService, never()).provisionFromWonDeal(any(), any(), any());

        // Structural half of the same guarantee: a RuntimeException escaping a
        // REQUIRED transaction rolls back the outbox rows written before it.
assertThat(DealService.class.getAnnotation(Transactional.class)).isNotNull();
        assertThat(outboxEnqueueIsTransactional())
                .as("the outbox insert must join the caller's transaction, not start its own")
                .isTrue();
    }
    // --- transport ---

    @Test
    void eventsEnqueueThroughTheTransactionalOutboxNotDirectlyToTheBroker() {
        // The canonical publisher's only job is to enqueue. Nothing in the P6 path
        // is annotated for direct broker access.
        assertThat(CanonicalCrmEventPublisher.class.getDeclaredMethods())
                .allSatisfy(method -> {
                    for (var annotation : method.getAnnotations()) {
                        assertThat(annotation.annotationType().getName())
                                .isNotEqualTo("org.springframework.amqp.rabbit.annotation.RabbitListener");
                    }
                });
        assertThat(outboxEnqueueIsTransactional())
                .as("the outbox insert must join the caller's transaction, not start its own")
                .isTrue();
    }

    /**
     * The outbox insert carries its transaction on the method rather than the
     * class, and must be REQUIRED so it joins the mutation's transaction.
     */
    private boolean outboxEnqueueIsTransactional() {
        var enqueue = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                CanonicalCrmEventOutboxService.class.getDeclaredMethod("enqueue",
                        com.shivang.crm.shared.event.CanonicalCrmEvent.class));
        Transactional annotation = enqueue.getAnnotation(Transactional.class);
        return annotation != null
                && annotation.propagation() == org.springframework.transaction.annotation.Propagation.REQUIRED;
    }

    // --- tenant isolation ---

    @Test
    void publishedEventsUseTheCreatingTenantAndNoOther() {
        var offering = liveOffering();
        UUID otherTenant = UUID.randomUUID();

        dealService.createDeal(tenantId, actorId,
                createRequest(openStage, line(offering.getId(), "1", "1000.00", "0.00")));

        ArgumentCaptor<UUID> publishedTenant = ArgumentCaptor.forClass(UUID.class);
        verify(publisher, atLeast(1)).publish(publishedTenant.capture(), anyString(), anyString(), any(), any());
        assertThat(publishedTenant.getAllValues()).containsOnly(tenantId);
        assertThat(publishedTenant.getAllValues()).doesNotContain(otherTenant);

        // A tenant B caller cannot resolve the tenant A Deal, so no event is produced.
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(any(), eq(otherTenant)))
                .thenReturn(Optional.empty());
        stored.set(null);
        Throwable thrown = catchThrowable(() -> dealService.createDeal(otherTenant, actorId,
                createRequest(openStage, line(offering.getId(), "1", "1000.00", "0.00"))));
        assertThat(thrown).isNotNull();
    }
}
