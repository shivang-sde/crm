package com.shivang.crm.modules.ticket.dto;

import com.shivang.crm.modules.ticket.entity.TicketMessageVisibility;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L3.1-P3: write a message on a Ticket.
 *
 * <p>Only client-controlled fields appear here. {@code authorUserId} comes from the
 * authenticated principal, {@code ticketId} from the URL path, and {@code tenantId}
 * from {@code TenantContext} — none of them are accepted from the body, so a caller
 * cannot post as somebody else or address a message at another tenant.
 *
 * <p>There is deliberately no {@code accountId}/{@code contactId}/{@code ownerId}:
 * those live on the parent Ticket, and a client that could set them here could
 * contradict it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for adding a message to a support ticket")
public class TicketMessageCreateRequest {

    /**
     * Upper bound guards against an unbounded payload. The column is TEXT, so this
     * is a request-size concern rather than a storage one.
     */
    @NotBlank(message = "Message body is required")
    @Size(max = 10000, message = "Message body cannot exceed 10000 characters")
    @Schema(description = "Message text. Must not be blank.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String body;

    /**
     * Defaults to INTERNAL when omitted.
     *
     * <p>Internal is the safe default on purpose: a caller who forgets to state
     * visibility gets a working note, not something marked for the customer.
     * {@code CUSTOMER_VISIBLE} records intent only — P3 delivers nothing.
     */
    @Schema(description = "Who the message is addressed to. Defaults to INTERNAL.")
    private TicketMessageVisibility visibility;
}
