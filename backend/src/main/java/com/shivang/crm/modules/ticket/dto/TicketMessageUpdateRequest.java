package com.shivang.crm.modules.ticket.dto;

import com.shivang.crm.modules.ticket.entity.TicketMessageVisibility;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L3.1-P3: edit an existing message.
 *
 * <p>Only {@code body} and {@code visibility} are editable, and only by the
 * message's original author. {@code ticketId}, {@code authorUserId} and
 * {@code tenantId} are structurally absent so they cannot be rewritten: moving a
 * message to another ticket would silently re-parent it and change its record scope,
 * and changing the author would let an editor take authorship of a statement they did
 * not make.
 *
 * <p>Both fields are optional. An omitted field is left alone rather than cleared,
 * matching the MapStruct {@code NullValuePropertyMappingStrategy.IGNORE} convention
 * used by the Ticket and Deal mappers.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for editing a ticket message. Only the original "
        + "author may edit. Omitted fields are unchanged.")
public class TicketMessageUpdateRequest {

    @Size(max = 10000, message = "Message body cannot exceed 10000 characters")
    @Schema(description = "Replacement message text")
    private String body;

    @Schema(description = "Who the message is addressed to")
    private TicketMessageVisibility visibility;
}
