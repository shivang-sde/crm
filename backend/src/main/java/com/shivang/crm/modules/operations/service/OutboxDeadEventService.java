package com.shivang.crm.modules.operations.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.operations.config.OperatorToolsProperties;
import com.shivang.crm.modules.operations.dto.OutboxDeadEventView;
import com.shivang.crm.modules.operations.enums.OperatorActionResult;
import com.shivang.crm.modules.operations.enums.OperatorActionType;
import com.shivang.crm.shared.event.OutboxEvent;
import com.shivang.crm.shared.event.OutboxEventClaimService;
import com.shivang.crm.shared.event.OutboxEventRepository;
import com.shivang.crm.shared.event.OutboxEventStatus;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OPS-02: operator inspection and explicit requeue of terminal outbox events.
 *
 * <p>Safety properties:
 * <ol>
 *   <li><b>No automatic resurrection.</b> Nothing schedules this class, and the
 *       claim query in {@link OutboxEventRepository#claimAvailable} only selects
 *       {@code PENDING} and {@code PROCESSING}, so a DEAD row cannot re-enter
 *       normal processing on its own.</li>
 *   <li><b>One state transition path.</b> The requeue is delegated to the existing
 *       {@link OutboxEventClaimService#requeueDead}, which is the same guarded
 *       conditional update the reliability work added. No controller touches a
 *       status column.</li>
 *   <li><b>History is preserved.</b> {@code attempts}, {@code last_error} and
 *       {@code dead_at} are not reset. The row returns to PENDING carrying its
 *       full failure history, and the operator action is recorded separately.</li>
 *   <li><b>Tenant scoped.</b> A tenant caller only ever sees and requeues rows
 *       whose persisted {@code tenant_id} equals the caller's tenant. Ownership is
 *       read from the real column, never inferred from the payload JSON.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxDeadEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventClaimService outboxEventClaimService;
    private final OperatorActionAuditService auditService;
    private final OperatorScopeResolver scope;
    private final OperatorToolsProperties properties;

    /** Terminal events, paginated, scoped to the caller. */
    @Transactional(readOnly = true)
    public Page<OutboxDeadEventView> listDead(int page, int size) {
        requireEnabled();
        Pageable pageable = pageable(page, size);

        Page<OutboxEvent> rows = scope.isPlatformScope()
                ? outboxEventRepository.findDeadPlatformWide(pageable)
                : outboxEventRepository.findDeadForTenant(scope.tenantId(), pageable);

        return rows.map(this::toView);
    }

    /** One terminal event by id, scoped to the caller. */
    @Transactional(readOnly = true)
    public OutboxDeadEventView findById(UUID eventId) {
        requireEnabled();
        OutboxEvent row = loadAndAuthorize(eventId);
        return toView(row);
    }

    /**
     * Explicit operator requeue of one terminal event.
     *
     * <p>Performs only the guarded state transition and lets the existing
     * {@code OutboxEventPublisher} scheduler pick the row up on its next poll. It
     * does not publish inline, does not wait for a broker confirm and does not
     * start a second retry loop.
     *
     * @throws BusinessException with code {@code OUTBOX_NOT_DEAD} when the row is no
     *         longer terminal - which is also what a concurrent second operator gets
     */
    public OutboxDeadEventView requeue(UUID eventId, String requestId) {
        requireEnabled();
        OutboxEvent row = loadAndAuthorize(eventId);

        if (row.getStatus() != OutboxEventStatus.DEAD) {
            auditService.record(entry(String.valueOf(eventId), requestId, OperatorActionResult.CONFLICT,
                    "Event is in status " + row.getStatus() + ", not DEAD")
                    .tenantId(row.getTenantId())
                    .build());
            throw new BusinessException("OUTBOX_NOT_DEAD",
                    "This outbox event is not in the DEAD state and does not need a requeue");
        }

        int attemptsBefore = row.getAttempts() == null ? 0 : row.getAttempts();
        String lastErrorBefore = row.getLastError();
        Instant deadAtBefore = row.getDeadAt();

        // The single guarded transition. Returns false when another actor moved the
        // row between the read above and this update, which is the concurrent
        // double-requeue case.
        boolean requeued = outboxEventClaimService.requeueDead(eventId);
        if (!requeued) {
            auditService.record(entry(String.valueOf(eventId), requestId, OperatorActionResult.CONFLICT,
                    "Concurrent requeue: the row was no longer DEAD")
                    .tenantId(row.getTenantId())
                    .build());
            throw new BusinessException("OUTBOX_NOT_DEAD",
                    "This outbox event was already requeued by another operator");
        }

        auditService.record(entry(String.valueOf(eventId), requestId, OperatorActionResult.SUCCESS, null)
                .tenantId(row.getTenantId())
                .build());

        log.info("Operator requeued terminal outbox event {} for tenant {}; attempts preserved at {}, "
                        + "prior dead_at={}, prior lastError={}",
                eventId, row.getTenantId(), attemptsBefore, deadAtBefore, lastErrorBefore);

        // Re-read so the response reflects the committed state.
        return outboxEventRepository.findById(eventId).map(this::toView)
                .orElseGet(() -> toView(row, true));
    }

    private OutboxEvent loadAndAuthorize(UUID eventId) {
        OutboxEvent row = outboxEventRepository.findByIdAndStatusAndDeletedFalse(eventId, OutboxEventStatus.DEAD)
                .orElseThrow(() -> new NotFoundException(
                        "OUTBOX_EVENT_NOT_FOUND",
                        "No outbox event with that id exists in the DEAD state"));

        if (!scope.canAccess(row.getTenantId())) {
            auditService.record(entry(String.valueOf(eventId), null, OperatorActionResult.NOT_ALLOWED,
                    "Event is not visible to the caller's tenant")
                    .tenantId(scope.tenantId())
                    .build());
            throw new com.shivang.crm.shared.exception.PermissionDeniedException(
                    "ACCESS_DENIED", "This outbox event does not belong to your organization");
        }
        return row;
    }

    private OutboxDeadEventView toView(OutboxEvent row) {
        return toView(row, false);
    }

    /**
     * Projects a terminal outbox row to a safe operator view.
     *
     * <p>The {@code payload} column is never mapped: it holds the serialised
     * canonical event including its free-form metadata map, which is not a place
     * an operator listing needs and is not somewhere sensitive data should flow.
     */
    private OutboxDeadEventView toView(OutboxEvent row, boolean justRequeued) {
        return OutboxDeadEventView.builder()
                .eventId(row.getEventId())
                .eventName(row.getEventName())
                .entityType(row.getAggregateType())
                .eventType(row.getEventType())
                .entityId(row.getAggregateId())
                .tenantId(row.getTenantId())
                .status(row.getStatus())
                .attempts(row.getAttempts())
                .deadAt(row.getDeadAt())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                // Sanitised and length-capped by the service. A raw exception message
                // can embed a connection string, so it is never passed through as-is.
                .lastError(sanitizeError(row.getLastError()))
                .payloadAvailable(row.getPayload() != null)
                .requeueable(row.getStatus() == OutboxEventStatus.DEAD)
                .requeued(justRequeued)
                .build();
    }

    private static String sanitizeError(String lastError) {
        if (lastError == null || lastError.isBlank()) {
            return null;
        }
        String sanitized = lastError.replaceAll("(?i)(password|secret|token|credential)\\s*=\\s*\\S+", "$1=***");
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500) + "...[truncated]";
    }

    private OperatorActionAuditService.Entry.EntryBuilder entry(String targetId, String requestId,
            OperatorActionResult result, String reason) {
        return OperatorActionAuditService.Entry.builder()
                .actionType(OperatorActionType.OUTBOX_REQUEUE)
                .targetId(targetId)
                .tenantId(scope.tenantId())
                .operatorUserId(scope.userId())
                .operatorRole(scope.role())
                .requestId(requestId)
                .result(result)
                .failureReason(reason);
    }

    private Pageable pageable(int page, int size) {
        int max = properties.getOutbox().getMaxPageSize();
        int resolvedSize = size <= 0 ? properties.getOutbox().getPageSize() : Math.min(size, max);
        return org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, resolvedSize));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException("OPERATOR_TOOLS_DISABLED",
                    "Operator recovery tooling is disabled (app.operator-tools.enabled=false)");
        }
    }
}
