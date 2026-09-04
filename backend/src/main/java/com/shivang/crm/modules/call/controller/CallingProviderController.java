package com.shivang.crm.modules.call.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.call.dto.CallingProviderOption;
import com.shivang.crm.modules.call.service.CallingProviderService;
import com.shivang.crm.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/calling-providers")
@RequiredArgsConstructor
public class CallingProviderController {

    private final CallingProviderService callingProviderService;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CallingProviderOption>>> list() {
        UUID tenantId = tenantContext.requireTenantId();
        List<CallingProviderOption> providers = callingProviderService.getAvailableCallingProviders(tenantId);
        return ResponseEntity.ok(ApiResponse.success(providers));
    }
}
