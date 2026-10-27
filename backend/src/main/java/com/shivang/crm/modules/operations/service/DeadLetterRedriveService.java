package com.shivang.crm.modules.operations.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.shivang.crm.modules.operations.config.OperatorToolsProperties;
import com.shivang.crm.modules.operations.enums.OperatorActionResult;
import com.shivang.crm.modules.operations.enums.OperatorActionType;
import com.shivang.crm.shared.event.ReturnedMessageTracker;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;
import com.shivang.crm.shared.exception.PermissionDeniedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OPS-01: explicit, operator-initiated re-drive of one dead letter.
 *
 * <p>Lifecycle implemented here, and nothing else drives it:
 * <pre>
 *   DLQ --inspect--> operator confirms --> re-drive
 *        --> resolve destination from x-death
 *        --> allow-list check
 *        --> publish with publisher confirm, new message id
 *        --> check publisher return
 *        --> on success: record SUCCESS audit (this is the "re-driven" marker)
 *        --> on failure: record FAILED audit, original dead letter untouched
 * </pre>
 *
 * <p><b>There is no second retry mechanism and no automatic replay.</b> This class
 * is only ever entered from an operator endpoint. Nothing schedules it, and no
 * listener calls it.
 *
 * <p><b>Why the original dead letter is never removed.</b> AMQP has no primitive to
 * delete a specific message from a queue, so any implementation must consume from
 * the head - which risks deleting the wrong message. Instead the re-drive
 * <em>republishes a copy</em> to the original destination and leaves the original
 * in place. The SUCCESS audit row is what marks the message as already re-driven,
 * and the partial unique index on {@code (action_type, target_id)} makes that
 * marker race-proof. This also means a failed or unconfirmed publish cannot lose
 * anything: the original message is simply still there.
 *
 * <p><b>What this does and does not guarantee.</b> The republished body is
 * byte-identical, so the canonical {@code eventId} is unchanged and
 * {@code WorkflowTriggerService}'s
 * {@code ON CONFLICT (workflow_version_id, trigger_event_id) DO NOTHING} means a
 * re-drive whose trigger already executed is a no-op. It is <em>not</em> exactly
 * once: if the original failure happened before the trigger row was created, the
 * re-drive creates it, and if a failure happened mid-node-action, node-level
 * idempotency applies instead. Duplicate execution remains possible at the broker
 * level and is stated as such.
 *
 * <p>Because the copy carries a new AMQP message id, a copy that fails again lands
 * in the DLQ as a distinct, independently re-drivable entry with
 * {@code x-crm-redrive-of} pointing at its ancestor, and the audit history links
 * the whole chain.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterRedriveService {

    private final DeadLetterQueueInspector inspector;
    private final DeadLetterMessageMapper mapper;
    private final OperatorActionAuditService auditService;
    private final OperatorScopeResolver scope;
    private final OperatorToolsProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final ReturnedMessageTracker returnedMessageTracker;

    public RedriveResult redrive(String messageId, String requestId) {
        if (!properties.isEnabled()) {
            throw new BusinessException("OPERATOR_TOOLS_DISABLED",
                    "Operator recovery tooling is disabled (app.operator-tools.enabled=false)");
        }
        if (messageId == null || messageId.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "messageId is required");
        }

        // 1-3. Locate the message. Non-destructive: the scan only requeues.
        RabbitManagementApiClient.ManagementMessage message = inspector.scanFor(messageId)
                .orElseThrow(() -> new NotFoundException(
                        "DLQ_MESSAGE_NOT_FOUND",
                        "No dead letter with that message id was found within the scan bound"));

        DeadLetterView view = mapper.toView(message, true);

        // 4. Tenant scope. A message with no derivable tenant is platform-only.
        if (!scope.isPlatformScope() && (view.getTenantId() == null
                || !view.getTenantId().equals(scope.tenantId()))) {
            auditService.record(OperatorActionAuditService.Entry.builder()
                    .actionType(OperatorActionType.DLQ_REDRIVE)
                    .targetId(messageId)
                    .tenantId(scope.tenantId())
                    .operatorUserId(scope.userId())
                    .operatorRole(scope.role())
                    .requestId(requestId)
                    .result(OperatorActionResult.NOT_ALLOWED)
                    .failureReason("Message is not visible to the caller's tenant")
                    .build());
            throw new PermissionDeniedException("ACCESS_DENIED",
                    "This dead letter does not belong to your organization");
        }

        // 5. Resolve the original destination from verified RabbitMQ metadata.
        DeadLetterMessageMapper.Destination destination = mapper.resolveDestination(message);

        // 6. Destination must be inside the approved CRM topology.
        if (!mapper.isApprovedDestination(destination)) {
            auditService.record(baseEntry(messageId, requestId, OperatorActionResult.NOT_ALLOWED,
                    "Destination exchange is not in the approved CRM topology")
                    .originalExchange(destination.exchange())
                    .originalRoutingKey(destination.routingKey())
                    .build());
            throw new PermissionDeniedException("DESTINATION_NOT_ALLOWED",
                    "The dead letter's original exchange is not an approved re-drive destination");
        }

        // 7-9. Republish a copy, requiring a publisher confirm, and honour a
        // publisher return as a failure.
        UUID copyMessageId = UUID.randomUUID();
        Message copy = MessageBuilder
                .withBody(payloadBytes(message))
                .setContentType(contentTypeOrDefault(message))
                .setMessageId(copyMessageId.toString())
                .setHeader(DeadLetterMessageMapper.X_CRM_REDRIVE_OF, messageId)
                .setHeader("x-crm-original-routing-key", String.valueOf(destination.routingKey()))
                .build();

        try {
            rabbitTemplate.invoke(operations -> {
                operations.send(destination.exchange(), destination.routingKey(), copy);
                operations.waitForConfirmsOrDie(properties.getDlq().getConfirmTimeoutMs());
                return null;
            });
        } catch (RuntimeException ex) {
            auditService.record(baseEntry(messageId, requestId, OperatorActionResult.FAILED,
                            "Publish failed: " + ex.getClass().getSimpleName())
                    .originalExchange(destination.exchange())
                    .originalRoutingKey(destination.routingKey())
                    .destinationExchange(destination.exchange())
                    .destinationRoutingKey(destination.routingKey())
                    .build());
            throw new BusinessException("DLQ_REDRIVE_PUBLISH_FAILED",
                    "Re-drive could not be confirmed by the broker. The original dead letter is untouched "
                            + "and can be retried.");
        }

        // 10. A broker confirm does not prove routability. A returned message is a
        // failure, exactly as it is for the normal publisher.
        if (returnedMessageTracker.wasReturned(copyMessageId.toString())) {
            auditService.record(baseEntry(messageId, requestId, OperatorActionResult.FAILED,
                            "Broker returned the republished message as unroutable")
                    .originalExchange(destination.exchange())
                    .originalRoutingKey(destination.routingKey())
                    .destinationExchange(destination.exchange())
                    .destinationRoutingKey(destination.routingKey())
                    .build());
            throw new BusinessException("DLQ_REDRIVE_UNROUTABLE",
                    "The re-drive could not be routed to " + destination.exchange() + " with routing key "
                            + destination.routingKey() + ". The original dead letter is untouched.");
        }

        // 11. Record the successful action. The unique index on
        // (action_type, target_id) WHERE result='SUCCESS' is the duplicate guard.
        try {
            auditService.record(baseEntry(messageId, requestId, OperatorActionResult.SUCCESS, null)
                    .tenantId(view.getTenantId())
                    .originalExchange(destination.exchange())
                    .originalRoutingKey(destination.routingKey())
                    .destinationExchange(destination.exchange())
                    .destinationRoutingKey(destination.routingKey())
                    .build());
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            // Two operators acted at once. The copy may or may not have been
            // published, so this is reported honestly rather than as a success.
            throw new BusinessException("DLQ_REDRIVE_CONFLICT",
                    "This dead letter was already re-driven by another operator. "
                            + "The DLQ message remains in place and is marked not replayable.");
        }

        return RedriveResult.builder()
                .originalMessageId(messageId)
                .republishedMessageId(copyMessageId.toString())
                .exchange(destination.exchange())
                .routingKey(destination.routingKey())
                .eventId(view.getEventId())
                .eventType(view.getEventType())
                .tenantId(view.getTenantId())
                .replayedAt(Instant.now())
                .originalDeadLetterRetained(true)
                .build();
    }

    private OperatorActionAuditService.Entry.EntryBuilder baseEntry(String messageId, String requestId,
            OperatorActionResult result, String reason) {
        return OperatorActionAuditService.Entry.builder()
                .actionType(OperatorActionType.DLQ_REDRIVE)
                .targetId(messageId)
                .tenantId(scope.tenantId())
                .operatorUserId(scope.userId())
                .operatorRole(scope.role())
                .requestId(requestId)
                .result(result)
                .failureReason(reason);
    }

    /**
     * The original body, byte for byte.
     *
     * <p>Preserving it matters: the canonical {@code eventId} inside is what makes
     * the consumer-side {@code ON CONFLICT} dedup effective. If the management API
     * returned base64 instead of a UTF-8 string it is decoded, and only then.
     */
    private byte[] payloadBytes(RabbitManagementApiClient.ManagementMessage message) {
        String payload = message.payload();
        if (payload == null) {
            throw new BusinessException("DLQ_MESSAGE_UNREADABLE",
                    "The dead letter has no readable payload and cannot be re-driven");
        }
        if ("base64".equalsIgnoreCase(message.payloadEncoding())) {
            return java.util.Base64.getDecoder().decode(payload);
        }
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private static String contentTypeOrDefault(RabbitManagementApiClient.ManagementMessage message) {
        return message.contentType() == null || message.contentType().isBlank()
                ? "application/json"
                : message.contentType();
    }

    @lombok.Getter
    @lombok.Builder
    public static class RedriveResult {
        private final String originalMessageId;
        private final String republishedMessageId;
        private final String exchange;
        private final String routingKey;
        private final String eventId;
        private final String eventType;
        private final UUID tenantId;
        private final Instant replayedAt;
        /**
         * Always true: re-drive republishes a copy and never consumes the dead
         * letter, because AMQP cannot delete a specific message from a queue.
         */
        private final boolean originalDeadLetterRetained;
    }
}
