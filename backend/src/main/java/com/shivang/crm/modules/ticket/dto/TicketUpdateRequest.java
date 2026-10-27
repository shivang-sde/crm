package com.shivang.crm.modules.ticket.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L3.1-P1: update a support Ticket's mutable fields.
 *
 * <p>Only {@code subject}, {@code description}, {@code accountId}, {@code contactId}
 * and {@code ownerId} are updatable. Identity, tenancy, lifecycle and soft-delete
 * state are structurally absent from this DTO so they cannot be set through it:
 *
 * <ul>
 *   <li>{@code status} / {@code resolvedAt} / {@code closedAt} — status has its own
 *       endpoint, and the lifecycle matrix must be enforced there rather than
 *       bypassable through a field edit.</li>
 *   <li>{@code id} / {@code tenantId} / {@code createdBy} / {@code createdAt} /
 *       {@code deleted} / {@code deletedAt} / {@code deletedBy} — identity and
 *       tenancy are server-owned.</li>
 * </ul>
 *
 * <p>A null field means "unchanged", matching the MapStruct
 * {@code NullValuePropertyMappingStrategy.IGNORE} convention used by the Deal and
 * Offering mappers. This is a partial update, never a replacement: omitting a field
 * cannot clear it. Clearing {@code contactId} explicitly is out of scope.
 *
 * <p><b>L3.1-P2: {@code ownerId} here is inert.</b> Changing ownership is a
 * privileged assignment operation behind {@code ticket:assign} and
 * {@code POST /api/v1/tickets/{id}/assign}, so {@code ticket:write} must not imply
 * it. This field is retained only so a payload carrying {@code ownerId} is not
 * rejected as an unknown field; {@link TicketService#updateTicket} deliberately
 * ignores it. Reassignment through this DTO is impossible, which is why the
 * assignment path exists as a separate endpoint rather than as a branch here.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for updating a support ticket. Omitted fields are unchanged.")
public class TicketUpdateRequest {

    @Size(max = 255, message = "Subject cannot exceed 255 characters")
    @Schema(description = "Short summary of the reported problem")
    private String subject;

    @Schema(description = "Detailed description of the reported problem")
    private String description;

    /**
     * Changing the Account revalidates both the new Account and the Contact against
     * it, so a move can never leave a Contact pointing at a different customer.
     */
    @Schema(description = "Move the ticket to a different account")
    private UUID accountId;

    @Schema(description = "Contact UUID who reported the issue")
    private UUID contactId;

    /**
     * L3.1-P2: accepted but IGNORED. Ownership changes flow only through
     * {@code POST /api/v1/tickets/{id}/assign}, which requires {@code ticket:assign}.
     */
    @Schema(description = "Ignored. Use POST /api/v1/tickets/{id}/assign to change ownership "
            + "(requires ticket:assign).")
    private UUID ownerId;
}
