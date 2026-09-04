package com.shivang.crm.modules.call.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.call.dto.CallingProviderOption;
import com.shivang.crm.modules.call.dto.ClickToCallRequest;
import com.shivang.crm.modules.call.dto.ClickToCallResponse;
import com.shivang.crm.modules.call.service.CallingProviderService;
import com.shivang.crm.modules.call.service.ClickToCallService;
import com.shivang.crm.shared.dto.ApiResponse;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/calls")
@org.springframework.security.access.prepost.PreAuthorize("hasPermission('call', 'write')")
@RequiredArgsConstructor
public class ClickToCallController {

    private final ClickToCallService clickToCallService;
    private final CallingProviderService callingProviderService;
    private final TenantContext tenantContext;

    @PostMapping("/click-to-call")
    public ResponseEntity<ApiResponse<ClickToCallResponse>> clickToCall(@RequestBody ClickToCallRequest request) {
        // FE/BE-WF-28: provider must be explicit for workflow path; for direct call
        // allow single-provider convenience but fail clearly if ambiguous.
        if (request.getProviderKey() == null || request.getProviderKey().isBlank()) {
            var available = callingProviderService.getAvailableCallingProviders(tenantContext.requireTenantId());
            if (available.isEmpty()) {
                throw new BusinessException("PROVIDER_NOT_CONFIGURED", "Configure a calling provider to use Click to Call");
            }
            if (available.size() == 1) {
                request.setProviderKey(available.get(0).providerKey());
            } else {
                throw new BusinessException("PROVIDER_REQUIRED", "Multiple calling providers configured — select a provider for this call");
            }
        }
        ClickToCallResponse resp = clickToCallService.clickToCall(request);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }
}
