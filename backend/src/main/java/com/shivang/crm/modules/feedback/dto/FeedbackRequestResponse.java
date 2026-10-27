package com.shivang.crm.modules.feedback.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shivang.crm.modules.feedback.entity.FeedbackRequestStatus;
import com.shivang.crm.modules.feedback.entity.FeedbackType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L3.5: staff view of a feedback request.
 *
 * <p>{@code token} and {@code tokenPath} are populated only by create and
 * regenerate. They are absent from list responses on purpose, because the raw
 * token cannot be recovered from the stored hash and re-exposing it would turn
 * every list read into a link leak.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "FeedbackRequestResponse")
public class FeedbackRequestResponse {

    private UUID id;
    private UUID ticketId;
    private UUID accountId;
    private UUID contactId;
    private FeedbackType type;
    private FeedbackRequestStatus status;
    private Instant expiresAt;
    private Instant submittedAt;
    private Instant revokedAt;
    private UUID feedbackId;
    private Instant createdAt;

    @Schema(description = "Single-use public link token. Returned only when the link is created or regenerated.")
    private String token;

    @Schema(description = "Relative public path for the token, e.g. /feedback/<token>")
    private String tokenPath;
}
