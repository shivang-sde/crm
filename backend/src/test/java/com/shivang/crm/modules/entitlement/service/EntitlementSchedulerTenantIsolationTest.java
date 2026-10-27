package com.shivang.crm.modules.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.mockito.ArgumentCaptor;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;

/**
 * L3.0-P2: scheduled customer-domain operations must not mutate or resolve rows
 * across tenants.
 *
 * Both entitlement schedulers are tenant-agnostic at the query level by design:
 * they are platform jobs and must process every tenant. The invariant under test
 * is that each row's OWN tenantId is what reaches the mutation, the owner
 * lookup, and the activity write.
 */
class EntitlementSchedulerTenantIsolationTest {

    private CustomerEntitlementRepository entitlementRepository;
    private EntitlementExpiryActivityService expiryActivityService;
    private EntitlementExpiryService expiryService;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setup() {
        entitlementRepository = mock(CustomerEntitlementRepository.class);
        expiryActivityService = mock(EntitlementExpiryActivityService.class);
        expiryService = new EntitlementExpiryService(entitlementRepository, expiryActivityService,
                mock(com.shivang.crm.shared.event.CanonicalCrmEventPublisher.class));

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
    }

    private CustomerEntitlement entitlementIn(UUID tenant, UUID accountId, LocalDate endDate) {
        CustomerEntitlement entitlement = new CustomerEntitlement();
        entitlement.setId(UUID.randomUUID());
        entitlement.setTenantId(tenant);
        entitlement.setAccountId(accountId);
        entitlement.setName("Support Plan");
        entitlement.setStatus(EntitlementStatus.ACTIVE);
        entitlement.setStartDate(endDate.minusDays(30));
        entitlement.setEndDate(endDate);
        return entitlement;
    }

    @Test
    void expiryProcessesEntitlementsFromEveryTenantIndependently() {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        CustomerEntitlement forA = entitlementIn(tenantA, accountA, LocalDate.now().minusDays(1));
        CustomerEntitlement forB = entitlementIn(tenantB, accountB, LocalDate.now().minusDays(1));

        when(entitlementRepository.findByStatusAndEndDateBeforeAndDeletedFalse(
                EntitlementStatus.ACTIVE, LocalDate.now()))
                .thenReturn(List.of(forA, forB));
        when(entitlementRepository.save(any(CustomerEntitlement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(expiryService.expireDueEntitlements(LocalDate.now())).isEqualTo(2);

        // Each row is mutated in its own transaction scope; neither row's tenant
        // is used for the other.
        assertThat(forA.getTenantId()).isEqualTo(tenantA);
        assertThat(forB.getTenantId()).isEqualTo(tenantB);
        assertThat(forA.getStatus()).isEqualTo(EntitlementStatus.EXPIRED);
        assertThat(forB.getStatus()).isEqualTo(EntitlementStatus.EXPIRED);
    }

    @Test
    void expiryActivityReceivesTheEntitlementsOwnTenant() {
        UUID accountA = UUID.randomUUID();
        CustomerEntitlement forA = entitlementIn(tenantA, accountA, LocalDate.now().minusDays(1));

        when(entitlementRepository.findByStatusAndEndDateBeforeAndDeletedFalse(
                EntitlementStatus.ACTIVE, LocalDate.now()))
                .thenReturn(List.of(forA));
        when(entitlementRepository.save(any(CustomerEntitlement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        expiryService.expireDueEntitlements(LocalDate.now());

        // The activity writer receives the row and uses entitlement.getTenantId()
        // internally, so only the loaded row is ever handed over.
        verify(expiryActivityService).writeExpiredActivity(forA);
    }

    @Test
    void expirySkipsSoftDeletedEntitlementsViaRepositoryContract() {
        // findByStatusAndEndDateBeforeAndDeletedFalse already excludes deleted rows,
        // so a soft-deleted entitlement never reaches the loop.
        when(entitlementRepository.findByStatusAndEndDateBeforeAndDeletedFalse(
                EntitlementStatus.ACTIVE, LocalDate.now()))
                .thenReturn(List.of());

        assertThat(expiryService.expireDueEntitlements(LocalDate.now())).isZero();
        verify(entitlementRepository, never()).save(any(CustomerEntitlement.class));
        verify(expiryActivityService, never()).writeExpiredActivity(any(CustomerEntitlement.class));
    }

    @Test
    void oneFailingRowDoesNotStopOtherTenants() {
        CustomerEntitlement failing = entitlementIn(tenantA, UUID.randomUUID(), LocalDate.now().minusDays(1));
        CustomerEntitlement healthy = entitlementIn(tenantB, UUID.randomUUID(), LocalDate.now().minusDays(1));

        when(entitlementRepository.findByStatusAndEndDateBeforeAndDeletedFalse(
                EntitlementStatus.ACTIVE, LocalDate.now()))
                .thenReturn(List.of(failing, healthy));
        when(entitlementRepository.save(any(CustomerEntitlement.class)))
                .thenThrow(new RuntimeException("simulated failure on first row"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(expiryService.expireDueEntitlements(LocalDate.now())).isEqualTo(1);
        assertThat(healthy.getStatus()).isEqualTo(EntitlementStatus.EXPIRED);
    }

    // --- renewal follow-up ---

    @Test
    void renewalOwnerLookupIsScopedToTheEntitlementsTenant() {
        UserRepository userRepository = mock(UserRepository.class);
        EntitlementRenewalTaskPersistenceService persistenceService =
                new EntitlementRenewalTaskPersistenceService(
                        mock(com.shivang.crm.modules.task.repository.TaskRepository.class),
                        mock(com.shivang.crm.modules.task.service.TaskService.class),
                        userRepository,
                        mock(com.shivang.crm.modules.reminder.service.ReminderPlanningService.class));

        CustomerEntitlement forA = entitlementIn(tenantA, UUID.randomUUID(), LocalDate.now().plusDays(5));
        forA.setRenewalDueDate(LocalDate.now());
        forA.setOwnerId(UUID.randomUUID());
        forA.setRenewable(true);

        UUID ownerId = forA.getOwnerId();
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(eq(ownerId), eq(tenantA)))
                .thenReturn(Optional.empty());
        // Also resolvable for the wrong tenant, proving the lookup is tenant-keyed.
        User foreign = new User();
        foreign.setId(ownerId);
        foreign.setTenantId(tenantB);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(eq(ownerId), eq(tenantB)))
                .thenReturn(Optional.of(foreign));

        // The owner is not a live tenantA user, so no task is created.
        assertThat(persistenceService.createRenewalFollowUpIfMissing(forA)).isZero();

        ArgumentCaptor<UUID> tenantCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(userRepository).findByIdAndTenantIdAndDeletedFalse(eq(ownerId), tenantCaptor.capture());
        assertThat(tenantCaptor.getValue()).isEqualTo(tenantA);
    }

    @Test
    void renewalDedupLookupIsScopedToTheEntitlementsTenant() {
        com.shivang.crm.modules.task.repository.TaskRepository taskRepository =
                mock(com.shivang.crm.modules.task.repository.TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        com.shivang.crm.modules.task.service.TaskService taskService =
                mock(com.shivang.crm.modules.task.service.TaskService.class);
        EntitlementRenewalTaskPersistenceService persistenceService =
                new EntitlementRenewalTaskPersistenceService(
                        taskRepository,
                        taskService,
                        userRepository,
                        mock(com.shivang.crm.modules.reminder.service.ReminderPlanningService.class));

        CustomerEntitlement forB = entitlementIn(tenantB, UUID.randomUUID(), LocalDate.now().plusDays(5));
        forB.setRenewalDueDate(LocalDate.now());
        forB.setRenewable(true);
        UUID ownerId = UUID.randomUUID();
        forB.setOwnerId(ownerId);

        User owner = new User();
        owner.setId(ownerId);
        owner.setTenantId(tenantB);
        owner.setIsActive(true);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(ownerId, tenantB))
                .thenReturn(Optional.of(owner));
        when(taskRepository.findByTenantIdAndEntityTypeAndEntityId(any(), any(), any()))
                .thenReturn(List.of());
        when(taskService.createTaskInternal(any(), any(), any(), any()))
                .thenReturn(com.shivang.crm.modules.task.dto.TaskResponse.builder()
                        .id(UUID.randomUUID())
                        .build());

        persistenceService.createRenewalFollowUpIfMissing(forB);

        ArgumentCaptor<UUID> tenantCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(taskRepository).findByTenantIdAndEntityTypeAndEntityId(
                tenantCaptor.capture(), eq("ENTITLEMENT"), eq(forB.getId()));
        assertThat(tenantCaptor.getValue()).isEqualTo(tenantB);
    }
}