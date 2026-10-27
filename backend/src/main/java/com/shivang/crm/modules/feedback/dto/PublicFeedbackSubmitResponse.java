package com.shivang.crm.modules.feedback.dto;

import java.time.Instant;

import com.shivang.crm.modules.feedback.entity.FeedbackRequestStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** L3.5: anonymous submission acknowledgement. No internal ids are returned. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PublicFeedbackSubmitResponse")
public class PublicFeedbackSubmitResponse {

    private FeedbackRequestStatus status;
    private Instant submittedAt;
}
