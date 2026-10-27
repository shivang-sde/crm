package com.shivang.crm.modules.feedback.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.feedback.dto.PublicFeedbackRequestResponse;
import com.shivang.crm.modules.feedback.dto.PublicFeedbackSubmitRequest;
import com.shivang.crm.modules.feedback.dto.PublicFeedbackSubmitResponse;
import com.shivang.crm.modules.feedback.service.FeedbackRequestService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * L3.5: the anonymous customer-facing feedback form.
 *
 * <p>The token is the credential. Responses expose only the requested scale and
 * lifecycle, never customer or ticket details, and the submission body can only
 * carry a score and comment. Everything else is taken from the request row.
 */
@RestController
@RequestMapping("/api/v1/public/feedback")
@RequiredArgsConstructor
@Tag(name = "Public Feedback", description = "Anonymous single-use ticket feedback links")
public class PublicFeedbackController {

    private final FeedbackRequestService feedbackRequestService;

    @GetMapping("/{token}")
    @Operation(summary = "Read a feedback link",
            description = "Returns the scale and lifecycle state only. Expired, revoked and submitted "
                    + "links return their terminal state as data.")
    public ResponseEntity<ApiResponse<PublicFeedbackRequestResponse>> metadata(
            @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(
                feedbackRequestService.publicMetadata(token)));
    }

    @PostMapping("/{token}")
    @Operation(summary = "Submit feedback through a public link",
            description = "Single use. The type, customer and provenance are derived from the link.")
    public ResponseEntity<ApiResponse<PublicFeedbackSubmitResponse>> submit(
            @PathVariable String token,
            @Valid @RequestBody PublicFeedbackSubmitRequest body) {

        PublicFeedbackSubmitResponse response = feedbackRequestService.submit(token, body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
