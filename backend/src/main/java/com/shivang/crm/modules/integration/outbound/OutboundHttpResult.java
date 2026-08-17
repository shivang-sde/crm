package com.shivang.crm.modules.integration.outbound;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record OutboundHttpResult(
    boolean success,
    int statusCode,
    JsonNode response,
    long executionTimeMs,
    UUID correlationId,
    String errorCode,
    String errorMessage
) {
}