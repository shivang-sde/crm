package com.shivang.crm.modules.workflow.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.call.dto.ClickToCallRequest;
import com.shivang.crm.modules.call.dto.ClickToCallResponse;
import com.shivang.crm.modules.call.service.impl.DefaultClickToCallService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowClickToCallService {

    private final DefaultClickToCallService clickToCallService;

    public ClickToCallResponse execute(UUID tenantId, UUID actorId, ClickToCallRequest request) {
        return clickToCallService.clickToCall(tenantId, actorId, request);
    }
}