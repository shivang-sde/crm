package com.shivang.crm.modules.ticket.dto;

import java.time.Instant;
import java.util.UUID;

import com.shivang.crm.modules.ticket.entity.TicketMessageVisibility;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L3.1-P3: TicketMessage response.
 *
 * <p>camelCase JSON with no {@code @JsonProperty} renames, per the L3.0-P5 contract
 * that the Ticket API already follows.
 *
 * <p>Soft-delete columns are absent: a deleted message is not returned by any normal
 * read, so exposing {@code deleted}/{@code deletedAt} would only invite a client to
 * branch on state it should never observe.
 *
 * <p>Neither {@code accountId} nor {@code ownerId} appears — they are the parent
 * Ticket's fields, and returning them here would let a client assemble a picture
 * where the message appears to own its own customer and agent.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A message in a support ticket conversation")
public class TicketMessageResponse {

    private UUID id;

    @Schema(description = "Tenant that owns the message and its parent ticket")
    private UUID tenantId;

    @Schema(description = "Parent ticket")
    private UUID ticketId;

    @Schema(description = "The agent who wrote the message")
    private UUID authorUserId;

    private String body;

    /**
     * Who the message is addressed to. {@code CUSTOMER_VISIBLE} records intent only;
     * no delivery is performed.
     */
    private TicketMessageVisibility visibility;

    private Instant createdAt;

    private Instant updatedAt;
}
