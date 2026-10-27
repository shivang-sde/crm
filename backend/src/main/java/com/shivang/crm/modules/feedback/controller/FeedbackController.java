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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.feedback.dto.FeedbackAnalyticsResponse;
import com.shivang.crm.modules.feedback.dto.FeedbackCreateRequest;
import com.shivang.crm.modules.feedback.dto.FeedbackListParams;
import com.shivang.crm.modules.feedback.dto.FeedbackResponse;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.service.ComplaintTicketService;
import com.shivang.crm.modules.feedback.service.FeedbackAnalyticsService;
import com.shivang.crm.modules.feedback.service.FeedbackService;
import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.3-P1: Feedback HTTP surface.
 *
 * <p>Three authorization layers, as the repository intends:
 * <ol>
 *   <li>{@code RbacFilter} derives {@code feedback:read|write} from the URL and
 *       fails closed. Without a seeded permission the whole prefix 403s.</li>
 *   <li>{@code @PreAuthorize("@rbac.has(...)")} restates the same intent, so a
 *       routing change cannot silently widen access.</li>
 *   <li>{@code FeedbackService} applies {@code RecordScopeGuard} for the record
 *       boundary. This is authoritative: having {@code feedback:read} does not
 *       reveal another owner's feedback.</li>
 * </ol>
 *
 * <p>Deliberately two read verbs and one write verb. There is no PATCH and no
 * DELETE: submitted customer feedback is immutable, and adding either later should
 * be an explicit product decision rather than an omission that gets filled in.
 *
 * <p>Not anonymous and not public. An external system authenticates with a normal
 * tenant credential; a dedicated third-party ingestion credential, if direct
 * anonymous form posting is ever required, is a later phase and not something to
 * invent here.
 */
@RestController
@RequestMapping("/api/v1/feedback")
@Slf4j
@Tag(name = "Customer Feedback", description = "Voice of Customer feedback submitted against a customer")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final ComplaintTicketService complaintTicketService;
    private final FeedbackAnalyticsService feedbackAnalyticsService;
    private final TenantContext tenantContext;

    public FeedbackController(FeedbackService feedbackService,
            ComplaintTicketService complaintTicketService,
            FeedbackAnalyticsService feedbackAnalyticsService,
            TenantContext tenantContext) {
        this.feedbackService = feedbackService;
        this.complaintTicketService = complaintTicketService;
        this.feedbackAnalyticsService = feedbackAnalyticsService;
        this.tenantContext = tenantContext;
    }

    @PostMapping
    @PreAuthorize("@rbac.has(authentication, 'feedback', 'write')")
    @Operation(summary = "Submit customer feedback",
            description = "Records one NPS, CSAT, CES, general-feedback or complaint signal. The "
                    + "customer is identified by email address and resolved to an existing Account "
                    + "and Contact in the authenticated tenant; internal customer ids are neither "
                    + "accepted nor required. Submitting the same source and external reference again "
                    + "returns the original record rather than creating a duplicate.")
    public ResponseEntity<ApiResponse<FeedbackResponse>> submitFeedback(
            @Valid @RequestBody FeedbackCreateRequest request) {

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        FeedbackResponse created = feedbackService.submit(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, Map.of("feedbackId", created.getId())));
    }

    @GetMapping
    @PreAuthorize("@rbac.has(authentication, 'feedback', 'read')")
    @Operation(summary = "List customer feedback",
            description = "Feedback visible to the caller within the caller's record scope, newest first")
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> listFeedback(
            @Parameter(description = "Filter by canonical customer") @RequestParam(required = false) UUID accountId,
            @Parameter(description = "Filter by correlated ticket") @RequestParam(required = false) UUID ticketId,
            @Parameter(description = "Filter by feedback type") @RequestParam(required = false) FeedbackType type,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size) {

        UUID tenantId = currentTenantId();

        var params = FeedbackListParams.builder()
                .accountId(accountId)
                .ticketId(ticketId)
                .type(type)
                .page(page)
                .size(size)
                .build();

        var result = feedbackService.list(tenantId, params);

        Map<String, Object> meta = Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "total", result.getTotalElements(),
                "totalPages", result.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbac.has(authentication, 'feedback', 'read')")
    @Operation(summary = "Get customer feedback",
            description = "Fetch one feedback record, enforcing tenant isolation and record scope")
    public ResponseEntity<ApiResponse<FeedbackResponse>> getFeedback(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(feedbackService.getById(currentTenantId(), id)));
    }

    /**
     * L3.3-P4: live Voice-of-Customer metrics.
     *
     * <p>Anchored on the feedback resource, so {@code RbacFilter} resolves it to
     * {@code feedback:read} and no new permission exists. The aggregates are computed
     * over exactly the rows the caller could list — same scope, same tenant, same
     * soft-delete exclusion — so this endpoint cannot reveal what {@code GET
     * /feedback} would hide.
     */
    @GetMapping("/analytics")
    @PreAuthorize("@rbac.has(authentication, 'feedback', 'read')")
    @Operation(summary = "Voice-of-Customer analytics",
            description = "Live NPS, CSAT and CES over the caller's visible feedback in "
                    + "[from, to). ISO-8601 instants; defaults to the last 30 days. Scores stay "
                    + "null when there are no responses rather than manufacturing a zero.")
    public ResponseEntity<ApiResponse<FeedbackAnalyticsResponse>> getAnalytics(
            @Parameter(description = "Inclusive lower bound, ISO-8601 instant")
            @RequestParam(required = false) String from,
            @Parameter(description = "Exclusive upper bound, ISO-8601 instant")
            @RequestParam(required = false) String to,
            @Parameter(description = "Restrict to one canonical customer")
            @RequestParam(required = false) UUID accountId) {

        return ResponseEntity.ok(ApiResponse.success(
                feedbackAnalyticsService.analytics(currentTenantId(), from, to, accountId)));
    }

    @PostMapping("/{feedbackId}/ticket")
    @PreAuthorize("@rbac.has(authentication, 'feedback', 'write') AND @rbac.has(authentication, 'ticket', 'write')")
    @Operation(summary = "Convert a complaint to an operational ticket",
            description = "Creates an operational ticket from a complaint feedback record. Only complaints "
                    + "can be converted. If the complaint is already linked to a ticket, returns the "
                    + "existing ticket. Requires both feedback:write and ticket:write permissions.")
    public ResponseEntity<ApiResponse<TicketResponse>> convertComplaintToTicket(@PathVariable UUID feedbackId) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        TicketResponse ticket = complaintTicketService.convertToTicket(tenantId, userId, feedbackId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ticket, Map.of("ticketId", ticket.getId())));
    }

    private UUID currentTenantId() {
        return tenantContext.requireTenantId();
    }

    private UUID currentUserId() {
        return com.shivang.crm.util.UserUtil.currentUserId();
    }
}
