package com.shivang.crm.modules.feedback.dto;

import com.shivang.crm.modules.feedback.entity.FeedbackSource;
import com.shivang.crm.modules.feedback.entity.FeedbackType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L3.3-P1: the external feedback submission contract.
 *
 * <p>Deliberately does <b>not</b> accept {@code accountId} or {@code contactId}. An
 * external system holds a customer's email address; it does not hold, and must not
 * be trusted with, internal CRM UUIDs. The email is resolved server-side against the
 * tenant's own Account and Contact records, so a submission cannot attach feedback
 * to an arbitrary customer — and a caller cannot probe for customer ids by
 * supplying one and observing whether it was accepted.
 *
 * <p>{@code expectedAccountId} is a narrow, optional consistency guard for the
 * staff UI. It is not an identity input and never overrides the email resolution:
 * when present, the resolved Account must match it or the submission is rejected.
 * Staff never choose an arbitrary account id; the page supplies the account that is
 * already open.
 *
 * <p>The submitted email is <b>not persisted</b>. Account and Contact already own it;
 * copying customer PII into a second table would create a second place for it to go
 * stale. Only the resolved internal ids are stored.
 *
 * <p>There is no DTO-level {@code @Min}/{@code @Max} on {@code score}. The valid
 * range depends on {@link #type}, so it is enforced in the domain
 * ({@code FeedbackType.acceptsScore}) where the scale is declared, and a
 * range annotation here would either duplicate that scale or apply the wrong one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FeedbackCreateRequest",
        description = "Submit customer feedback. The customer is identified by email address, "
                + "not by internal Account or Contact identifiers.")
public class FeedbackCreateRequest {

    @NotBlank(message = "Email is required to identify the customer")
    @Email(message = "Email must be a valid address")
    @Size(max = 255, message = "Email must be at most 255 characters")
    @Schema(description = "Customer email address; resolved to an existing Account or Contact "
            + "inside the authenticated tenant. Never auto-creates a customer.",
            example = "customer@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotNull(message = "Feedback type is required")
    @Schema(description = "NPS (0-10), CSAT (1-5), CES (1-7), or an unscored GENERAL_FEEDBACK/COMPLAINT",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private FeedbackType type;

    @Schema(description = "Score within this type's scale. Required for NPS, CSAT and CES; "
            + "optional for GENERAL_FEEDBACK and COMPLAINT. Out-of-range values are rejected, never clamped.")
    private Integer score;

    @Size(max = 4000, message = "Comment must be at most 4000 characters")
    @Schema(description = "Free-text customer comment")
    private String comment;

    @Schema(description = "Provenance of the feedback. Not an integration mechanism — the API is "
            + "the integration boundary. Part of the external-reference dedup key.")
    private FeedbackSource source;

    @Size(max = 255, message = "External reference must be at most 255 characters")
    @Schema(description = "The source system's stable submission identifier. With `source`, makes a "
            + "resubmission idempotent instead of creating a duplicate row.")
    private String externalReference;

    @Schema(description = "Optional staff-UI consistency guard, not an identity input. When present, "
            + "the email must resolve to this Account or the submission is rejected. External callers "
            + "should omit it.")
    private java.util.UUID expectedAccountId;

    @Schema(description = "Optional correlation to an existing support ticket. Validated to belong to "
            + "the resolved Account and the current tenant. P1 creates no tickets.")
    private java.util.UUID ticketId;
}
