package com.shivang.crm.modules.feedback.dto;

import com.shivang.crm.modules.feedback.entity.FeedbackType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L3.5: staff request to ask for a score on a ticket.
 *
 * <p>Only scored types are meaningful for a public form, so this is validated in
 * the service rather than by a bean annotation: the allowed set lives on the enum.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FeedbackRequestCreateRequest")
public class FeedbackRequestCreateRequest {

    @Schema(description = "CSAT, NPS or CES. Defaults to CSAT.",
            example = "CSAT")
    private FeedbackType type;
}
