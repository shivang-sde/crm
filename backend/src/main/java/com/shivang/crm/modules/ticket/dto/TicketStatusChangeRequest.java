package com.shivang.crm.modules.ticket.dto;

import com.shivang.crm.modules.ticket.entity.TicketStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L3.1-P1: request a Ticket status transition.
 *
 * <p>A single endpoint covers every transition, including resolve, close and
 * reopen. Separate verbs would fragment one state machine across several endpoints
 * and duplicate the authorization and lifecycle checks that must not be bypassable.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for changing a support ticket's status")
public class TicketStatusChangeRequest {

    @NotNull(message = "Status is required")
    @Schema(description = "Requested status. Idempotent: the current status is accepted and is a no-op.")
    private TicketStatus status;
}
