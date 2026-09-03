package com.shivang.crm.modules.acquisition.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.acquisition.dto.FormDefinitionResponse;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;
import com.shivang.crm.modules.acquisition.service.LeadIngestionFormService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/public/forms")
@RequiredArgsConstructor
@Tag(name = "Public Form", description = "Public form definition and submission")
public class LeadIngestionFormPublicController {

    private final LeadIngestionFormService leadIngestionFormService;

    @GetMapping("/{publicKey}")
    @Operation(summary = "Get public form definition", description = "Returns form fields derived from active mappings; public, no auth")
    public ResponseEntity<ApiResponse<FormDefinitionResponse>> getDefinition(
            @PathVariable String publicKey) {

        FormDefinitionResponse def = leadIngestionFormService.getDefinition(publicKey);
        return ResponseEntity.ok(ApiResponse.success(def));
    }

    @PostMapping("/{publicKey}")
    @Operation(summary = "Submit public form", description = "Creates an acquisition event per submission and runs universal pipeline")
    public ResponseEntity<ApiResponse<Map<String, String>>> submit(
            @PathVariable String publicKey,
            @RequestBody(required = false) String rawBody,
            @RequestHeader HttpHeaders headers) {

        LeadIngestionEvent result = leadIngestionFormService.submit(publicKey, rawBody, headers);

        // Do not expose internal eventId/leadId to public user
        if (result.getStatus() == LeadIngestionEventStatus.REJECTED) {
            String msg = result.getErrorMessage() != null ? result.getErrorMessage() : "Validation failed";
            // Return 400 with safe validation message, no internal details
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", msg));
        }
        if (result.getStatus() == LeadIngestionEventStatus.FAILED) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("PROCESSING_ERROR", "Submission failed, please try again"));
        }
        // PROCESSED and DUPLICATE both appear as success to public user (don't reveal duplicate as error)
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success(Map.of("message", "Submitted successfully")));
    }
}
