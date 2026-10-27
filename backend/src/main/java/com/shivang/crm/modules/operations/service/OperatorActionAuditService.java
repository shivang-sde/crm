package com.shivang.crm.modules.operations.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.operations.entity.OperatorActionRecord;
import com.shivang.crm.modules.operations.enums.OperatorActionResult;
import com.shivang.crm.modules.operations.enums.OperatorActionType;
import com.shivang.crm.modules.operations.repository.OperatorActionRecordRepository;
import com.shivang.crm.shared.resilience.InfrastructureMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Single audit mechanism for operator recovery actions.
 *
 * <p>Deliberately separate from {@code entity_history} and {@code activities}:
 * those are CRM-record timelines keyed on a business entity, and a DLQ message or
 * a dead outbox row is not one. One table serves both features so there is one
 * place to audit, not one per feature.
 *
 * <p>Every write is a plain insert. There is no scheduler, no retry and no
 * listener anywhere in this package.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OperatorActionAuditService {

    private static final int MAX_FAILURE_REASON = 500;

    private final OperatorActionRecordRepository repository;
    private final InfrastructureMetrics metrics;

    /**
     * True when a previous action on this target already succeeded.
     *
     * <p>Fast pre-check for a friendly 409. The database unique index is the actual
     * guarantee, because two concurrent operators can both read {@code false}
     * here; {@link #record} then relies on the index to reject the second insert.
     */
    @Transactional(readOnly = true)
    public boolean alreadySucceeded(OperatorActionType actionType, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return false;
        }
        return repository.existsByActionTypeAndTargetIdAndResultAndDeletedFalse(
                actionType, targetId, OperatorActionResult.SUCCESS);
    }

    /**
     * Appends one audit row.
     *
     * <p>{@code REQUIRES_NEW} so the audit record survives even when the caller
     * throws afterwards, and so a conflict here cannot roll back unrelated work.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OperatorActionRecord record(Entry entry) {
        String reason = entry.getFailureReason();
        if (reason != null && reason.length() > MAX_FAILURE_REASON) {
            reason = reason.substring(0, MAX_FAILURE_REASON);
        }

        OperatorActionRecord record = OperatorActionRecord.builder()
                .actionType(entry.getActionType())
                .targetId(truncate(entry.getTargetId(), 255))
                .tenantId(entry.getTenantId())
                .operatorUserId(entry.getOperatorUserId())
                .operatorRole(truncate(entry.getOperatorRole(), 100))
                .requestId(truncate(entry.getRequestId(), 255))
                .originalExchange(truncate(entry.getOriginalExchange(), 255))
                .originalRoutingKey(truncate(entry.getOriginalRoutingKey(), 255))
                .destinationExchange(truncate(entry.getDestinationExchange(), 255))
                .destinationRoutingKey(truncate(entry.getDestinationRoutingKey(), 255))
                .result(entry.getResult())
                .failureReason(reason)
                .build();

        OperatorActionRecord saved = repository.saveAndFlush(record);

        if (entry.getResult() == OperatorActionResult.SUCCESS) {
            metrics.recordOperatorAction(entry.getActionType().name(), "SUCCESS");
            log.info("Operator recovery succeeded: action={}, target={}, tenant={}, operator={}, "
                            + "destination={}/{}",
                    entry.getActionType(), entry.getTargetId(), entry.getTenantId(),
                    entry.getOperatorUserId(), entry.getDestinationExchange(),
                    entry.getDestinationRoutingKey());
        } else {
            metrics.recordOperatorAction(entry.getActionType().name(), entry.getResult().name());
            log.warn("Operator recovery attempt did not succeed: action={}, target={}, operator={}, "
                            + "result={}, reason={}",
                    entry.getActionType(), entry.getTargetId(), entry.getOperatorUserId(),
                    entry.getResult(), reason);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<OperatorActionRecord> history(OperatorActionType actionType, UUID tenantId, Pageable pageable) {
        return tenantId == null
                ? repository.findHistoryPlatformWide(actionType, pageable)
                : repository.findHistoryForTenant(actionType, tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public long countSuccessful(OperatorActionType actionType) {
        return repository.countByActionTypeAndResultAndDeletedFalse(actionType, OperatorActionResult.SUCCESS);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Builder-style audit entry. Carries identifiers and short text only. */
    @lombok.Getter
    @lombok.Setter
    @lombok.Builder
    public static class Entry {
        private OperatorActionType actionType;
        private String targetId;
        private UUID tenantId;
        private UUID operatorUserId;
        private String operatorRole;
        private String requestId;
        private String originalExchange;
        private String originalRoutingKey;
        private String destinationExchange;
        private String destinationRoutingKey;
        private OperatorActionResult result;
        private String failureReason;
    }
}
