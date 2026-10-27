package com.shivang.crm.modules.ticket.dto;

import java.time.Instant;
import java.util.UUID;

import com.shivang.crm.modules.ticket.entity.TicketStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L3.1-P1: support Ticket response.
 *
 * <p>camelCase JSON, no {@code @JsonProperty} renames, per the L3.0-P5 contract.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload for a support ticket")
public class TicketResponse {

    private UUID id;

    @Schema(description = "Tenant that owns the ticket")
    private UUID tenantId;

    @Schema(description = "Owning agent user UUID")
    private UUID ownerId;

    @Schema(description = "The canonical customer identity this ticket belongs to")
    private UUID accountId;

    @Schema(description = "Contact who reported the issue, if known")
    private UUID contactId;

    @Schema(description = "Short summary of the reported problem")
    private String subject;

    @Schema(description = "Detailed description of the reported problem")
    private String description;

    @Schema(description = "Current lifecycle state")
    private TicketStatus status;

    @Schema(description = "When the ticket was resolved, cleared on reopen")
    private Instant resolvedAt;

    @Schema(description = "When the ticket was closed, cleared on reopen")
    private Instant closedAt;

    @Schema(description = "When an agent response is due, snapshotted at creation")
    private Instant responseDueAt;

    @Schema(description = "When a resolution is due, snapshotted at creation")
    private Instant resolutionDueAt;

    @Schema(description = "When the first agent message was recorded, null while outstanding")
    private Instant firstResponseAt;

    @Schema(description = "True once either SLA deadline was missed. Historical: never cleared "
            + "by resolving, closing or reopening.")
    private Boolean slaBreached;

    private UUID createdBy;

    private UUID updatedBy;

    private Instant createdAt;

    private Instant updatedAt;
}
