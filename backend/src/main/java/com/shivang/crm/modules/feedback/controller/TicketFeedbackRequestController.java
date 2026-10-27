package com.shivang.crm.modules.feedback.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.feedback.dto.FeedbackRequestCreateRequest;
import com.shivang.crm.modules.feedback.dto.FeedbackRequestResponse;
import com.shivang.crm.modules.feedback.service.FeedbackRequestService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * L3.5: staff workflow for ticket feedback links.
 *
 * <p>Separate from {@link FeedbackController} on purpose: a ticket link request
 * is not feedback, and {@code FeedbackContractTest} treats that controller's
 * permission surface as immutable. Both ticket and feedback permissions are
 * required because this operation creates a customer-facing request from a
 * ticket and will read/write feedback provenance.
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/feedback-requests")
@RequiredArgsConstructor
@Tag(name = "Ticket Feedback Requests",
        description = "Create, list, revoke and regenerate single-use customer feedback links")
public class TicketFeedbackRequestController {

    private final FeedbackRequestService feedbackRequestService;
    private final TenantContext tenantContext;

    @PostMapping
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'write') "
            + "AND @rbac.has(authentication, 'feedback', 'write')")
    @Operation(summary = "Create a feedback request for a ticket",
            description = "Returns the raw single-use link token. The token is stored only as a hash "
                    + "and cannot be recovered later; regenerate to issue a new one.")
    public ResponseEntity<ApiResponse<FeedbackRequestResponse>> create(
            @PathVariable UUID ticketId,
            @RequestBody(required = false) FeedbackRequestCreateRequest body) {

        FeedbackRequestResponse created = feedbackRequestService.create(
                currentTenantId(), currentUserId(), ticketId, body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, Map.of("requestId", created.getId())));
    }

    @GetMapping
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'read') "
            + "AND @rbac.has(authentication, 'feedback', 'read')")
    @Operation(summary = "List feedback requests for a ticket",
            description = "Newest first. Tokens are never returned here because only their hashes exist.")
    public ResponseEntity<ApiResponse<List<FeedbackRequestResponse>>> list(
            @PathVariable UUID ticketId) {

        List<FeedbackRequestResponse> requests = feedbackRequestService.list(
                currentTenantId(), currentUserId(), ticketId);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @PostMapping("/{requestId}/revoke")
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'write') "
            + "AND @rbac.has(authentication, 'feedback', 'write')")
    @Operation(summary = "Revoke a pending feedback request",
            description = "Idempotent for an already-revoked request. A submitted response is immutable.")
    public ResponseEntity<ApiResponse<FeedbackRequestResponse>> revoke(
            @PathVariable UUID ticketId,
            @PathVariable UUID requestId) {

        FeedbackRequestResponse revoked = feedbackRequestService.revoke(
                currentTenantId(), currentUserId(), ticketId, requestId);
        return ResponseEntity.ok(ApiResponse.success(revoked));
    }

    @PostMapping("/{requestId}/regenerate")
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'write') "
            + "AND @rbac.has(authentication, 'feedback', 'write')")
    @Operation(summary = "Regenerate a feedback request",
            description = "Revokes the old pending link if needed and issues a new single-use token.")
    public ResponseEntity<ApiResponse<FeedbackRequestResponse>> regenerate(
            @PathVariable UUID ticketId,
            @PathVariable UUID requestId) {

        FeedbackRequestResponse regenerated = feedbackRequestService.regenerate(
                currentTenantId(), currentUserId(), ticketId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(regenerated, Map.of("requestId", regenerated.getId())));
    }

    private UUID currentTenantId() {
        return tenantContext.requireTenantId();
    }

    private UUID currentUserId() {
        return com.shivang.crm.util.UserUtil.currentUserId();
    }
}
