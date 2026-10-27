package com.shivang.crm.modules.ticket.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L3.1-P1: create a support Ticket.
 *
 * <p>camelCase JSON with no per-field {@code @JsonProperty} renames, which is the
 * contract L3.0-P5 normalized the Deal and DealLineItem DTOs to. The repository has
 * a static contract check that fails if a snake_case rename reappears here.
 *
 * <p>No {@code status} is accepted: a Ticket is always created OPEN, so a client
 * cannot invent a delivered outcome. Status changes go through the status endpoint.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for creating a support ticket")
public class TicketCreateRequest {

    /**
     * The canonical customer identity. Mandatory — a Ticket cannot exist without an
     * Account. Validated live, not deleted, and in this tenant by
     * {@code CustomerRelationshipValidator}.
     */
    @NotNull(message = "Account is required")
    @Schema(description = "Account UUID the ticket belongs to")
    private UUID accountId;

    /** Optional. When present, must belong to {@link #accountId}. */
    @Schema(description = "Contact UUID who reported the issue")
    private UUID contactId;

    /**
     * Optional owner. When supplied it must be a live user of this tenant. Reassignment
     * is L3.1-P2; P1 only validates an owner given at creation.
     */
    @Schema(description = "Owning agent user UUID")
    private UUID ownerId;

    @NotBlank(message = "Subject is required")
    @Schema(description = "Short summary of the reported problem", example = "Customer cannot access dashboard")
    private String subject;

    @Schema(description = "Detailed description of the reported problem")
    private String description;
}
