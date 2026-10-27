package com.shivang.crm.modules.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.entitlement.dto.CustomerEntitlementUpdateRequest;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.mapper.CustomerEntitlementMapper;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.exception.PermissionDeniedException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.0-P3: entitlement assignment is authorization-separated from generic
 * entitlement write.
 *
 * The real RecordScopeGuard is wired against a mocked PermissionEvaluatorService,
 * so the actual scope resolution and fail-closed path are exercised.
 */
class EntitlementAssignmentAuthorizationTest {

    private CustomerEntitlementRepository entitlementRepository;
    private PermissionEvaluatorService permissionEvaluatorService;
    private UserRepository userRepository;
    private CustomerEntitlementService service;

    private UUID tenantId;
    private UUID userId;
    private UUID entitlementId;
    private UUID ownerId;

    @BeforeEach
    void setup() {
        entitlementRepository = mock(CustomerEntitlementRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        userRepository = mock(UserRepository.class);

        AccountRepository accountRepository = mock(AccountRepository.class);
        ContactRepository contactRepository = mock(ContactRepository.class);
        DealRepository dealRepository = mock(DealRepository.class);

        RecordScopeGuard guard = new RecordScopeGuard(permissionEvaluatorService, userRepository);

        service = new CustomerEntitlementService(
                entitlementRepository,
                mock(CustomerEntitlementMapper.class),
                dealRepository,
                mock(DealLineItemRepository.class),
                mock(OfferingRepository.class),
                mock(ActivityService.class),
                guard,
new CustomerRelationshipValidator(
      accountRepository, contactRepository, dealRepository,
      mock(LeadRepository.class), mock(TicketRepository.class), userRepository),
                mock(com.shivang.crm.shared.event.CanonicalCrmEventPublisher.class));

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        entitlementId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        when(entitlementRepository.save(any(CustomerEntitlement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(permissionEvaluatorService.getTeamUserIds(userId, tenantId))
                .thenReturn(java.util.List.of());

        ownedEntitlement(ownerId);
    }

    private void grantScope(String action, String scope) {
        when(permissionEvaluatorService.getAccessScope(userId, tenantId, "entitlement", action))
                .thenReturn(scope);
    }

    private CustomerEntitlement ownedEntitlement(UUID owner) {
        CustomerEntitlement entitlement = new CustomerEntitlement();
        entitlement.setId(entitlementId);
        entitlement.setTenantId(tenantId);
        entitlement.setName("Support Plan");
        entitlement.setStatus(EntitlementStatus.ACTIVE);
        entitlement.setOwnerId(owner);
        entitlement.setCreatedBy(owner);
        when(entitlementRepository.findByIdAndTenantIdAndDeletedFalse(entitlementId, tenantId))
                .thenReturn(Optional.of(entitlement));
        return entitlement;
    }

    private CustomerEntitlementUpdateRequest requestWithOwner(UUID newOwnerId) {
        CustomerEntitlementUpdateRequest request = new CustomerEntitlementUpdateRequest();
        request.setOwnerUserId(newOwnerId);
        return request;
    }

    private UUID liveUser() {
        UUID id = UUID.randomUUID();
        var user = new com.shivang.crm.modules.auth.entity.User();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setIsActive(true);
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId))
                .thenReturn(Optional.of(user));
        return id;
    }

    // --- the separation P3 establishes ---

    @Test
    void entitlementWriteAloneCannotChangeOwner() {
        grantScope("write", "OWN");
        grantScope("assign", "NONE");

        Throwable thrown = catchThrowable(() ->
                service.update(entitlementId, tenantId, userId, requestWithOwner(UUID.randomUUID())));

        assertThat(thrown).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void entitlementWriteAloneCannotChangeOwnerEvenWithAllWriteScope() {
        grantScope("write", "ALL");
        grantScope("assign", "NONE");

        assertThat(catchThrowable(() ->
                service.update(entitlementId, tenantId, userId, requestWithOwner(UUID.randomUUID()))))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- assignment permitted ---

    @Test
    void allScopedAssignPermissionCanChangeOwner() {
        grantScope("write", "ALL");
        grantScope("assign", "ALL");

        UUID newOwner = liveUser();
        assertThatNoException().isThrownBy(() ->
                service.update(entitlementId, tenantId, userId, requestWithOwner(newOwner)));
    }

    @Test
    void ownScopedAssignPermissionCanReassignOwnEntitlement() {
        ownedEntitlement(userId);
        grantScope("write", "OWN");
        grantScope("assign", "OWN");

        UUID newOwner = liveUser();
        assertThatNoException().isThrownBy(() ->
                service.update(entitlementId, tenantId, userId, requestWithOwner(newOwner)));
    }

    @Test
    void assignmentRejectsAForeignOwner() {
        grantScope("write", "ALL");
        grantScope("assign", "ALL");

        UUID foreignOwner = UUID.randomUUID();
        var foreign = new com.shivang.crm.modules.auth.entity.User();
        foreign.setId(foreignOwner);
        foreign.setTenantId(UUID.randomUUID());
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(foreignOwner, tenantId))
                .thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() ->
                service.update(entitlementId, tenantId, userId, requestWithOwner(foreignOwner)));

        assertThat(thrown).isInstanceOf(com.shivang.crm.shared.exception.BusinessException.class);
        assertThat(((com.shivang.crm.shared.exception.BusinessException) thrown).getErrorCode())
                .isEqualTo("OWNER_NOT_FOUND");
    }

    @Test
    void assignScopeStillRespectsRecordScope() {
        // Caller may assign globally, but the entitlement is owned by someone else
        // and the caller only has OWN record scope for write.
        grantScope("write", "OWN");
        grantScope("assign", "ALL");

        assertThat(catchThrowable(() ->
                service.update(entitlementId, tenantId, userId, requestWithOwner(UUID.randomUUID()))))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- generic write is unaffected ---

    @Test
    void entitlementWriteStillUpdatesUnrelatedFields() {
        grantScope("write", "ALL");
        grantScope("assign", "NONE");

        CustomerEntitlementUpdateRequest request = new CustomerEntitlementUpdateRequest();
        request.setName("Renamed Plan");
        request.setQuantity(new java.math.BigDecimal("5"));

        assertThatNoException().isThrownBy(() -> service.update(entitlementId, tenantId, userId, request));
    }

    @Test
    void entitlementWriteStillUpdatesUnrelatedFieldsUnderOwnScope() {
        ownedEntitlement(userId);
        grantScope("write", "OWN");
        grantScope("assign", "NONE");

        CustomerEntitlementUpdateRequest request = new CustomerEntitlementUpdateRequest();
        request.setDescription("Updated");

        assertThatNoException().isThrownBy(() -> service.update(entitlementId, tenantId, userId, request));
    }

    // --- no-op assignment does not require assign ---

    @Test
    void settingTheSameOwnerDoesNotRequireAssignPermission() {
        grantScope("write", "ALL");
        grantScope("assign", "NONE");

        // Same owner as current: not a reassignment.
        assertThatNoException().isThrownBy(() ->
                service.update(entitlementId, tenantId, userId, requestWithOwner(ownerId)));
    }

    @Test
    void omittingOwnerDoesNotRequireAssignPermission() {
        grantScope("write", "ALL");
        grantScope("assign", "NONE");

        CustomerEntitlementUpdateRequest request = new CustomerEntitlementUpdateRequest();
        request.setName("Renamed");

        assertThatNoException().isThrownBy(() -> service.update(entitlementId, tenantId, userId, request));
    }

    // --- status transitions remain write-scoped, not assign-scoped ---

    @Test
    void statusTransitionsDoNotRequireAssignPermission() {
        ownedEntitlement(userId);
        grantScope("write", "OWN");
        grantScope("assign", "NONE");

        // A PENDING entitlement can be activated by write alone.
        CustomerEntitlement entitlement = ownedEntitlement(userId);
        entitlement.setStatus(EntitlementStatus.PENDING);

        assertThatNoException().isThrownBy(() -> service.activate(entitlementId, tenantId, userId));
    }

    // --- P2 tenant owner validation is preserved ---

    @Test
    void crossTenantOwnerStillRejectedUnderTenantLifecycleAutomation() {
        // P2's CustomerRelationshipValidator.requireTenantOwner guards the
        // provisioning path; assert the validator itself still rejects it.
CustomerRelationshipValidator validator = new CustomerRelationshipValidator(
                mock(AccountRepository.class), mock(ContactRepository.class),
                mock(DealRepository.class), mock(LeadRepository.class),
                mock(TicketRepository.class), userRepository);

        UUID foreignOwner = UUID.randomUUID();
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(foreignOwner, tenantId))
                .thenReturn(Optional.empty());

        assertThat(catchThrowable(() -> validator.requireTenantOwner(foreignOwner, tenantId)))
                .isInstanceOf(com.shivang.crm.shared.exception.BusinessException.class);
    }

    @Test
    void recordScopeStillRequiredBeforeAnyUpdate() {
        grantScope("write", "NONE");
        grantScope("assign", "ALL");

        CustomerEntitlementUpdateRequest request = new CustomerEntitlementUpdateRequest();
        request.setName("Renamed");

        assertThat(catchThrowable(() -> service.update(entitlementId, tenantId, userId, request)))
                .isInstanceOf(PermissionDeniedException.class);
    }
}