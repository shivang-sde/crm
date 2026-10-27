package com.shivang.crm.modules.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.0-P4-D: an Offering that underpins live customer coverage cannot be deleted.
 */
class OfferingDeletionLifecycleTest {

    private OfferingRepository offeringRepository;
    private CustomerEntitlementRepository entitlementRepository;
    private DealLineItemRepository dealLineItemRepository;
    private OfferingService service;

    private UUID tenantId;
    private UUID actorId;
    private UUID offeringId;

    @BeforeEach
    void setup() {
        offeringRepository = mock(OfferingRepository.class);
        entitlementRepository = mock(CustomerEntitlementRepository.class);
        dealLineItemRepository = mock(DealLineItemRepository.class);
        com.shivang.crm.modules.catalog.mapper.OfferingMapper offeringMapper =
                mock(com.shivang.crm.modules.catalog.mapper.OfferingMapper.class);
        UserRepository userRepository = mock(UserRepository.class);
        ActivityService activityService = mock(ActivityService.class);

// Field order follows the service: offeringRepository,
        // entitlementRepository, dealLineItemRepository,
        // canonicalCrmEventPublisher, offeringMapper, userRepository,
        // activityService.
        service = new OfferingService(
                offeringRepository, entitlementRepository, dealLineItemRepository,
                mock(com.shivang.crm.shared.event.CanonicalCrmEventPublisher.class),
                offeringMapper, userRepository, activityService);

        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        offeringId = UUID.randomUUID();

        Offering offering = new Offering();
        offering.setId(offeringId);
        offering.setTenantId(tenantId);
        offering.setName("Plan");
        offering.setCode("PLAN");
        offering.setOfferingType(OfferingType.SUBSCRIPTION);
        offering.setBillingType(BillingType.ONE_TIME);
        offering.setActive(true);
        when(offeringRepository.findByIdAndTenantIdAndDeletedFalse(offeringId, tenantId))
                .thenReturn(Optional.of(offering));

        when(offeringRepository.save(any(Offering.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entitlementRepository.existsByTenantIdAndOfferingIdAndStatusInAndDeletedFalse(
                any(), any(), any())).thenReturn(false);
    }

    private void liveEntitlementPresent() {
        when(entitlementRepository.existsByTenantIdAndOfferingIdAndStatusInAndDeletedFalse(
                any(), any(), any())).thenReturn(true);
    }

    private void assertCode(String expected, ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    // --- deletion allowed ---

    @Test
    void offeringWithNoEntitlementsCanBeDeleted() {
        assertThatNoException().isThrownBy(() -> service.deleteOffering(offeringId, tenantId, actorId));
        verify(offeringRepository).save(any(Offering.class));
    }

    /**
     * Terminal coverages (EXPIRED / CANCELLED / RENEWED / TERMINATED) stay
     * queryable but do not represent live coverage, so they must not block
     * catalog cleanup.
     */
    @Test
    void offeringWithOnlyTerminalEntitlementsCanBeDeleted() {
        assertThat(OfferingService.LIVE_ENTITLEMENT_STATUSES)
                .containsExactly(EntitlementStatus.PENDING, EntitlementStatus.ACTIVE, EntitlementStatus.SUSPENDED)
                .doesNotContain(EntitlementStatus.EXPIRED,
                        EntitlementStatus.CANCELLED,
                        EntitlementStatus.RENEWED,
                        EntitlementStatus.TERMINATED);

        assertThatNoException().isThrownBy(() -> service.deleteOffering(offeringId, tenantId, actorId));
        verify(offeringRepository).save(any(Offering.class));
    }

    // --- deletion rejected ---

    @Test
    void offeringWithLiveEntitlementCannotBeDeleted() {
        liveEntitlementPresent();

        assertCode("OFFERING_IN_USE", () -> service.deleteOffering(offeringId, tenantId, actorId));
        verify(offeringRepository, never()).save(any(Offering.class));
    }

    @Test
    void offeringReferencedByAnOpenDealLineCannotBeDeleted() {
        when(dealLineItemRepository.existsOpenDealLineItemByTenantIdAndOfferingId(tenantId, offeringId))
                .thenReturn(true);

        assertCode("OFFERING_IN_USE", () -> service.deleteOffering(offeringId, tenantId, actorId));
        verify(offeringRepository, never()).save(any(Offering.class));
    }

    // --- tenant scoping ---

    @Test
    void anotherTenantsEntitlementDoesNotBlockDeletion() {
        // Only tenantB has a live entitlement for this offering id.
        when(entitlementRepository.existsByTenantIdAndOfferingIdAndStatusInAndDeletedFalse(
                org.mockito.ArgumentMatchers.eq(tenantId), any(), any())).thenReturn(false);
        when(entitlementRepository.existsByTenantIdAndOfferingIdAndStatusInAndDeletedFalse(
                org.mockito.ArgumentMatchers.eq(UUID.randomUUID()), any(), any())).thenReturn(true);

        assertThatNoException().isThrownBy(() -> service.deleteOffering(offeringId, tenantId, actorId));
    }

    @Test
    void deletionLookupIsTenantAndOfferingKeyed() {
        service.deleteOffering(offeringId, tenantId, actorId);

        verify(entitlementRepository).existsByTenantIdAndOfferingIdAndStatusInAndDeletedFalse(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(offeringId),
                org.mockito.ArgumentMatchers.eq(OfferingService.LIVE_ENTITLEMENT_STATUSES));
    }

    // --- entitlements are never mutated to permit deletion ---

    @Test
    void entitlementsAreNeverMutatedToPermitDeletion() {
        liveEntitlementPresent();

        catchThrowable(() -> service.deleteOffering(offeringId, tenantId, actorId));

        verify(entitlementRepository, never()).save(any());
        verify(entitlementRepository, never())
                .delete(any(com.shivang.crm.modules.entitlement.entity.CustomerEntitlement.class));
    }

    @Test
    void noOfferingIsFoundInAnotherTenant() {
        UUID foreignTenant = UUID.randomUUID();
        when(offeringRepository.findByIdAndTenantIdAndDeletedFalse(offeringId, foreignTenant))
                .thenReturn(Optional.empty());

        assertCode("NOT_FOUND", () -> service.deleteOffering(offeringId, foreignTenant, actorId));
    }
}