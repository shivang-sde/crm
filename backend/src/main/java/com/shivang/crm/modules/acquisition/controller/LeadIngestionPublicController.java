package com.shivang.crm.modules.acquisition.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.acquisition.dto.LeadIngestionAcceptedResponse;
import com.shivang.crm.modules.acquisition.service.LeadIngestionIngressService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/public/acquisition")
@RequiredArgsConstructor
@Tag(name = "Lead Ingestion Public", description = "Public endpoint for generic lead webhook ingress")
public class LeadIngestionPublicController {

    private final LeadIngestionIngressService leadIngestionIngressService;

    @PostMapping("/{publicKey}")
    @Operation(summary = "Receive generic lead webhook", description = "Captures raw webhook payload as durable ingestion event")
    public ResponseEntity<ApiResponse<LeadIngestionAcceptedResponse>> ingest(
            @PathVariable String publicKey,
            @RequestBody(required = false) String rawBody,
            @RequestHeader HttpHeaders headers) {

        LeadIngestionAcceptedResponse response = leadIngestionIngressService.receive(publicKey, rawBody, headers);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }
}
