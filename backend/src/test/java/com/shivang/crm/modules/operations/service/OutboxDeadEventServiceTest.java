package com.shivang.crm.modules.operations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.shivang.crm.modules.operations.config.OperatorToolsProperties;
import com.shivang.crm.modules.operations.dto.OutboxDeadEventView;
import com.shivang.crm.modules.operations.enums.OperatorActionResult;
import com.shivang.crm.shared.event.OutboxEvent;
import com.shivang.crm.shared.event.OutboxEventClaimService;
import com.shivang.crm.shared.event.OutboxEventRepository;
import com.shivang.crm.shared.event.OutboxEventStatus;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;
import com.shivang.crm.shared.exception.PermissionDeniedException;

class OutboxDeadEventServiceTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    private OutboxEventRepository repository;
    private OutboxEventClaimService claimService;
    private OperatorActionAuditService auditService;
    private OperatorScopeResolver scope;
    private OperatorToolsProperties properties;
    private OutboxDeadEventService service;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        claimService = mock(OutboxEventClaimService.class);
        auditService = mock(OperatorActionAuditService.class);
        scope = mock(OperatorScopeResolver.class);
        properties = new OperatorToolsProperties();
        properties.setEnabled(true);
        properties.getOutbox().setPageSize(20);
        properties.getOutbox().setMaxPageSize(200);

        when(auditService.record(any()))
                .thenReturn(new com.shivang.crm.modules.operations.entity.OperatorActionRecord());
        when(scope.userId()).thenReturn(UUID.randomUUID());
        when(scope.role()).thenReturn("ADMIN");
        when(scope.isPlatformScope()).thenReturn(false);
        when(scope.tenantId()).thenReturn(TENANT_A);
        when(scope.canAccess(any())).thenAnswer(i -> TENANT_A.equals(i.getArgument(0)));

        service = new OutboxDeadEventService(repository, claimService, auditService, scope, properties);
    }

    // ── 1/2. listing and pagination ─────────────────────────────────────────

    @Test
    void tenantListingQueriesOnlyItsOwnTenant() {
        when(repository.findDeadForTenant(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deadRow(EVENT_ID, TENANT_A))));

        service.listDead(0, 20);

        verify(repository).findDeadForTenant(org.mockito.ArgumentMatchers.eq(TENANT_A), any(Pageable.class));
        verify(repository, never()).findDeadPlatformWide(any(Pageable.class));
    }

    @Test
    void platformListingQueriesEveryTenant() {
        when(scope.isPlatformScope()).thenReturn(true);
        when(repository.findDeadPlatformWide(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deadRow(EVENT_ID, TENANT_A), deadRow(UUID.randomUUID(), TENANT_B))));

        var page = service.listDead(0, 20);

        assertThat(page.getTotalElements()).isEqualTo(2);
        verify(repository).findDeadPlatformWide(any(Pageable.class));
    }

    @Test
    void pageSizeIsClampedToTheConfiguredMaximum() {
        when(repository.findDeadForTenant(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listDead(0, 100_000);

        ArgumentCaptorHelper.assertPageSize(repository, 200);
    }

    // ── 3. safe metadata returned ───────────────────────────────────────────

    @Test
    void safeMetadataIsReturnedWithoutThePayload() {
        when(repository.findDeadForTenant(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deadRow(EVENT_ID, TENANT_A))));

        OutboxDeadEventView view = service.listDead(0, 20).getContent().get(0);

        assertThat(view.getEventId()).isEqualTo(EVENT_ID);
        assertThat(view.getTenantId()).isEqualTo(TENANT_A);
        assertThat(view.getEntityType()).isEqualTo("LEAD");
        assertThat(view.getEventType()).isEqualTo("CREATED");
        assertThat(view.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(view.getAttempts()).isEqualTo(10);
        assertThat(view.getDeadAt()).isNotNull();
        assertThat(view.isPayloadAvailable()).isTrue();
        assertThat(view.isRequeueable()).isTrue();
    }

    @Test
    void viewHasNoFieldThatCouldCarryThePayload() {
        assertThat(OutboxDeadEventView.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("payload", "body", "metadata", "rawPayload");
    }

    // ── 4. last error is sanitised ──────────────────────────────────────────

    @Test
    void lastErrorIsSanitisedAndCapped() {
        OutboxEvent row = deadRow(EVENT_ID, TENANT_A);
        row.setLastError("connect failed password=hunter2 token=abcdef and " + "x".repeat(2_000));
        when(repository.findDeadForTenant(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        String error = service.listDead(0, 20).getContent().get(0).getLastError();

        assertThat(error).doesNotContain("hunter2").doesNotContain("abcdef");
        assertThat(error.length())
                .as("capped at 500 characters plus the truncation marker")
                .isLessThanOrEqualTo(514);
    }

    // ── 5/6. authorization ──────────────────────────────────────────────────

    @Test
    void tenantCannotSeeAnotherTenantsEvent() {
        when(repository.findByIdAndStatusAndDeletedFalse(EVENT_ID, OutboxEventStatus.DEAD))
                .thenReturn(Optional.of(deadRow(EVENT_ID, TENANT_B)));
        when(scope.canAccess(TENANT_B)).thenReturn(false);

        assertThatThrownBy(() -> service.findById(EVENT_ID))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void unauthorizedRequeueIsAudited() {
        when(repository.findByIdAndStatusAndDeletedFalse(EVENT_ID, OutboxEventStatus.DEAD))
                .thenReturn(Optional.of(deadRow(EVENT_ID, TENANT_B)));
        when(scope.canAccess(TENANT_B)).thenReturn(false);

        assertThatThrownBy(() -> service.requeue(EVENT_ID, "req-1"))
                .isInstanceOf(PermissionDeniedException.class);
        verify(claimService, never()).requeueDead(any());
        verify(auditService).record(org.mockito.ArgumentMatchers.argThat(
                e -> e.getResult() == OperatorActionResult.NOT_ALLOWED));
    }

    @Test
    void unknownEventIsNotFound() {
        when(repository.findByIdAndStatusAndDeletedFalse(any(), any(OutboxEventStatus.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(EVENT_ID)).isInstanceOf(NotFoundException.class);
    }

    // ── 7. explicit requeue reuses the existing transition ──────────────────

    @Test
    void requeueDelegatesToTheExistingGuardedTransition() {
        UUID rowId = UUID.randomUUID();
        when(repository.findByIdAndStatusAndDeletedFalse(EVENT_ID, OutboxEventStatus.DEAD))
                .thenReturn(Optional.of(deadRow(rowId, TENANT_A)));
        when(claimService.requeueDead(EVENT_ID)).thenReturn(true);
        when(repository.findById(EVENT_ID))
                .thenReturn(Optional.of(requeuedRow(rowId, TENANT_A)));

        service.requeue(EVENT_ID, "req-1");

        verify(claimService).requeueDead(EVENT_ID);
    }

    @Test
    void requeueDoesNotTouchAttemptsOrHistory() {
        UUID rowId = UUID.randomUUID();
        OutboxEvent dead = deadRow(rowId, TENANT_A);
        dead.setAttempts(10);
        dead.setLastError("broker down");
        Instant deadAt = dead.getDeadAt();

        when(repository.findByIdAndStatusAndDeletedFalse(EVENT_ID, OutboxEventStatus.DEAD))
                .thenReturn(Optional.of(dead));
        when(claimService.requeueDead(EVENT_ID)).thenReturn(true);
        when(repository.findById(EVENT_ID)).thenReturn(Optional.of(dead));

        service.requeue(EVENT_ID, "req-1");

        // No repository save / no direct field mutation: the guarded conditional
        // update inside OutboxEventClaimService is the only writer, and it does not
        // reset attempts, last_error or dead_at.
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).delete(any());
        assertThat(dead.getAttempts()).isEqualTo(10);
        assertThat(dead.getLastError()).isEqualTo("broker down");
        assertThat(dead.getDeadAt()).isEqualTo(deadAt);
    }

    // ── 8. conflict and concurrency ─────────────────────────────────────────

    @Test
    void concurrentRequeueThatLosesTheRaceIsRejected() {
        when(repository.findByIdAndStatusAndDeletedFalse(EVENT_ID, OutboxEventStatus.DEAD))
                .thenReturn(Optional.of(deadRow(UUID.randomUUID(), TENANT_A)));
        // The other operator won: the conditional update matches no row.
        when(claimService.requeueDead(EVENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.requeue(EVENT_ID, "req-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already requeued by another operator");
    }

    @Test
    void secondRequeueOfAnAlreadyRequeuedEventIsRejected() {
        // Row is no longer DEAD, so it is not even found by the DEAD-scoped load.
        when(repository.findByIdAndStatusAndDeletedFalse(EVENT_ID, OutboxEventStatus.DEAD))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requeue(EVENT_ID, "req-1"))
                .isInstanceOf(NotFoundException.class);
        verify(claimService, never()).requeueDead(any());
    }

    @Test
    void requeueOfANonDeadRowIsRejected() {
        OutboxEvent pending = requeuedRow(UUID.randomUUID(), TENANT_A);
        when(repository.findByIdAndStatusAndDeletedFalse(EVENT_ID, OutboxEventStatus.DEAD))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.requeue(EVENT_ID, "req-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not in the DEAD state");
        verify(claimService, never()).requeueDead(any());
    }

    // ── 9. audit ────────────────────────────────────────────────────────────

    @Test
    void successfulRequeueIsAuditedWithOperatorAndTenant() {
        when(repository.findByIdAndStatusAndDeletedFalse(EVENT_ID, OutboxEventStatus.DEAD))
                .thenReturn(Optional.of(deadRow(UUID.randomUUID(), TENANT_A)));
        when(claimService.requeueDead(EVENT_ID)).thenReturn(true);
        when(repository.findById(EVENT_ID))
                .thenReturn(Optional.of(requeuedRow(UUID.randomUUID(), TENANT_A)));

        service.requeue(EVENT_ID, "req-9");

        var captor = org.mockito.ArgumentCaptor.forClass(OperatorActionAuditService.Entry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo(OperatorActionResult.SUCCESS);
        assertThat(captor.getValue().getTargetId()).isEqualTo(String.valueOf(EVENT_ID));
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_A);
        assertThat(captor.getValue().getOperatorUserId()).isEqualTo(scope.userId());
        assertThat(captor.getValue().getRequestId()).isEqualTo("req-9");
    }

    // ── 10. disabled tooling ─────────────────────────────────────────────────

    @Test
    void disabledToolingRefusesListingAndRequeue() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.listDead(0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("disabled");
        assertThatThrownBy(() -> service.requeue(EVENT_ID, "req-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("disabled");
        verify(claimService, never()).requeueDead(any());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static OutboxEvent deadRow(UUID id, UUID tenantId) {
        return OutboxEvent.builder()
                .id(id)
                .eventId(EVENT_ID)
                .tenantId(tenantId)
                .eventName("LEAD.CREATED")
                .aggregateType("LEAD")
                .eventType("CREATED")
                .aggregateId(UUID.randomUUID())
                .payload(Map.of("eventId", EVENT_ID.toString(), "metadata", Map.of("actorId", "x")))
                .status(OutboxEventStatus.DEAD)
                .attempts(10)
                .deadAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private static OutboxEvent requeuedRow(UUID id, UUID tenantId) {
        return OutboxEvent.builder()
                .id(id)
                .eventId(EVENT_ID)
                .tenantId(tenantId)
                .eventName("LEAD.CREATED")
                .aggregateType("LEAD")
                .eventType("CREATED")
                .aggregateId(UUID.randomUUID())
                .payload(Map.of("eventId", EVENT_ID.toString()))
                .status(OutboxEventStatus.PENDING)
                .attempts(10)
                .build();
    }

    /** Small helper so the clamping assertion reads clearly. */
    static final class ArgumentCaptorHelper {
        static void assertPageSize(OutboxEventRepository repository, int expected) {
            var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(repository).findDeadForTenant(any(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(expected);
        }

        private ArgumentCaptorHelper() {
        }
    }
}
