package com.shivang.crm.modules.operations.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.operations.dto.OperatorActionView;
import com.shivang.crm.modules.operations.dto.OutboxDeadEventView;
import com.shivang.crm.modules.operations.entity.OperatorActionRecord;
import com.shivang.crm.modules.operations.enums.OperatorActionType;
import com.shivang.crm.modules.operations.service.DeadLetterQueueInspector;
import com.shivang.crm.modules.operations.service.DeadLetterRedriveService;
import com.shivang.crm.modules.operations.service.DeadLetterView;
import com.shivang.crm.modules.operations.service.OperatorActionAuditService;
import com.shivang.crm.modules.operations.service.OperatorScopeResolver;
import com.shivang.crm.modules.operations.service.OutboxDeadEventService;
import com.shivang.crm.shared.dto.ApiResponse;
import com.shivang.crm.shared.exception.NotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Operator tooling for recovering failed asynchronous work.
 *
 * <pre>
 *   GET  /api/v1/operations/rabbitmq/dlq                        list dead letters (non-destructive)
 *   GET  /api/v1/operations/rabbitmq/dlq/{messageId}            one dead letter (non-destructive)
 *   POST /api/v1/operations/rabbitmq/dlq/{messageId}/redrive    explicit re-drive
 *   GET  /api/v1/operations/outbox/dead                          list terminal outbox events
 *   GET  /api/v1/operations/outbox/dead/{eventId}                one terminal outbox event
 *   POST /api/v1/operations/outbox/dead/{eventId}/requeue        explicit requeue
 *   GET  /api/v1/operations/actions                              operator action history
 * </pre>
 *
 * <p>Every recovery action is explicit and operator initiated. There is no
 * scheduled consumer on the dead letter queue and no scheduled DEAD-outbox
 * recovery job anywhere in this subsystem.
 *
 * <p><b>Authorization.</b> Two new seeded permissions, {@code admin:operations_read}
 * and {@code admin:operations_write}, granted by V66 to platform SUPERADMIN and to
 * tenant ADMIN. {@code workflow:write} deliberately does not grant this: recovering
 * failed asynchronous work is an operator action.
 *
 * <p>{@code /api/v1/operations} is registered in {@code RbacFilter}'s excluded
 * prefixes, alongside {@code /api/v1/integrations} and
 * {@code /api/v1/admin/commercial}. That is required because {@code RbacFilter}
 * derives a permission from the URL, and the derived module for this prefix is
 * not seeded - the annotations below are the authoritative, DB-backed check. This
 * follows the existing convention rather than inventing a broad {@code admin:read}
 * grant that would also cover unrelated admin paths.
 *
 * <p>No handler mutates a database status column. Every state change goes through
 * a service, and the outbox requeue specifically delegates to the existing
 * {@code OutboxEventClaimService.requeueDead}.
 */
@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
@Tag(name = "Operator Tools", description = "Inspection and explicit recovery of failed asynchronous work")
public class OperatorMessageController {

    private final DeadLetterQueueInspector deadLetterQueueInspector;
    private final DeadLetterRedriveService deadLetterRedriveService;
    private final OutboxDeadEventService outboxDeadEventService;
    private final OperatorActionAuditService auditService;
    private final OperatorScopeResolver scope;

    // ── OPS-01: RabbitMQ dead letters ────────────────────────────────────────

    @GetMapping("/rabbitmq/dlq")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'operations_read')")
    @Operation(summary = "List dead lettered messages",
            description = "Non-destructive inspection. No message is consumed, requeued differently or removed. "
                    + "Requires the RabbitMQ management API; reports a clear error when it is not configured.")
    public ResponseEntity<ApiResponse<List<DeadLetterView>>> listDeadLetters(
            @RequestParam(required = false) Integer limit) {
        List<DeadLetterView> views = deadLetterQueueInspector.listDeadLetters(limit == null ? 0 : limit);
        return ResponseEntity.ok(ApiResponse.success(views, Map.of("count", views.size())));
    }

    @GetMapping("/rabbitmq/dlq/{messageId}")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'operations_read')")
    @Operation(summary = "Inspect one dead lettered message",
            description = "Non-destructive. Returns identifiers and dead-letter facts only; the message body "
                    + "is never returned.")
    public ResponseEntity<ApiResponse<DeadLetterView>> getDeadLetter(@PathVariable String messageId) {
        return ResponseEntity.ok(ApiResponse.success(deadLetterQueueInspector.findByMessageId(messageId)));
    }

    @PostMapping("/rabbitmq/dlq/{messageId}/redrive")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'operations_write')")
    @Operation(summary = "Re-drive one dead lettered message",
            description = "Republishes a byte-identical copy to the original exchange and routing key recorded "
                    + "by RabbitMQ at dead-letter time, and requires a publisher confirm. The original dead "
                    + "letter is retained: AMQP cannot delete a specific message from a queue, so the audit "
                    + "record is what marks it as already re-driven and prevents a second attempt. "
                    + "The downstream workflow may execute again.")
    public ResponseEntity<ApiResponse<DeadLetterRedriveService.RedriveResult>> redrive(
            @PathVariable String messageId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestIdHeader,
            HttpServletRequest servletRequest) {

        DeadLetterRedriveService.RedriveResult result =
                deadLetterRedriveService.redrive(messageId, requestId(servletRequest, requestIdHeader));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── OPS-02: terminal outbox events ───────────────────────────────────────

    @GetMapping("/outbox/dead")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'operations_read')")
    @Operation(summary = "List terminal outbox events",
            description = "Paginated. Tenant callers see only their own tenant's events; platform operators see all.")
    public ResponseEntity<ApiResponse<List<OutboxDeadEventView>>> listDeadOutbox(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<OutboxDeadEventView> result = outboxDeadEventService.listDead(page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), pageMeta(result, page, size)));
    }

    @GetMapping("/outbox/dead/{eventId}")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'operations_read')")
    @Operation(summary = "Inspect one terminal outbox event")
    public ResponseEntity<ApiResponse<OutboxDeadEventView>> getDeadOutbox(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(outboxDeadEventService.findById(eventId)));
    }

    @PostMapping("/outbox/dead/{eventId}/requeue")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'operations_write')")
    @Operation(summary = "Requeue one terminal outbox event",
            description = "Explicit operator action. Performs the existing guarded DEAD to PENDING transition and "
                    + "lets the normal outbox publisher pick it up. Attempt count, last error and dead_at history "
                    + "are preserved. A second or concurrent requeue is rejected.")
    public ResponseEntity<ApiResponse<OutboxDeadEventView>> requeue(
            @PathVariable UUID eventId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestIdHeader,
            HttpServletRequest servletRequest) {

        OutboxDeadEventView view = outboxDeadEventService.requeue(eventId, requestId(servletRequest, requestIdHeader));
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(view));
    }

    // ── shared audit view ────────────────────────────────────────────────────

    @GetMapping("/actions")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'operations_read')")
    @Operation(summary = "Operator recovery action history",
            description = "Audit trail of explicit recovery actions. Contains identifiers and routing only, "
                    + "never a message body or event payload.")
    public ResponseEntity<ApiResponse<List<OperatorActionView>>> actions(
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        OperatorActionType type = parseActionType(actionType);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)));
        Page<OperatorActionRecord> records = auditService.history(type, scope.tenantId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(records.getContent().stream()
                .map(this::toView)
                .toList(), pageMeta(records, page, size)));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private OperatorActionView toView(OperatorActionRecord record) {
        return OperatorActionView.builder()
                .id(record.getId())
                .actionType(record.getActionType() == null ? null : record.getActionType().name())
                .targetId(record.getTargetId())
                .tenantId(record.getTenantId())
                .operatorUserId(record.getOperatorUserId())
                .operatorRole(record.getOperatorRole())
                .requestId(record.getRequestId())
                .originalExchange(record.getOriginalExchange())
                .originalRoutingKey(record.getOriginalRoutingKey())
                .destinationExchange(record.getDestinationExchange())
                .destinationRoutingKey(record.getDestinationRoutingKey())
                .result(record.getResult() == null ? null : record.getResult().name())
                .failureReason(record.getFailureReason())
                .createdAt(record.getCreatedAt())
                .build();
    }

    private static Map<String, Object> pageMeta(Page<?> page, int requestedPage, int requestedSize) {
        return Map.of(
                "page", page.getNumber(),
                "size", page.getSize(),
                "total", page.getTotalElements(),
                "totalPages", page.getTotalPages());
    }

    private static OperatorActionType parseActionType(String value) {
        if (value == null || value.isBlank()) {
            return OperatorActionType.DLQ_REDRIVE;
        }
        try {
            return OperatorActionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("UNKNOWN_ACTION_TYPE",
                    "Unknown actionType: " + value + ". Expected one of DLQ_REDRIVE, OUTBOX_REQUEUE.");
        }
    }

    /**
     * Request correlation for the audit row. Only the explicit header is used; it is
     * never synthesised, so a missing header records null rather than a value that
     * looks like real correlation.
     */
    private static String requestId(HttpServletRequest request, String header) {
        if (header != null && !header.isBlank()) {
            return header;
        }
        String forwarded = request == null ? null : request.getHeader("X-Request-Id");
        return forwarded == null || forwarded.isBlank() ? null : forwarded;
    }
}
