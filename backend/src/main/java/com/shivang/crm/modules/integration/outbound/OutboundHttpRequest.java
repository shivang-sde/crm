package com.shivang.crm.modules.integration.outbound;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OutboundHttpRequest(
    UUID tenantId,
    UUID actorId,
    UUID workflowExecutionId,
    UUID workflowNodeExecutionId,
    OutboundHttpMethod method,
    String url,
    Map<String, List<String>> queryParams,
    Map<String, String> headers,
    Object body,
    UUID connectionId,
    UUID executionUserId
) {
    public OutboundHttpRequest(
            UUID tenantId,
            UUID actorId,
            UUID workflowExecutionId,
            UUID workflowNodeExecutionId,
            OutboundHttpMethod method,
            String url,
            Map<String, List<String>> queryParams,
            Map<String, String> headers,
            Object body,
            UUID connectionId) {
        this(tenantId, actorId, workflowExecutionId, workflowNodeExecutionId, method, url, queryParams, headers, body, connectionId, actorId);
    }
}