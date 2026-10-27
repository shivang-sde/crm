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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.shivang.crm.modules.deal.dto.DealCreateRequest;
import com.shivang.crm.modules.deal.dto.DealUpdateRequest;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.ForecastCategory;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.mapper.DealMapper;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.deal.repository.DealStageRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;

/**
 * L3.0-P6: DEAL.UPDATED.
 *
 * <p>The interesting rules are the ones about what must NOT produce this event:
 * a no-op update, a creation, a stage-only change (which has its own more precise
 * event), and an owner-only change (OWNER_CHANGED). A phantom UPDATED would make
 * workflow triggers fire on mutations that carry no domain change.
 */
class DealUpdatedEventTest {

    private CanonicalCrmEventPublisher publisher;
    private DealRepository dealRepository;
    private DealMapper dealMapper;
    private DealStageRepository stageRepository;
    private DealLineItemRepository lineItemRepository;
    private com.shivang.crm.modules.deal.repository.DealCustomFieldRepository customFieldRepository;
    private DealService service;

    private UUID tenantId;
    private UUID actorId;
    private UUID dealId;
    private Deal deal;
    private DealStage negotiation;
    private DealStage closedWon;

    @BeforeEach
    void setup() {
        publisher = mock(CanonicalCrmEventPublisher.class);
        dealRepository = mock(DealRepository.class);
        dealMapper = mock(DealMapper.class);
        stageRepository = mock(DealStageRepository.class);
        lineItemRepository = mock(DealLineItemRepository.class);
        customFieldRepository = mock(com.shivang.crm.modules.deal.repository.DealCustomFieldRepository.class);
        var activityService = mock(com.shivang.crm.modules.activity.service.ActivityService.class);
        var scopeGuard = mock(com.shivang.crm.modules.rbac.service.RecordScopeGuard.class);

        var lineItemService = new DealLineItemService(lineItemRepository, dealRepository,
                mock(com.shivang.crm.modules.catalog.repository.OfferingRepository.class),
                mock(com.shivang.crm.modules.deal.mapper.DealLineItemMapper.class),
                activityService, scopeGuard, publisher);

        service = new DealService(dealRepository,
                customFieldRepository,
                lineItemRepository, stageRepository, lineItemService,
                mock(com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository.class),
                publisher, dealMapper, activityService,
                mock(com.shivang.crm.modules.lead.service.EntityHistoryService.class),
                mock(com.shivang.crm.modules.entitlement.service.EntitlementProvisioningService.class),
                mock(com.shivang.crm.modules.auth.repository.UserRepository.class),
                mock(com.shivang.crm.modules.rbac.service.PermissionEvaluatorService.class),
                scopeGuard,
                mock(com.shivang.crm.shared.service.CustomerRelationshipValidator.class));

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        dealId = UUID.randomUUID();

        negotiation = stage(RecordCategory.OPEN, "Negotiation");
        closedWon = stage(RecordCategory.CLOSED_WON, "Closed Won");
        when(stageRepository.findByIdAndTenantId(negotiation.getId(), tenantId)).thenReturn(Optional.of(negotiation));
        when(stageRepository.findByIdAndTenantId(closedWon.getId(), tenantId)).thenReturn(Optional.of(closedWon));
        when(stageRepository.findByTenantIdAndIsDefault(tenantId, true)).thenReturn(Optional.of(negotiation));

        deal = new Deal();
        deal.setId(dealId);
        deal.setTenantId(tenantId);
        deal.setName("Original");
        deal.setDescription("Original description");
        deal.setStage(negotiation);
        deal.setOwnerId(actorId);
        deal.setCreatedBy(actorId);
        deal.setProbability(50);
        deal.setForecastCategory(ForecastCategory.PIPELINE);
        deal.setNextStep("Call");

        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId))
                .thenReturn(Optional.of(deal));
        when(dealRepository.save(any(Deal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dealMapper.toResponse(any(Deal.class)))
                .thenAnswer(inv -> new com.shivang.crm.modules.deal.dto.DealResponse());
        // MapStruct applies the request's non-null fields; the mock must as well or
        // every update would look like a no-op.
        org.mockito.Mockito.doAnswer(invocation -> {
            DealUpdateRequest request = invocation.getArgument(0);
            Deal target = invocation.getArgument(1);
            if (request.getName() != null) {
                target.setName(request.getName());
            }
            if (request.getDescription() != null) {
                target.setDescription(request.getDescription());
            }
            if (request.getNextStep() != null) {
                target.setNextStep(request.getNextStep());
            }
            if (request.getProbability() != null) {
                target.setProbability(request.getProbability());
            }
            if (request.getForecastCategory() != null) {
                target.setForecastCategory(request.getForecastCategory());
            }
            if (request.getExpectedCloseDate() != null) {
                target.setExpectedCloseDate(request.getExpectedCloseDate());
            }
            if (request.getWonReason() != null) {
                target.setWonReason(request.getWonReason());
            }
            if (request.getLostReason() != null) {
                target.setLostReason(request.getLostReason());
            }
            return null;
        }).when(dealMapper).updateEntity(any(DealUpdateRequest.class), any(Deal.class));
    }

    private DealStage stage(RecordCategory category, String name) {
        DealStage s = new DealStage();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setName(name);
        s.setRecordCategory(category);
        s.setDefaultProbability(50);
        return s;
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

    // --- a meaningful update ---

    @Test
    void aMeaningfulUpdatePublishesExactlyOneUpdatedEvent() {
        DealUpdateRequest request = DealUpdateRequest.builder().name("Renamed").build();

        service.updateDeal(dealId, tenantId, actorId, request);

        assertThat(publishedNames()).containsExactly("DEAL.UPDATED");
    }

    @Test
    void updatedMetadataNamesTheChangedFieldsAndTheActor() {
        DealUpdateRequest request = DealUpdateRequest.builder()
                .name("Renamed")
                .description("New description")
                .build();

        service.updateDeal(dealId, tenantId, actorId, request);

        Map<String, Object> metadata = metadataOf("DEAL.UPDATED");
        assertThat((java.util.List<String>) metadata.get("changedFields"))
                .containsExactly("name", "description");
        assertThat(metadata)
                .containsEntry("actorId", actorId.toString())
                .containsEntry("actorType", "USER")
                .containsEntry("source", "MANUAL");
    }

    @Test
    void updatedMetadataCarriesStableCustomerIdentifiers() {
        UUID accountId = UUID.randomUUID();
        deal.setAccountId(accountId);
        UUID contactId = UUID.randomUUID();
        deal.setContactId(contactId);

        service.updateDeal(dealId, tenantId, actorId, DealUpdateRequest.builder().name("Renamed").build());

        Map<String, Object> metadata = metadataOf("DEAL.UPDATED");
        assertThat(metadata)
                .containsEntry("accountId", accountId.toString())
                .containsEntry("contactId", contactId.toString())
                .containsEntry("stageId", negotiation.getId().toString());
    }

    @Test
    void updatedMetadataCarriesNoSecretsAndNoEntityDump() {
        service.updateDeal(dealId, tenantId, actorId, DealUpdateRequest.builder().name("Renamed").build());

        Map<String, Object> metadata = metadataOf("DEAL.UPDATED");
        assertThat(metadata).doesNotContainKeys("password", "credentials", "token", "secret");
        assertThat(metadata).containsOnlyKeys("actorId", "actorType", "source", "changedFields",
                "stageId");
    }

    @Test
    void theUpdatedEventTargetsTheDealIdAndTenant() {
        service.updateDeal(dealId, tenantId, actorId, DealUpdateRequest.builder().name("Renamed").build());

        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> entityId = ArgumentCaptor.forClass(UUID.class);
        verify(publisher).publish(tenant.capture(), eq("DEAL"), eq("UPDATED"), entityId.capture(), any());
        assertThat(tenant.getValue()).isEqualTo(tenantId);
        assertThat(entityId.getValue()).isEqualTo(dealId);
    }

    // --- what must not emit ---

    @Test
    void anUpdateThatChangesNothingPublishesNothing() {
        service.updateDeal(dealId, tenantId, actorId, DealUpdateRequest.builder().build());

        assertThat(publishedNames()).isEmpty();
    }

    @Test
    void resubmittingTheSameValuePublishesNothing() {
        DealUpdateRequest request = DealUpdateRequest.builder()
                .name("Original")
                .description("Original description")
                .nextStep("Call")
                .probability(50)
                .build();

        service.updateDeal(dealId, tenantId, actorId, request);

        assertThat(publishedNames()).isEmpty();
    }

    @Test
    void creationPublishesCreatedOnlyAndNeverUpdated() {
        when(dealMapper.toEntity(any(DealCreateRequest.class))).thenAnswer(inv -> {
            Deal d = new Deal();
            d.setName("Brand new");
            return d;
        });
        DealCreateRequest request = DealCreateRequest.builder().name("Brand new").build();
        request.setStageId(negotiation.getId());

        service.createDeal(tenantId, actorId, request);

        assertThat(publishedNames()).containsExactly("DEAL.CREATED");
        assertThat(publishedNames()).doesNotContain("DEAL.UPDATED");
    }

    @Test
    void aStageOnlyChangePublishesStageTransitionedAndNotUpdated() {
        // STAGE_TRANSITIONED already models this transition with strictly more
        // detail, so a second UPDATED would double-count it.
        when(lineItemRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId)).thenReturn(true);
        DealUpdateRequest request = DealUpdateRequest.builder()
                .stageId(closedWon.getId())
                .wonReason("Signed")
                .build();

        service.updateDeal(dealId, tenantId, actorId, request);

        // The won reason is a genuine field change, but the stage transition is
        // still the authoritative event and must be emitted through PUT too.
        assertThat(publishedNames())
                .containsExactly("DEAL.STAGE_TRANSITIONED")
                .doesNotContain("DEAL.UPDATED");
    }

    @Test
    void aPureStageMoveThroughUpdatePublishesTheStageTransition() {
        when(lineItemRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId)).thenReturn(true);
        DealUpdateRequest request = DealUpdateRequest.builder().stageId(closedWon.getId()).build();

        service.updateDeal(dealId, tenantId, actorId, request);

        assertThat(publishedNames()).containsExactly("DEAL.STAGE_TRANSITIONED");
        assertThat(metadataOf("DEAL.STAGE_TRANSITIONED"))
                .containsEntry("previousStageId", negotiation.getId().toString())
                .containsEntry("newStageId", closedWon.getId().toString());
    }

    @Test
    void aStageChangeDoesNotDoublePublish() {
        when(lineItemRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId)).thenReturn(true);
        service.changeStage(dealId, tenantId, closedWon.getId(), actorId);

        List<String> names = publishedNames();
        assertThat(names).contains("DEAL.STAGE_TRANSITIONED");
        assertThat(names).doesNotContain("DEAL.UPDATED");
        assertThat(names.stream().filter("DEAL.STAGE_TRANSITIONED"::equals)).hasSize(1);
    }

    @Test
    void anOwnerChangePublishesOwnerChangedAndNotUpdated() {
        UUID newOwner = UUID.randomUUID();
        deal.setOwnerId(actorId);

        service.assignDeal(dealId, tenantId, newOwner, actorId);

        List<String> names = publishedNames();
        assertThat(names).contains("DEAL.OWNER_CHANGED");
        assertThat(names).doesNotContain("DEAL.UPDATED");
    }

    @Test
    void updatingOwnerThroughPutAssignsItAndPublishesOwnerChanged() {
        UUID newOwner = UUID.randomUUID();
        DealUpdateRequest request = DealUpdateRequest.builder().ownerUserId(newOwner).build();

        service.updateDeal(dealId, tenantId, actorId, request);

        assertThat(deal.getOwnerId()).isEqualTo(newOwner);
        assertThat(publishedNames())
                .containsExactly("DEAL.OWNER_CHANGED")
                .doesNotContain("DEAL.UPDATED");
    }

    @Test
    void aFailedUpdatePublishesNothing() {
        org.mockito.Mockito.doThrow(new com.shivang.crm.shared.exception.BusinessException("NOT_FOUND", "nope"))
                .when(dealMapper).updateEntity(any(DealUpdateRequest.class), any(Deal.class));

        Throwable thrown = catchThrowable(() -> service.updateDeal(dealId, tenantId, actorId,
                DealUpdateRequest.builder().name("Renamed").build()));

        assertThat(thrown).isNotNull();
        verifyNoInteractions(publisher);
    }

    @Test
    void anotherTenantCannotUpdateOrEmit() {
        UUID otherTenant = UUID.randomUUID();
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, otherTenant))
                .thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.updateDeal(dealId, otherTenant, actorId,
                DealUpdateRequest.builder().name("Renamed").build()));

        assertThat(thrown).isNotNull();
        verifyNoInteractions(publisher);
    }

    @Test
    void aDealWithNoAccountOmitsTheIdentifierRatherThanWritingNull() {
        service.updateDeal(dealId, tenantId, actorId, DealUpdateRequest.builder().name("Renamed").build());

        assertThat(metadataOf("DEAL.UPDATED")).doesNotContainKey("accountId");
    }

    @Test
    void aForecastCategoryChangeIsReported() {
        service.updateDeal(dealId, tenantId, actorId,
                DealUpdateRequest.builder().forecastCategory(ForecastCategory.COMMIT).build());

        assertThat((java.util.List<String>) metadataOf("DEAL.UPDATED").get("changedFields"))
                .containsExactly("forecastCategory");
    }

    @Test
    void anExpectedCloseDateChangeIsReported() {
        deal.setExpectedCloseDate(LocalDate.of(2026, 1, 1));

        service.updateDeal(dealId, tenantId, actorId,
                DealUpdateRequest.builder().expectedCloseDate(LocalDate.of(2026, 6, 1)).build());

        assertThat((java.util.List<String>) metadataOf("DEAL.UPDATED").get("changedFields"))
                .containsExactly("expectedCloseDate");
    }

    @Test
    void anAmountOnlyChangeIsNotAnUpdatedEvent() {
        // The amount is managed by line items and recalculated internally, so it is
        // deliberately outside the compared field set.
        deal.setAmount(new BigDecimal("500.00"));

        service.updateDeal(dealId, tenantId, actorId,
                DealUpdateRequest.builder().amount(new BigDecimal("900.00")).build());

        assertThat(publishedNames()).isEmpty();
    }

    @Test
    void omittingCustomDataOnAPartialUpdateKeepsRequiredValues() {
        var segment = new com.shivang.crm.modules.deal.entity.DealCustomField();
        segment.setTenantId(tenantId);
        segment.setFieldKey("segment");
        segment.setFieldLabel("Segment");
        segment.setFieldType("TEXT");
        segment.setIsRequired(true);
        when(customFieldRepository.findActiveFieldsByTenant(tenantId)).thenReturn(List.of(segment));
        deal.setCustomData(Map.of("segment", "enterprise"));

        service.updateDeal(dealId, tenantId, actorId, DealUpdateRequest.builder().name("Renamed").build());

        assertThat(deal.getCustomData()).containsEntry("segment", "enterprise");
    }

    @Test
    void softDeletingADealPublishesNoUpdatedEvent() {
        service.deleteDeal(dealId, tenantId, actorId);

        assertThat(deal.getDeleted()).isTrue();
        assertThat(publishedNames()).isEmpty();
    }

    @Test
    void provisioningIsNotTriggeredByAPlainFieldUpdate() {
        var provisioning = mock(com.shivang.crm.modules.entitlement.service.EntitlementProvisioningService.class);
        // Re-using the wired service's provisioning mock is not possible here, so
        // assert through the observable effect instead: no entitlement events.
        service.updateDeal(dealId, tenantId, actorId, DealUpdateRequest.builder().name("Renamed").build());

        assertThat(publishedNames()).doesNotContain("ENTITLEMENT.CREATED");
        verify(publisher, never()).publish(any(), eq("ENTITLEMENT"), anyString(), any(), any());
    }
}
