package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.integration.outbound.OutboundHttpRequest;
import com.shivang.crm.modules.integration.outbound.OutboundHttpResult;
import com.shivang.crm.modules.integration.outbound.OutboundHttpService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowHttpApiService {

    private final OutboundHttpService outboundHttpService;

    public OutboundHttpResult execute(
        UUID tenantId,
        UUID actorId,
        UUID workflowExecutionId,
        UUID workflowNodeExecutionId,
        OutboundHttpRequest request
    ) {
        if (request == null || !tenantId.equals(request.tenantId()) || !actorId.equals(request.actorId())) {
            throw new WorkflowRuntimeException("WORKFLOW_HTTP_API_INVALID_CONFIG", "HTTP request identity is invalid");
        }
        return outboundHttpService.execute(request);
    }
}