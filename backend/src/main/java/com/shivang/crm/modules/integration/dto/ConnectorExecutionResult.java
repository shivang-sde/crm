package com.shivang.crm.modules.integration.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorExecutionResult {

    private boolean success;
    private int statusCode;
    private Map<String, Object> responseBody = new HashMap<>();
    private Map<String, Object> requestHeaders = new HashMap<>();
    private Map<String, Object> requestBody = new HashMap<>();
    private String errorMessage;
    private long executionTimeMs;
    private UUID executionId;

    public ConnectorExecutionResult(boolean success, int statusCode, Map<String, Object> responseBody,
                                    Map<String, Object> requestHeaders, Map<String, Object> requestBody,
                                    String errorMessage, long executionTimeMs) {
        this.success = success;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.requestHeaders = requestHeaders == null ? new HashMap<>() : requestHeaders;
        this.requestBody = requestBody == null ? new HashMap<>() : requestBody;
        this.errorMessage = errorMessage;
        this.executionTimeMs = executionTimeMs;
    }
}
