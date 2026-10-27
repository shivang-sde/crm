package com.shivang.crm.modules.ticket.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.ticket.dto.TicketMessageCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketMessageResponse;
import com.shivang.crm.modules.ticket.dto.TicketMessageUpdateRequest;
import com.shivang.crm.modules.ticket.service.TicketMessageService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * L3.1-P3: the Ticket conversation HTTP surface.
 *
 * <p>Nested under the ticket rather than given its own top-level resource, because
 * a message is meaningless outside its ticket and this shape makes that structural:
 * every path already carries the ticket whose scope governs the request.
 *
 * <p>Permissions are the parent's. {@code ticket:read} reads the conversation and
 * {@code ticket:write} mutates it — there is deliberately no {@code ticket_message}
 * module, because a second permission would have to be kept in step with the Ticket
 * ones and could drift into letting someone act on a conversation they cannot see.
 * {@code RbacFilter} already derives {@code ticket} from this URL's second segment,
 * so the fail-closed filter check and these annotations agree.
 *
 * <p>Tenant and author come from {@code TenantContext} and the security principal;
 * neither is accepted from the request body.
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/messages")
@RequiredArgsConstructor
@Tag(name = "Support Ticket Messages", description = "Ticket conversation messages")
public class TicketMessageController {

    private final TicketMessageService ticketMessageService;
    private final TenantContext tenantContext;

    @PostMapping
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'write')")
    @Operation(summary = "Add a message",
            description = "Append a message to the ticket's conversation. The author is always "
                    + "the authenticated user. Refused on a CLOSED ticket. CUSTOMER_VISIBLE records "
                    + "intent only; no delivery is performed.")
    public ResponseEntity<ApiResponse<TicketMessageResponse>> createMessage(
            @PathVariable UUID ticketId,
            @Valid @RequestBody TicketMessageCreateRequest request) {

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        TicketMessageResponse created =
                ticketMessageService.createMessage(ticketId, tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, Map.of("messageId", created.getId())));
    }

    @GetMapping
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'read')")
    @Operation(summary = "List messages",
            description = "The ticket's live conversation, newest first, bounded and ordered. "
                    + "Allowed on a CLOSED ticket.")
    public ResponseEntity<ApiResponse<List<TicketMessageResponse>>> listMessages(
            @PathVariable UUID ticketId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size) {

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        Page<TicketMessageResponse> messages =
                ticketMessageService.listMessages(ticketId, tenantId, userId, page, size);

        Map<String, Object> meta = Map.of(
                "page", messages.getNumber(),
                "size", messages.getSize(),
                "total", messages.getTotalElements(),
                "totalPages", messages.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(messages.getContent(), meta));
    }

    @GetMapping("/{messageId}")
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'read')")
    @Operation(summary = "Get message",
            description = "Fetch one message on the ticket, enforcing tenant isolation and the "
                    + "parent ticket's record scope")
    public ResponseEntity<ApiResponse<TicketMessageResponse>> getMessage(
            @PathVariable UUID ticketId, @PathVariable UUID messageId) {

        return ResponseEntity.ok(ApiResponse.success(
                ticketMessageService.getMessage(ticketId, messageId, currentTenantId(), currentUserId())));
    }

    @PatchMapping("/{messageId}")
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'write')")
    @Operation(summary = "Edit message",
            description = "Edit body and/or visibility. Only the original author may do so, even at "
                    + "full ticket scope. Refused on a CLOSED ticket.")
    public ResponseEntity<ApiResponse<TicketMessageResponse>> updateMessage(
            @PathVariable UUID ticketId,
            @PathVariable UUID messageId,
            @Valid @RequestBody TicketMessageUpdateRequest request) {

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        return ResponseEntity.ok(ApiResponse.success(
                ticketMessageService.updateMessage(ticketId, messageId, tenantId, userId, request)));
    }

    @DeleteMapping("/{messageId}")
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'write')")
    @Operation(summary = "Delete message",
            description = "Soft-delete a message. Only the original author may do so. Never a "
                    + "physical delete. Refused on a CLOSED ticket.")
    public ResponseEntity<ApiResponse<String>> deleteMessage(
            @PathVariable UUID ticketId, @PathVariable UUID messageId) {

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        ticketMessageService.deleteMessage(ticketId, messageId, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success("Message deleted successfully"));
    }

    private UUID currentTenantId() {
        return tenantContext.getTenantId();
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("User authentication is not available");
        }
        return UUID.fromString(authentication.getPrincipal().toString());
    }
}
