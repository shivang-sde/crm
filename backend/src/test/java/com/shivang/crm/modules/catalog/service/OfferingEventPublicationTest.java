package com.shivang.crm.modules.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.catalog.enums.BillingInterval;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;
import com.shivang.crm.modules.catalog.dto.OfferingCreateRequest;
import com.shivang.crm.modules.catalog.dto.OfferingUpdateRequest;
import com.shivang.crm.modules.catalog.mapper.OfferingMapper;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;

/**
 * L3.0-P6: the OFFERING event family.
 *
 * <p>The Offering lifecycle in this repository is a boolean {@code active} flag,
 * so the event semantics chosen here are: UPDATED for non-lifecycle field edits,
 * ACTIVATED / DEACTIVATED for a transition across that flag, and exactly one event
 * per mutation.
 */
class OfferingEventPublicationTest {

    private CanonicalCrmEventPublisher publisher;
    private OfferingRepository repository;
    private OfferingMapper mapper;
    private com.shivang.crm.modules.auth.repository.UserRepository userRepository;
    private OfferingService service;

    private UUID tenantId;
    private UUID actorId;
    private UUID offeringId;
    private Offering offering;

    @BeforeEach
    void setup() {
        publisher = mock(CanonicalCrmEventPublisher.class);
        repository = mock(OfferingRepository.class);
        mapper = mock(OfferingMapper.class);
        userRepository = mock(com.shivang.crm.modules.auth.repository.UserRepository.class);
        var activityService = mock(com.shivang.crm.modules.activity.service.ActivityService.class);

        // The service maps to a response on every write path, and maps the create
        // request onto a fresh entity, so both must produce objects.
        when(mapper.toResponse(any(Offering.class))).thenReturn(new com.shivang.crm.modules.catalog.dto.OfferingResponse());
        when(mapper.toEntity(any(OfferingCreateRequest.class))).thenAnswer(inv -> {
            OfferingCreateRequest request = inv.getArgument(0);
            Offering entity = new Offering();
            // MapStruct copies these across; the stub must as well, because
            // createOffering reads the code back off the mapped entity.
            entity.setName(request.getName());
            entity.setCode(request.getCode());
            entity.setDescription(request.getDescription());
            entity.setOfferingType(request.getOfferingType());
            entity.setBillingType(request.getBillingType());
            entity.setBillingInterval(request.getBillingInterval());
            entity.setDefaultPrice(request.getDefaultPrice());
            entity.setCurrencyCode(request.getCurrencyCode());
            entity.setDefaultTermDays(request.getDefaultTermDays());
            entity.setRenewable(request.getRenewable());
            return entity;
        });
        // MapStruct would apply the request's non-null fields; the mock must too,
        // otherwise every "update" looks like a no-op and the changed-field
        // detection under test never sees a change.
        org.mockito.Mockito.doAnswer(invocation -> {
            OfferingUpdateRequest request = invocation.getArgument(0);
            Offering target = invocation.getArgument(1);
            if (request.getName() != null) {
                target.setName(request.getName());
            }
            if (request.getDescription() != null) {
                target.setDescription(request.getDescription());
            }
            if (request.getOfferingType() != null) {
                target.setOfferingType(request.getOfferingType());
            }
            if (request.getBillingType() != null) {
                target.setBillingType(request.getBillingType());
            }
            if (request.getBillingInterval() != null) {
                target.setBillingInterval(request.getBillingInterval());
            }
            if (request.getDefaultPrice() != null) {
                target.setDefaultPrice(request.getDefaultPrice());
            }
            if (request.getCurrencyCode() != null) {
                target.setCurrencyCode(request.getCurrencyCode());
            }
            if (request.getDefaultTermDays() != null) {
                target.setDefaultTermDays(request.getDefaultTermDays());
            }
            if (request.getRenewable() != null) {
                target.setRenewable(request.getRenewable());
            }
            return null;
        }).when(mapper).updateEntity(any(OfferingUpdateRequest.class), any(Offering.class));

        service = new OfferingService(repository,
                mock(CustomerEntitlementRepository.class),
                mock(com.shivang.crm.modules.deal.repository.DealLineItemRepository.class),
                publisher, mapper, userRepository, activityService);

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        offeringId = UUID.randomUUID();

        offering = new Offering();
        offering.setId(offeringId);
        offering.setTenantId(tenantId);
        offering.setName("Plan");
        offering.setCode("PLAN");
        offering.setOfferingType(OfferingType.SUBSCRIPTION);
        offering.setBillingType(BillingType.ONE_TIME);
        offering.setActive(true);
        offering.setCurrencyCode("USD");

        // Owner validation resolves the actor, so the actor must look like a live
        // user of this tenant.
        var user = new com.shivang.crm.modules.auth.entity.User();
        user.setId(actorId);
        user.setTenantId(tenantId);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(actorId, tenantId))
                .thenReturn(Optional.of(user));

        when(repository.findByIdAndTenantIdAndDeletedFalse(offeringId, tenantId))
                .thenReturn(Optional.of(offering));
        when(repository.save(any(Offering.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private List0 published() {
        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(publisher, atLeast(0)).publish(any(UUID.class), entityType.capture(),
                eventType.capture(), any(), metadata.capture());
        return new List0(entityType.getAllValues(), eventType.getAllValues(), metadata.getAllValues());
    }

    private record List0(java.util.List<String> entityTypes, java.util.List<String> eventTypes,
            java.util.List<Map<String, Object>> metadatas) {
        java.util.List<String> names() {
            return java.util.stream.IntStream.range(0, entityTypes.size())
                    .mapToObj(i -> entityTypes.get(i) + "." + eventTypes.get(i))
                    .toList();
        }

        Map<String, Object> metadataOf(String name) {
            return metadatas.get(names().indexOf(name));
        }
    }

    private OfferingUpdateRequest updateRequest() {
        return new OfferingUpdateRequest();
    }

    // --- creation ---

    @Test
    void creatingAnOfferingPublishesCreated() {
        when(repository.existsByTenantIdAndCodeIgnoreCaseAndDeletedFalse(any(), any())).thenReturn(false);
        when(repository.save(any(Offering.class))).thenAnswer(inv -> {
            Offering saved = inv.getArgument(0);
            saved.setId(offeringId);
            return saved;
        });
        OfferingCreateRequest request = new OfferingCreateRequest();
        request.setName("Plan");
        request.setCode("PLAN");
        request.setOfferingType(OfferingType.SUBSCRIPTION);
        request.setBillingType(BillingType.ONE_TIME);
        request.setDefaultPrice(new BigDecimal("1000.00"));
        request.setCurrencyCode("USD");

        service.createOffering(tenantId, actorId, request);

        List0 events = published();
        assertThat(events.names()).containsExactly("OFFERING.CREATED");
        assertThat(events.metadataOf("OFFERING.CREATED"))
                .containsEntry("actorId", actorId.toString())
                .containsEntry("actorType", "USER")
                .containsEntry("code", "PLAN")
                .containsEntry("currencyCode", "USD");
    }

    @Test
    void creatingWithoutAnExplicitActiveFlagDefaultsToActive() {
        when(repository.existsByTenantIdAndCodeIgnoreCaseAndDeletedFalse(any(), any())).thenReturn(false);
        when(repository.save(any(Offering.class))).thenAnswer(inv -> inv.getArgument(0));
        OfferingCreateRequest request = new OfferingCreateRequest();
        request.setName("Plan");
        request.setCode("PLAN");
        request.setOfferingType(OfferingType.SUBSCRIPTION);
        request.setBillingType(BillingType.ONE_TIME);

        service.createOffering(tenantId, actorId, request);

        org.mockito.ArgumentCaptor<Offering> saved = org.mockito.ArgumentCaptor.forClass(Offering.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getActive()).isTrue();
        assertThat(published().metadataOf("OFFERING.CREATED")).containsEntry("active", true);
    }

    // --- plain update ---

    @Test
    void updatingANonLifecycleFieldPublishesUpdatedWithChangedFields() {
        OfferingUpdateRequest request = updateRequest();
        request.setName("Renamed plan");

        service.updateOffering(offeringId, tenantId, actorId, request);

        List0 events = published();
        assertThat(events.names()).containsExactly("OFFERING.UPDATED");
        assertThat(events.metadataOf("OFFERING.UPDATED"))
                .containsEntry("changedFields", java.util.List.of("name"))
                .containsEntry("actorId", actorId.toString());
    }

    @Test
    void anUpdateThatChangesNothingPublishesNothing() {
        service.updateOffering(offeringId, tenantId, actorId, updateRequest());

        assertThat(published().names()).isEmpty();
    }

    @Test
    void aPriceChangePublishesUpdatedNotALifecycleEvent() {
        OfferingUpdateRequest request = updateRequest();
        request.setDefaultPrice(new BigDecimal("2000.00"));

        service.updateOffering(offeringId, tenantId, actorId, request);

        assertThat(published().names()).containsExactly("OFFERING.UPDATED");
    }

    // --- lifecycle transitions ---

    @Test
    void activatingPublishesActivated() {
        offering.setActive(false);

        service.activateOffering(offeringId, tenantId, actorId);

        List0 events = published();
        assertThat(events.names()).containsExactly("OFFERING.ACTIVATED");
        assertThat(events.metadataOf("OFFERING.ACTIVATED"))
                .containsEntry("active", true)
                .containsEntry("actorType", "USER");
    }

    @Test
    void deactivatingPublishesDeactivated() {
        service.deactivateOffering(offeringId, tenantId, actorId);

        List0 events = published();
        assertThat(events.names()).containsExactly("OFFERING.DEACTIVATED");
        assertThat(events.metadataOf("OFFERING.DEACTIVATED")).containsEntry("active", false);
    }

    @Test
    void reActivatingAnAlreadyActiveOfferingPublishesNothing() {
        // No transition happened, so no lifecycle event is invented.
        offering.setActive(true);

        service.activateOffering(offeringId, tenantId, actorId);

        assertThat(published().names()).isEmpty();
    }

    @Test
    void deactivatingAnAlreadyInactiveOfferingPublishesNothing() {
        offering.setActive(false);

        service.deactivateOffering(offeringId, tenantId, actorId);

        assertThat(published().names()).isEmpty();
    }

    @Test
    void flippingActiveThroughUpdatePublishesTheLifecycleEventAndNotUpdated() {
        OfferingUpdateRequest request = updateRequest();
        request.setActive(false);

        service.updateOffering(offeringId, tenantId, actorId, request);

        assertThat(published().names()).containsExactly("OFFERING.DEACTIVATED");
    }

    @Test
    void aLifecycleFlipSupersedesUpdatedEvenWhenOtherFieldsAlsoChange() {
        // One mutation, one event. The lifecycle transition is the consequential
        // fact; a consumer needing field detail re-reads the entity.
        OfferingUpdateRequest request = updateRequest();
        request.setActive(false);
        request.setName("Also renamed");

        service.updateOffering(offeringId, tenantId, actorId, request);

        assertThat(published().names()).containsExactly("OFFERING.DEACTIVATED");
    }

    @Test
    void settingActiveToItsCurrentValueIsNotATransition() {
        OfferingUpdateRequest request = updateRequest();
        request.setActive(true);
        offering.setActive(true);
        request.setName("Renamed");

        service.updateOffering(offeringId, tenantId, actorId, request);

        assertThat(published().names()).containsExactly("OFFERING.UPDATED");
    }

    // --- metadata safety ---

    @Test
    void offeringEventsCarryNoSecretsOrEntityDumps() {
        OfferingUpdateRequest request = updateRequest();
        request.setName("Renamed");

        service.updateOffering(offeringId, tenantId, actorId, request);

        Map<String, Object> metadata = published().metadataOf("OFFERING.UPDATED");
        assertThat(metadata).doesNotContainKeys("password", "passwordHash", "credentials", "token", "secret");
        // Compact: identifiers and the change list only, never the whole entity.
        assertThat(metadata).containsOnlyKeys("actorId", "actorType", "source", "code",
                "currencyCode", "changedFields");
    }

    @Test
    void eventsUseTheTenantFromTheRequestNotAGlobalContext() {
        UUID otherTenant = UUID.randomUUID();
        when(repository.findByIdAndTenantIdAndDeletedFalse(offeringId, otherTenant))
                .thenReturn(Optional.empty());

        OfferingUpdateRequest request = updateRequest();
        request.setName("Renamed");
        service.updateOffering(offeringId, tenantId, actorId, request);

        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        verify(publisher, atLeast(1)).publish(tenant.capture(), anyString(), anyString(), any(), any());
        assertThat(tenant.getAllValues()).containsOnly(tenantId);
        assertThat(tenant.getAllValues()).doesNotContain(otherTenant);
    }

    @Test
    void aFailedLifecycleTransitionPublishesNothing() {
        when(repository.findByIdAndTenantIdAndDeletedFalse(offeringId, tenantId))
                .thenReturn(Optional.empty());

        var thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.activateOffering(offeringId, tenantId, actorId));

        assertThat(thrown).isNotNull();
        verifyNoInteractions(publisher);
    }

    @Test
    void billingIntervalIsPartOfTheChangedFieldSet() {
        // Billing interval is only legal on a recurring offering, so set one up.
        offering.setBillingType(BillingType.RECURRING);
        offering.setBillingInterval(BillingInterval.MONTHLY);
        OfferingUpdateRequest request = updateRequest();
        request.setBillingInterval(BillingInterval.YEARLY);

        service.updateOffering(offeringId, tenantId, actorId, request);

        assertThat(published().metadataOf("OFFERING.UPDATED"))
                .containsEntry("changedFields", java.util.List.of("billingInterval"));
    }

    @Test
    void switchingFromRecurringToOneTimeClearsTheIntervalAndPublishesOneUpdate() {
        offering.setBillingType(BillingType.RECURRING);
        offering.setBillingInterval(BillingInterval.MONTHLY);
        OfferingUpdateRequest request = updateRequest();
        request.setBillingType(BillingType.ONE_TIME);

        service.updateOffering(offeringId, tenantId, actorId, request);

        assertThat(offering.getBillingType()).isEqualTo(BillingType.ONE_TIME);
        assertThat(offering.getBillingInterval()).isNull();
        List0 events = published();
        assertThat(events.names()).containsExactly("OFFERING.UPDATED");
        assertThat((java.util.List<String>) events.metadataOf("OFFERING.UPDATED").get("changedFields"))
                .contains("billingType", "billingInterval");
    }

    @Test
    void changingTheOwnerPublishesAnUpdateAndTheOwnerIsPersisted() {
        UUID newOwner = UUID.randomUUID();
        var user = new com.shivang.crm.modules.auth.entity.User();
        user.setId(newOwner);
        user.setTenantId(tenantId);
        user.setIsActive(true);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(newOwner, tenantId))
                .thenReturn(Optional.of(user));
        OfferingUpdateRequest request = updateRequest();
        request.setOwnerUserId(newOwner);

        service.updateOffering(offeringId, tenantId, actorId, request);

        assertThat(offering.getOwnerId()).isEqualTo(newOwner);
        List0 events = published();
        assertThat(events.names()).containsExactly("OFFERING.UPDATED");
        assertThat((java.util.List<String>) events.metadataOf("OFFERING.UPDATED").get("changedFields"))
                .containsExactly("ownerId");
    }

    @Test
    void everyOfferingEventTargetsTheOfferingEntityType() {
        OfferingUpdateRequest request = updateRequest();
        request.setName("Renamed");
        service.updateOffering(offeringId, tenantId, actorId, request);

        List0 events = published();
        assertThat(events.entityTypes()).containsOnly("OFFERING");
        assertThat(events.metadataOf("OFFERING.UPDATED")).containsEntry("code", "PLAN");
    }
}
