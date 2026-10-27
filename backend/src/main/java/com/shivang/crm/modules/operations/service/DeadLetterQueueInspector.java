package com.shivang.crm.modules.operations.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.operations.config.OperatorToolsProperties;

import com.shivang.crm.modules.operations.enums.OperatorActionType;
import com.shivang.crm.modules.workflow.config.WorkflowRabbitConfig;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OPS-01: read-only, non-destructive inspection of the RabbitMQ dead letter queue.
 *
 * <p>Safety properties, in order of importance:
 * <ol>
 *   <li><b>No automatic replay.</b> Nothing in this class re-drives anything, and
 *       nothing schedules a consumer on the DLQ. The only path to a re-drive is
 *       {@link DeadLetterRedriveService#redrive} called from an operator endpoint.</li>
 *   <li><b>Inspection never removes a message.</b> Only
 *       {@link RabbitManagementApiClient#peek} is used, which is the management
 *       API's requeue acknowledgement mode.</li>
 *   <li><b>Bounded.</b> A page fetches at most {@code pageSize} messages and a
 *       single-message lookup scans at most {@code maxScanMessages}. The queue is
 *       never loaded into memory.</li>
 *   <li><b>Tenant scoped.</b> A tenant caller only sees messages whose canonical
 *       event declares its tenant id. A message with no derivable tenant is
 *       platform-scope only, so it cannot leak into a tenant's list.</li>
 * </ol>
 *
 * <p>Note on rotation: the management API's requeue mode returns the fetched
 * messages to the head of the queue, so each inspection call rotates the queue by
 * the number of messages it read. Nothing is lost and no message can be consumed
 * twice, but deep offsets are not stable across calls. A message deeper than
 * {@code maxScanMessages} is therefore not reachable in a single lookup. That is
 * a documented limit, not a silent truncation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueInspector {

    private final RabbitManagementApiClient client;
    private final DeadLetterMessageMapper mapper;
    private final OperatorActionAuditService auditService;
    private final OperatorScopeResolver scope;
    private final OperatorToolsProperties properties;

    /**
     * Returns a page of operator-safe dead-letter views.
     *
     * @param limit page size, clamped to the configured page size
     */
    public List<DeadLetterView> listDeadLetters(int limit) {
        requireEnabled();

        int pageSize = clamp(limit, properties.getDlq().getPageSize());
        List<RabbitManagementApiClient.ManagementMessage> messages =
                client.peek(WorkflowRabbitConfig.WORKFLOW_DEAD_LETTER_QUEUE, pageSize);

        return messages.stream()
                .map(this::toScopedView)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Locates a single dead letter by message id without removing it.
     *
     * @throws NotFoundException when the message is not present within the scan bound
     */
    @Transactional(readOnly = true)
    public DeadLetterView findByMessageId(String messageId) {
        requireEnabled();
        if (messageId == null || messageId.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "messageId is required");
        }

        RabbitManagementApiClient.ManagementMessage found = scanFor(messageId)
                .orElseThrow(() -> new NotFoundException(
                        "DLQ_MESSAGE_NOT_FOUND",
                        "No dead letter with that message id was found within the scan bound of "
                                + properties.getDlq().getMaxScanMessages() + " messages"));

        DeadLetterView view = toScopedView(found);
        if (view == null) {
            // The message exists but is not visible to this caller. Report it as
            // not found rather than confirming its existence.
            throw new NotFoundException("DLQ_MESSAGE_NOT_FOUND", "No dead letter with that message id");
        }
        return view;
    }

    /**
     * Non-destructive bounded scan for one message id.
     *
     * <p>Each iteration fetches a single message with the requeue mode, so nothing
     * is ever removed. Returns empty when the scan bound is reached.
     */
    java.util.Optional<RabbitManagementApiClient.ManagementMessage> scanFor(String messageId) {
        int bound = Math.max(1, properties.getDlq().getMaxScanMessages());
        for (int scanned = 0; scanned < bound; scanned++) {
            List<RabbitManagementApiClient.ManagementMessage> fetched =
                    client.peek(WorkflowRabbitConfig.WORKFLOW_DEAD_LETTER_QUEUE, 1);
            if (fetched == null || fetched.isEmpty()) {
                return java.util.Optional.empty();
            }
            RabbitManagementApiClient.ManagementMessage candidate = fetched.get(0);
            if (messageId.equals(candidate.messageId())) {
                return java.util.Optional.of(candidate);
            }
        }
        log.warn("DLQ lookup for messageId={} reached the scan bound of {} without a match; "
                + "the message may exist deeper in the queue", messageId, bound);
        return java.util.Optional.empty();
    }

    /**
     * Projects a raw message and applies caller scoping.
     *
     * @return null when the message must not be visible to this caller
     */
    private DeadLetterView toScopedView(RabbitManagementApiClient.ManagementMessage message) {
        boolean alreadyRedriven = auditService.alreadySucceeded(
                OperatorActionType.DLQ_REDRIVE, message.messageId());
        DeadLetterView view = mapper.toView(message, !alreadyRedriven);

        if (scope.isPlatformScope()) {
            return view;
        }
        // Tenant callers see only messages whose canonical event declares their
        // tenant. A message with no derivable tenant id is never tenant-visible.
        UUID tenantId = view.getTenantId();
        if (tenantId == null || !tenantId.equals(scope.tenantId())) {
            return null;
        }
        return view;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException("OPERATOR_TOOLS_DISABLED",
                    "Operator recovery tooling is disabled (app.operator-tools.enabled=false)");
        }
        if (!client.isAvailable()) {
            throw new BusinessException("DLQ_INSPECTION_UNAVAILABLE",
                    "RabbitMQ DLQ inspection requires the RabbitMQ management API. Configure "
                            + "app.operator-tools.dlq.management-url. This service will not fall back to "
                            + "consuming the dead letter queue.");
        }
    }

    private static int clamp(int requested, int configured) {
        int fallback = configured > 0 ? configured : 20;
        if (requested <= 0) {
            return fallback;
        }
        return Math.min(requested, fallback);
    }
}
