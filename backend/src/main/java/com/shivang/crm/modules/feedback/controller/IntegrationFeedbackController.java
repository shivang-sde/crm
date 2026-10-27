package com.shivang.crm.modules.feedback.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.feedback.dto.FeedbackCreateRequest;
import com.shivang.crm.modules.feedback.dto.FeedbackResponse;
import com.shivang.crm.modules.feedback.service.FeedbackIntegrationService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * L3.5: machine-to-machine feedback ingestion.
 *
 * <p>Authenticated by {@code CommercialIntegrationAuthFilter} (X-API-Key or
 * X-Commercial-Token), not JWT. The filter binds the tenant from the key and
 * marks the request {@code ROLE_INTEGRATION}, which lets
 * {@code FeedbackService} preserve the declared customer provenance.
 */
@RestController
@RequestMapping("/api/v1/integrations/commercial/feedback")
@RequiredArgsConstructor
@Tag(name = "Commercial Integration - Feedback",
        description = "Trusted feedback submission using the tenant commercial API key")
public class IntegrationFeedbackController {

    private final FeedbackIntegrationService feedbackIntegrationService;

    @PostMapping
    @SecurityRequirement(name = "commercialApiKey")
    @Operation(summary = "Submit feedback from a trusted integration",
            description = "Identified by customer email, exactly like the authenticated feedback API. "
                    + "Source defaults to API. Reuses the tenant commercial API key; no separate credential exists.")
    public ResponseEntity<ApiResponse<FeedbackResponse>> submit(
            @Valid @RequestBody FeedbackCreateRequest request) {

        FeedbackResponse created = feedbackIntegrationService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, Map.of("feedbackId", created.getId())));
    }
}
