package com.shivang.crm.modules.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealLineItem;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;

/**
 * L3.0-P6: ENTITLEMENT.CREATED, and the requirement that every creation path
 * converges on one producer.
 *
 * <p>Both P5 CLOSED_WON provisioning and the P4 reconciliation service reach an
 * entitlement through {@code provisionFromWonDeal}, so publishing there means
 * neither path can forget the event and neither can emit its own. The
 * reconciliation path is exercised here too, to prove convergence rather than
 * assert it.
 */
class EntitlementCreationEventTest {

    private CanonicalCrmEventPublisher publisher;
    private CustomerEntitlementRepository entitlementRepository;
    private DealRepository dealRepository;
    private DealLineItemRepository lineItemRepository;
    private CustomerEntitlementService service;

    private UUID tenantId;
    private UUID actorId;
    private UUID dealId;
    private UUID accountId;
    private UUID offeringId;

    @BeforeEach
    void setup() {
        publisher = mock(CanonicalCrmEventPublisher.class);
        entitlementRepository = mock(CustomerEntitlementRepository.class);
        dealRepository = mock(DealRepository.class);
        lineItemRepository = mock(DealLineItemRepository.class);
        OfferingRepository offeringRepository = mock(OfferingRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        com.shivang.crm.modules.auth.repository.UserRepository userRepository =
                mock(com.shivang.crm.modules.auth.repository.UserRepository.class);

        service = new CustomerEntitlementService(entitlementRepository,
                mock(com.shivang.crm.modules.entitlement.mapper.CustomerEntitlementMapper.class),
                dealRepository, lineItemRepository, offeringRepository,
                mock(com.shivang.crm.modules.activity.service.ActivityService.class),
                mock(com.shivang.crm.modules.rbac.service.RecordScopeGuard.class),
                new com.shivang.crm.shared.service.CustomerRelationshipValidator(
                        accountRepository, mock(com.shivang.crm.modules.contact.repository.ContactRepository.class),
                        dealRepository, mock(com.shivang.crm.modules.lead.repository.LeadRepository.class),
                        mock(com.shivang.crm.modules.ticket.repository.TicketRepository.class),
                        userRepository),
                publisher);

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        dealId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        offeringId = UUID.randomUUID();

        when(entitlementRepository.save(any(CustomerEntitlement.class))).thenAnswer(inv -> {
            CustomerEntitlement saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(entitlementRepository.existsByTenantIdAndDealLineItemIdAndDeletedFalse(any(), any()))
                .thenReturn(false);

        Account account = new Account();
        account.setId(accountId);
        account.setTenantId(tenantId);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(accountId, tenantId))
                .thenReturn(Optional.of(account));

        var owner = new com.shivang.crm.modules.auth.entity.User();
        owner.setId(actorId);
        owner.setTenantId(tenantId);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(actorId, tenantId))
                .thenReturn(Optional.of(owner));

        Offering offering = new Offering();
        offering.setId(offeringId);
        offering.setTenantId(tenantId);
        offering.setName("Plan");
        offering.setActive(true);
        offering.setCurrencyCode("USD");
        when(offeringRepository.findByIdAndTenantIdAndDeletedFalse(offeringId, tenantId))
                .thenReturn(Optional.of(offering));
    }

    private Deal wonDeal() {
        DealStage stage = new DealStage();
        stage.setId(UUID.randomUUID());
        stage.setTenantId(tenantId);
        stage.setRecordCategory(RecordCategory.CLOSED_WON);

        Deal deal = new Deal();
        deal.setId(dealId);
        deal.setTenantId(tenantId);
        deal.setStage(stage);
        deal.setAccountId(accountId);
        deal.setOwnerId(actorId);
        deal.setCreatedBy(actorId);
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId))
                .thenReturn(Optional.of(deal));
        return deal;
    }

    private DealLineItem lineItem() {
        DealLineItem li = new DealLineItem();
        li.setId(UUID.randomUUID());
        li.setTenantId(tenantId);
        li.setDealId(dealId);
        li.setOfferingId(offeringId);
        li.setItemName("Plan");
        li.setQuantity(BigDecimal.ONE);
        li.setUnitPrice(new BigDecimal("1000.00"));
        li.setCurrencyCode("USD");
        li.setServiceStartDate(LocalDate.now().minusDays(1));
        li.setServiceEndDate(LocalDate.now().plusDays(30));
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

    @Test
    void provisioningFromAWonDealPublishesCreated() {
        wonDeal();
        when(lineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of(lineItem()));

        service.provisionFromWonDeal(tenantId, dealId, actorId);

        assertThat(publishedNames()).containsExactly("ENTITLEMENT.CREATED");
    }

    @Test
    void createdMetadataCarriesTheCommercialProvenance() {
        wonDeal();
        DealLineItem li = lineItem();
        when(lineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of(li));

        service.provisionFromWonDeal(tenantId, dealId, actorId);

        Map<String, Object> metadata = metadataOf("ENTITLEMENT.CREATED");
        assertThat(metadata)
                .containsEntry("accountId", accountId.toString())
                .containsEntry("dealId", dealId.toString())
                .containsEntry("dealLineItemId", li.getId().toString())
                .containsEntry("offeringId", offeringId.toString())
                .containsEntry("currencyCode", "USD")
                .containsEntry("actorId", actorId.toString())
                .containsEntry("actorType", "USER");
    }

    @Test
    void oneEntitlementIsPublishedPerLineItem() {
        wonDeal();
        DealLineItem first = lineItem();
        DealLineItem second = lineItem();
        when(lineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of(first, second));

        service.provisionFromWonDeal(tenantId, dealId, actorId);

        assertThat(publishedNames()).hasSize(2);
        assertThat(publishedNames()).containsOnly("ENTITLEMENT.CREATED");
        verify(entitlementRepository, times(2)).save(any(CustomerEntitlement.class));
    }

    @Test
    void aLineItemThatAlreadyHasAnEntitlementPublishesNothing() {
        wonDeal();
        when(lineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of(lineItem()));
        when(entitlementRepository.existsByTenantIdAndDealLineItemIdAndDeletedFalse(any(), any()))
                .thenReturn(true);

        service.provisionFromWonDeal(tenantId, dealId, actorId);

        assertThat(publishedNames()).isEmpty();
    }

    @Test
    void aWonDealWithNoLineItemsPublishesNothing() {
        wonDeal();
        when(lineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of());

        service.provisionFromWonDeal(tenantId, dealId, actorId);

        assertThat(publishedNames()).isEmpty();
    }

    @Test
    void aNonWonDealPublishesNothing() {
        wonDeal();
        Deal nonWon = dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId).orElseThrow();
        DealStage openStage = new DealStage();
        openStage.setId(UUID.randomUUID());
        openStage.setTenantId(tenantId);
        openStage.setRecordCategory(RecordCategory.OPEN);
        nonWon.setStage(openStage);
        when(lineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of(lineItem()));

        service.provisionFromWonDeal(tenantId, dealId, actorId);

        assertThat(publishedNames()).isEmpty();
    }

    /**
     * The reconciliation path must reach the same producer, so a Deal repaired by
     * P4's reconciliation service produces exactly the same event as one provisioned
     * at creation time. Two producers would let the two paths drift apart.
     */
    @Test
    void theReconciliationPathConvergesOnTheSameProducer() {
        Deal deal = wonDeal();
        when(lineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of(lineItem()));
        when(entitlementRepository.findByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(List.of());
        when(dealRepository.findByTenantIdAndRecordCategoryAndDeletedFalse(
                tenantId, RecordCategory.CLOSED_WON))
                .thenReturn(List.of(deal));
        when(entitlementRepository.existsByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .thenReturn(true);

        service.reconcileMissingEntitlementsForWonDeals(tenantId, actorId);

        // Same event, same producer, no second path.
        assertThat(publishedNames()).containsExactly("ENTITLEMENT.CREATED");
        verify(publisher, times(1)).publish(any(UUID.class),
                org.mockito.ArgumentMatchers.eq("ENTITLEMENT"),
                org.mockito.ArgumentMatchers.eq("CREATED"), any(), any());
    }

    @Test
    void entitlementEventsUseTheProvisionsTenant() {
        wonDeal();
        when(lineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of(lineItem()));

        service.provisionFromWonDeal(tenantId, dealId, actorId);

        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        verify(publisher).publish(tenant.capture(), anyString(), anyString(), any(), any());
        assertThat(tenant.getValue()).isEqualTo(tenantId);
    }

    @Test
    void createdMetadataCarriesTheCoverageWindowAndStatus() {
        wonDeal();
        when(lineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of(lineItem()));

        service.provisionFromWonDeal(tenantId, actorId.equals(actorId) ? dealId : dealId, actorId);

        Map<String, Object> metadata = metadataOf("ENTITLEMENT.CREATED");
        assertThat(metadata)
                .containsEntry("status", EntitlementStatus.ACTIVE.name())
                .containsKeys("startDate", "endDate", "renewable");
    }

    @Test
    void createdMetadataCarriesNoSecretsAndNoEntityDump() {
        wonDeal();
        when(lineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId))
                .thenReturn(List.of(lineItem()));

        service.provisionFromWonDeal(tenantId, dealId, actorId);

        Map<String, Object> metadata = metadataOf("ENTITLEMENT.CREATED");
        assertThat(metadata).doesNotContainKeys("password", "credentials", "token", "secret", "customData");
        assertThat(metadata).containsOnlyKeys("actorId", "actorType", "source", "accountId", "dealId",
                "dealLineItemId", "offeringId", "status", "startDate", "endDate", "renewable", "currencyCode");
    }
}
