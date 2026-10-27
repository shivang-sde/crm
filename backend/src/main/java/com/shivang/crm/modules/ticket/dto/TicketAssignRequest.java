package com.shivang.crm.modules.ticket.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L3.1-P2: request to change a Ticket's owning agent.
 *
 * <p>{@code ownerId} is required and deliberately NOT nullable-tolerant: clearing
 * ownership is not a supported operation. Unassignment is P2-explicitly out of
 * scope, so a request without an owner is a client error rather than a silent
 * unassign.
 *
 * <p>There is no tenant field. Tenancy comes from {@code TenantContext}, never from
 * the request body, so a cross-tenant assignment cannot be attempted by forging a
 * payload.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for assigning a support ticket to an agent. "
        + "Requires the ticket:assign permission.")
public class TicketAssignRequest {

    @NotNull(message = "ownerId is required")
    @Schema(description = "User UUID of the agent who will own the ticket. Must be an "
            + "active user of the same tenant.", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID ownerId;
}
