package com.shivang.crm.modules.feedback.dto;

import java.time.Instant;
import java.util.UUID;

import com.shivang.crm.modules.feedback.entity.FeedbackSource;
import com.shivang.crm.modules.feedback.entity.FeedbackType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L3.3-P1: a submitted feedback record.
 *
 * <p>camelCase throughout and no per-field JSON name overrides, matching the L3.0-P5
 * contract every other response DTO in this codebase follows.
 *
 * <p>The resolved {@code accountId} / {@code contactId} are exposed here because this
 * is the authenticated CRM read surface — the same convention every other CRM
 * response uses. They are deliberately absent from the submission request, so
 * {@code FeedbackCreateRequest} and this DTO have different shapes by design.
 *
 * <p>{@code email} is absent because it is not stored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FeedbackResponse", description = "A submitted customer feedback record")
public class FeedbackResponse {

    @Schema(description = "This feedback record's id")
    private UUID id;

    @Schema(description = "The Account this feedback is anchored on")
    private UUID accountId;

    @Schema(description = "The Contact who gave the feedback, when it resolved to one")
    private UUID contactId;

    @Schema(description = "The Ticket this feedback correlates to, when supplied")
    private UUID ticketId;

    @Schema(description = "Feedback type")
    private FeedbackType type;

    @Schema(description = "Score within the type's scale")
    private Integer score;

    @Schema(description = "Free-text customer comment")
    private String comment;

    @Schema(description = "Where the feedback originated")
    private FeedbackSource source;

    @Schema(description = "The source system's submission identifier, when supplied")
    private String externalReference;

    @Schema(description = "Owning CRM user, inherited from the Account. Drives OWN/TEAM/ALL record scope.")
    private UUID ownerUserId;

    @Schema(description = "When the feedback was submitted")
    private Instant createdAt;
}
