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

import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.ticket.dto.TicketAssignRequest;
import com.shivang.crm.modules.ticket.dto.TicketCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.modules.ticket.dto.TicketStatusChangeRequest;
import com.shivang.crm.modules.ticket.dto.TicketUpdateRequest;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.service.TicketService;
import com.shivang.crm.modules.ticket.service.TicketTimelineService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * L3.1-P1: Ticket HTTP surface.
 *
 * <p>Three authorization layers, as the repository intends:
 * <ol>
 *   <li>{@code RbacFilter} derives {@code ticket:read|write|delete} from the URL
 *       and fails closed. Without a seeded permission the whole prefix 403s.</li>
 *   <li>{@code @PreAuthorize("@rbac.has(...)")} restates the same intent here, so a
 *       routing change cannot silently widen access.</li>
 *   <li>{@code TicketService} applies {@code RecordScopeGuard} for the record
 *       boundary. This is the authoritative layer: having {@code ticket:write} does
 *       not grant access to another agent's ticket.</li>
 * </ol>
 *
 * <p>camelCase JSON throughout; the DTOs carry no per-field name overrides, which
 * is the L3.0-P5 contract.
 */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Tag(name = "Support Tickets", description = "Customer support ticket management")
public class TicketController {

    private final TicketService ticketService;
    private final TicketTimelineService ticketTimelineService;
    private final TenantContext tenantContext;

    @PostMapping
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'write')")
    @Operation(summary = "Create ticket",
            description = "Raise a support ticket. Always starts OPEN; the initial status is not client-supplied.")
    public ResponseEntity<ApiResponse<TicketResponse>> createTicket(
            @Valid @RequestBody TicketCreateRequest request) {

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        TicketResponse created = ticketService.createTicket(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, Map.of("ticketId", created.getId())));
    }

    @GetMapping
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'read')")
    @Operation(summary = "List tickets",
            description = "List tickets visible to the caller, newest first, bounded and deterministically ordered")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> listTickets(
            @Parameter(description = "Filter by account") @RequestParam(required = false) UUID accountId,
            @Parameter(description = "Filter by status") @RequestParam(required = false) TicketStatus status,
            @Parameter(description = "Search term matched against the subject")
            @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size) {

        UUID tenantId = currentTenantId();

        Page<TicketResponse> tickets = ticketService.listTickets(tenantId, accountId, status, search, page, size);

        Map<String, Object> meta = Map.of(
                "page", tickets.getNumber(),
                "size", tickets.getSize(),
                "total", tickets.getTotalElements(),
                "totalPages", tickets.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(tickets.getContent(), meta));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'read')")
    @Operation(summary = "Get ticket", description = "Fetch one ticket, enforcing tenant isolation and record scope")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicket(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();

        return ResponseEntity.ok(ApiResponse.success(ticketService.getTicketById(id, tenantId)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'write')")
    @Operation(summary = "Update ticket",
            description = "Partial update of subject, description, accountId, contactId or ownerId. "
                    + "Lifecycle state is not reachable here.")
    public ResponseEntity<ApiResponse<TicketResponse>> updateTicket(
            @PathVariable UUID id,
            @Valid @RequestBody TicketUpdateRequest request) {

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        return ResponseEntity.ok(ApiResponse.success(ticketService.updateTicket(id, tenantId, userId, request)));
    }

    /**
     * L3.1-P2: the one canonical assignment endpoint.
     *
     * <p>{@code @PreAuthorize} names {@code ticket:assign} explicitly rather than
     * {@code ticket:write}. This is the layer that stops a write-capable agent from
     * handing a ticket to someone else, so it is stated here and not left to the
     * service alone — a routing or filter change must not silently widen access.
     *
     * <p>Tenant and user come from {@code TenantContext} and the security context;
     * neither is accepted from the request body.
     */
    @PostMapping("/{id}/assign")
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'assign')")
    @Operation(summary = "Assign ticket",
            description = "Assign the ticket to an agent. Requires ticket:assign, which "
                    + "ticket:write does not imply. Assigning the current owner is an idempotent no-op.")
    public ResponseEntity<ApiResponse<TicketResponse>> assignTicket(
            @PathVariable UUID id,
            @Valid @RequestBody TicketAssignRequest request) {

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        return ResponseEntity.ok(
                ApiResponse.success(ticketService.assignTicket(id, tenantId, userId, request)));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'write')")
    @Operation(summary = "Change ticket status",
            description = "The single status endpoint, covering resolve, close and reopen. "
                    + "Requesting the current status is an idempotent no-op.")
    public ResponseEntity<ApiResponse<TicketResponse>> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TicketStatusChangeRequest request) {

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        return ResponseEntity.ok(
                ApiResponse.success(ticketService.changeStatus(id, tenantId, userId, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'delete')")
    @Operation(summary = "Delete ticket",
            description = "Soft delete. The row is retained so customer correspondence history is not lost.")
    public ResponseEntity<ApiResponse<String>> deleteTicket(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        ticketService.deleteTicket(id, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success("Ticket deleted successfully"));
    }

    /**
     * L3.1-P5: the ticket timeline.
     *
     * <p>Reuses the existing activities table and the ticket's own read permission
     * rather than introducing an activity permission: a timeline entry is only
     * visible to someone who may read the ticket it belongs to.
     */
    @GetMapping("/{id}/activities")
    @PreAuthorize("@rbac.has(authentication, 'ticket', 'read')")
    @Operation(summary = "Get ticket timeline",
            description = "Lifecycle and operational activities for this ticket, newest first. "
                    + "Visible only to callers who can read the ticket itself.")
    public ResponseEntity<ApiResponse<List<ActivityResponse>>> getTicketActivities(
            @PathVariable UUID id,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size) {

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        Page<ActivityResponse> activities =
                ticketTimelineService.getTicketTimeline(id, tenantId, userId, page, size);

        Map<String, Object> meta = Map.of(
                "page", activities.getNumber(),
                "size", activities.getSize(),
                "total", activities.getTotalElements(),
                "totalPages", activities.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(activities.getContent(), meta));
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
