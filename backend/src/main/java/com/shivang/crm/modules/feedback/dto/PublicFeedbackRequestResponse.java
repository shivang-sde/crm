package com.shivang.crm.modules.feedback.dto;

import java.time.Instant;

import com.shivang.crm.modules.feedback.entity.FeedbackRequestStatus;
import com.shivang.crm.modules.feedback.entity.FeedbackType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L3.5: safe metadata for the anonymous feedback page.
 *
 * <p>Deliberately excludes ticket subject, customer name, email and every
 * internal id. The token is the only credential, so knowing it must not reveal
 * anything about the customer beyond the requested scale and lifecycle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PublicFeedbackRequestResponse")
public class PublicFeedbackRequestResponse {

    private FeedbackRequestStatus status;
    private FeedbackType type;
    private Integer minScore;
    private Integer maxScore;
    private Instant expiresAt;
    private Instant submittedAt;
}
