package com.shivang.crm.modules.call.controller;

import com.shivang.crm.modules.call.dto.CallCreateRequest;
import com.shivang.crm.modules.call.dto.CallResponse;
import com.shivang.crm.modules.call.dto.CallUpdateRequest;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.service.CallService;
import com.shivang.crm.modules.auth.security.TenantContext;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
public class CallController {

    private final TenantContext tenantContext;
    private final CallService callService;

    @PostMapping
    public ResponseEntity<CallResponse> createCall(@RequestBody CallCreateRequest request) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        CallResponse response = callService.createCall(tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<CallResponse>> listCalls(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(required = false) Call.CallStatus status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID tenantId = tenantContext.getTenantId();
        Page<CallResponse> response = callService.listCalls(tenantId, entityType, entityId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CallResponse> getCall(@PathVariable UUID id) {
        UUID tenantId = tenantContext.getTenantId();
        CallResponse response = callService.getCall(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CallResponse> updateCall(
        @PathVariable UUID id,
        @RequestBody CallUpdateRequest request
    ) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        CallResponse response = callService.updateCall(id, tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCall(@PathVariable UUID id) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        callService.deleteCall(id, tenantId, userId);
        return ResponseEntity.noContent().build();
    }
}
