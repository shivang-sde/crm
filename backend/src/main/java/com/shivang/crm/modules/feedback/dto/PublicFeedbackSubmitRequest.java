package com.shivang.crm.modules.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L3.5: anonymous submission body.
 *
 * <p>No type, account, contact, source or ticket input exists here. All of those
 * are derived from the request row, so a public caller cannot redirect the
 * response to another customer or claim a different provenance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PublicFeedbackSubmitRequest")
public class PublicFeedbackSubmitRequest {

    @Schema(description = "Score within the requested type's scale", example = "5")
    private Integer score;

    @Size(max = 4000, message = "Comment must be at most 4000 characters")
    @Schema(description = "Optional free-text comment")
    private String comment;
}
