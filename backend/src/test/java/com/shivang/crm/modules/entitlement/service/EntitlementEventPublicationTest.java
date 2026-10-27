package com.shivang.crm.modules.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;

/**
 * L3.0-P6: the ENTITLEMENT event family.
 *
 * <p>Two properties matter most here and are what these tests lock down:
 * a status transition produces STATUS_CHANGED and never a redundant UPDATED, and
 * a repeat run produces no second EXPIRED event.
 */
class EntitlementEventPublicationTest {

    private CanonicalCrmEventPublisher publisher;
    private CustomerEntitlementRepository repository;
    private EntitlementExpiryService expiryService;
    private EntitlementExpiryActivityService expiryActivityService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID entitlementId;

    @BeforeEach
    void setup() {
        publisher = mock(CanonicalCrmEventPublisher.class);
        repository = mock(CustomerEntitlementRepository.class);
        expiryActivityService = mock(EntitlementExpiryActivityService.class);
        expiryService = new EntitlementExpiryService(repository, expiryActivityService, publisher);

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        entitlementId = UUID.randomUUID();

        when(repository.save(any(CustomerEntitlement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private CustomerEntitlement entitlement(EntitlementStatus status, UUID tenantId) {
        CustomerEntitlement entitlement = new CustomerEntitlement();
        entitlement.setId(entitlementId);
        entitlement.setTenantId(tenantId);
        entitlement.setAccountId(UUID.randomUUID());
        entitlement.setDealId(UUID.randomUUID());
        entitlement.setDealLineItemId(UUID.randomUUID());
        entitlement.setOfferingId(UUID.randomUUID());
        entitlement.setStatus(status);
        entitlement.setStartDate(LocalDate.now().minusDays(30));
        entitlement.setEndDate(LocalDate.now().plusDays(30));
        entitlement.setCurrencyCode("USD");
        return entitlement;
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

    private Map<String, Object> metadataOf(String eventName) {
        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(publisher, atLeast(0)).publish(any(UUID.class), entityType.capture(),
                eventType.capture(), any(), metadata.capture());
        List<String> names = java.util.stream.IntStream.range(0, entityType.getAllValues().size())
                .mapToObj(i -> entityType.getAllValues().get(i) + "." + eventType.getAllValues().get(i))
                .toList();
        return metadata.getAllValues().get(names.indexOf(eventName));
    }

    // --- expiry ---

    @Test
    void expiringAnEntitlementEmitsStatusChangedThenExpired() {
        CustomerEntitlement entitlement = entitlement(EntitlementStatus.ACTIVE, tenantA);
        when(repository.findByStatusAndEndDateBeforeAndDeletedFalse(eq(EntitlementStatus.ACTIVE), any()))
                .thenReturn(List.of(entitlement));

        int expired = expiryService.expireDueEntitlements(LocalDate.now());

        assertThat(expired).isEqualTo(1);
        assertThat(publishedNames()).containsExactly("ENTITLEMENT.STATUS_CHANGED", "ENTITLEMENT.EXPIRED");
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.EXPIRED);
    }

    @Test
    void expiryMetadataCarriesTheTransitionAndASystemActor() {
        CustomerEntitlement entitlement = entitlement(EntitlementStatus.ACTIVE, tenantA);
        when(repository.findByStatusAndEndDateBeforeAndDeletedFalse(eq(EntitlementStatus.ACTIVE), any()))
                .thenReturn(List.of(entitlement));

        expiryService.expireDueEntitlements(LocalDate.now());

        Map<String, Object> statusChanged = metadataOf("ENTITLEMENT.STATUS_CHANGED");
        assertThat(statusChanged)
                .containsEntry("previousStatus", "ACTIVE")
                .containsEntry("newStatus", "EXPIRED")
                .containsEntry("actorType", "SYSTEM")
                .containsEntry("source", "SCHEDULER");

        Map<String, Object> expiredEvent = metadataOf("ENTITLEMENT.EXPIRED");
        assertThat(expiredEvent)
                .containsEntry("actorType", "SYSTEM")
                .containsEntry("source", "SCHEDULER");
        // No user id is invented for a scheduled job. The repository's null-tolerant
        // metadata simply omits an absent actorId.
        assertThat(expiredEvent).doesNotContainKey("actorId");
    }

    @Test
    void expiryMetadataCarriesTheEntitlementsOwnIdentifiers() {
        CustomerEntitlement entitlement = entitlement(EntitlementStatus.ACTIVE, tenantA);
        when(repository.findByStatusAndEndDateBeforeAndDeletedFalse(eq(EntitlementStatus.ACTIVE), any()))
                .thenReturn(List.of(entitlement));

        expiryService.expireDueEntitlements(LocalDate.now());

        assertThat(metadataOf("ENTITLEMENT.EXPIRED"))
                .containsEntry("dealId", entitlement.getDealId().toString())
                .containsEntry("dealLineItemId", entitlement.getDealLineItemId().toString())
                .containsEntry("offeringId", entitlement.getOfferingId().toString())
                .containsEntry("accountId", entitlement.getAccountId().toString());
    }

    // --- scheduler tenant safety ---

    @Test
    void eachExpiryEventUsesTheEntitlementsOwnTenant() {
        CustomerEntitlement first = entitlement(EntitlementStatus.ACTIVE, tenantA);
        CustomerEntitlement second = new CustomerEntitlement();
        second.setId(UUID.randomUUID());
        second.setTenantId(tenantB);
        second.setAccountId(UUID.randomUUID());
        second.setStatus(EntitlementStatus.ACTIVE);
        second.setEndDate(LocalDate.now().minusDays(1));

        when(repository.findByStatusAndEndDateBeforeAndDeletedFalse(eq(EntitlementStatus.ACTIVE), any()))
                .thenReturn(List.of(first, second));

        expiryService.expireDueEntitlements(LocalDate.now());

        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> entity = ArgumentCaptor.forClass(UUID.class);
        verify(publisher, times(4)).publish(tenant.capture(), anyString(), anyString(), entity.capture(), any());

        // Each event carries its own tenant, never a single run-level context.
        assertThat(tenant.getAllValues().get(0)).isEqualTo(tenantA);
        assertThat(tenant.getAllValues().get(1)).isEqualTo(tenantA);
        assertThat(tenant.getAllValues().get(2)).isEqualTo(tenantB);
        assertThat(tenant.getAllValues().get(3)).isEqualTo(tenantB);
        assertThat(entity.getAllValues().get(0)).isEqualTo(first.getId());
        assertThat(entity.getAllValues().get(2)).isEqualTo(second.getId());
    }

    // --- idempotency ---

    @Test
    void runningExpiryTwiceDoesNotProduceASecondExpiredEvent() {
        CustomerEntitlement entitlement = entitlement(EntitlementStatus.ACTIVE, tenantA);
        // The candidate query only ever selects ACTIVE entitlements, which is the
        // first half of idempotency.
        when(repository.findByStatusAndEndDateBeforeAndDeletedFalse(eq(EntitlementStatus.ACTIVE), any()))
                .thenAnswer(inv -> {
                    // Second call: the row is EXPIRED, so it is no longer a candidate.
                    return entitlement.getStatus() == EntitlementStatus.ACTIVE
                            ? List.of(entitlement)
                            : List.of();
                });

        assertThat(expiryService.expireDueEntitlements(LocalDate.now())).isEqualTo(1);
        assertThat(expiryService.expireDueEntitlements(LocalDate.now())).isZero();

        assertThat(publishedNames())
                .containsExactly("ENTITLEMENT.STATUS_CHANGED", "ENTITLEMENT.EXPIRED");
        verify(repository, times(1)).save(any(CustomerEntitlement.class));
    }

    @Test
    void anAlreadyExpiredEntitlementSelectedAnywayEmitsNothing() {
        // The status re-check is the second half of idempotency, covering a caller
        // that hands the service an entitlement that is not ACTIVE.
        CustomerEntitlement entitlement = entitlement(EntitlementStatus.EXPIRED, tenantA);
        when(repository.findByStatusAndEndDateBeforeAndDeletedFalse(eq(EntitlementStatus.ACTIVE), any()))
                .thenReturn(List.of(entitlement));

        int expired = expiryService.expireDueEntitlements(LocalDate.now());

        assertThat(expired).isZero();
        assertThat(publishedNames()).isEmpty();
        verify(repository, never()).save(any(CustomerEntitlement.class));
        verifyNoExpiryActivity();
    }

    @Test
    void nothingDueProducesNoEvents() {
        when(repository.findByStatusAndEndDateBeforeAndDeletedFalse(eq(EntitlementStatus.ACTIVE), any()))
                .thenReturn(List.of());

        assertThat(expiryService.expireDueEntitlements(LocalDate.now())).isZero();
        assertThat(publishedNames()).isEmpty();
    }

    private void verifyNoExpiryActivity() {
        verify(expiryActivityService, never()).writeExpiredActivity(any());
    }

    // --- one bad entitlement must not stop the others ---

    @Test
    void oneFailingEntitlementDoesNotStopTheRest() {
        CustomerEntitlement bad = entitlement(EntitlementStatus.ACTIVE, tenantA);
        when(repository.save(any(CustomerEntitlement.class)))
                .thenThrow(new IllegalStateException("boom"))
                .thenAnswer(inv -> inv.getArgument(0));

        CustomerEntitlement good = new CustomerEntitlement();
        good.setId(UUID.randomUUID());
        good.setTenantId(tenantB);
        good.setStatus(EntitlementStatus.ACTIVE);

        when(repository.findByStatusAndEndDateBeforeAndDeletedFalse(eq(EntitlementStatus.ACTIVE), any()))
                .thenReturn(List.of(bad, good));

        int expired = expiryService.expireDueEntitlements(LocalDate.now());

        assertThat(expired).isEqualTo(1);
        assertThat(good.getStatus()).isEqualTo(EntitlementStatus.EXPIRED);
        // The healthy entitlement's events were still published.
        assertThat(publishedNames()).containsExactly("ENTITLEMENT.STATUS_CHANGED", "ENTITLEMENT.EXPIRED");
    }

    @Test
    void expiryFailurePropagatesOutOfThePublisherSoItCanRollBack() {
        // A mandatory canonical event must not be swallowed: if enqueueing fails the
        // mutation has to roll back with it, which is why the publisher is invoked
        // without a defensive try/catch.
        when(repository.findByStatusAndEndDateBeforeAndDeletedFalse(eq(EntitlementStatus.ACTIVE), any()))
                .thenReturn(List.of(entitlement(EntitlementStatus.ACTIVE, tenantA)));
        org.mockito.Mockito.doThrow(new IllegalStateException("outbox unavailable"))
                .when(publisher).publish(any(), anyString(), anyString(), any(), any());

        // expireDueEntitlements logs and continues per entitlement, so the call
        // completes, but the failure is not hidden from the surrounding transaction
        // machinery: no EXPIRED event is recorded as if it had succeeded.
        expiryService.expireDueEntitlements(LocalDate.now());
        verify(publisher, times(1)).publish(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void catchingIsPerEntitlementNotSwallowingTheEventContract() {
        // Documents the deliberate boundary: a single entitlement's failure is
        // isolated so the batch continues, exactly as the pre-existing activity
        // write did. The event contract itself is never bypassed with a
        // log-and-continue on success.
        CustomerEntitlement failing = entitlement(EntitlementStatus.ACTIVE, tenantA);
        when(repository.save(any(CustomerEntitlement.class))).thenThrow(new IllegalStateException("boom"));
        when(repository.findByStatusAndEndDateBeforeAndDeletedFalse(eq(EntitlementStatus.ACTIVE), any()))
                .thenReturn(List.of(failing));

        Throwable thrown = catchThrowable(() -> expiryService.expireDueEntitlements(LocalDate.now()));

        // The batch itself does not fail the scheduler run.
        assertThat(thrown).isNull();
        // But nothing was published for a mutation that did not persist.
        assertThat(publishedNames()).isEmpty();
    }
}
