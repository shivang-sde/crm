package com.shivang.crm.modules.call.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.call.dto.CallCreateRequest;
import com.shivang.crm.modules.call.dto.CallDispositionRequest;
import com.shivang.crm.modules.call.dto.CallLinkRequest;
import com.shivang.crm.modules.call.dto.CallResponse;
import com.shivang.crm.modules.call.dto.CallUpdateRequest;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.service.CallService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
public class CallController {

    private final TenantContext tenantContext;
    private final CallService callService;

    @PostMapping
    @PreAuthorize("hasPermission('call', 'write')")
    public ResponseEntity<CallResponse> createCall(@RequestBody CallCreateRequest request) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        CallResponse response = callService.createCall(tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasPermission('call', 'read')")
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
    @PreAuthorize("hasPermission('call', 'read')")
    public ResponseEntity<CallResponse> getCall(@PathVariable UUID id) {
        UUID tenantId = tenantContext.getTenantId();
        CallResponse response = callService.getCall(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('call', 'write')")
    public ResponseEntity<CallResponse> updateCall(
        @PathVariable UUID id,
        @RequestBody CallUpdateRequest request
    ) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        CallResponse response = callService.updateCall(id, tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/link-entity")
    @PreAuthorize("hasPermission('call', 'write')")
    public ResponseEntity<CallResponse> linkCallEntity(
        @PathVariable UUID id,
        @Valid @RequestBody CallLinkRequest request
    ) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        CallResponse response = callService.linkCallEntity(id, tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/disposition")
    @PreAuthorize("hasPermission('call', 'write')")
    public ResponseEntity<CallResponse> saveDisposition(
        @PathVariable UUID id,
        @Valid @RequestBody CallDispositionRequest request
    ) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        CallResponse response = callService.saveDisposition(id, tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('call', 'write')")
    public ResponseEntity<Void> deleteCall(@PathVariable UUID id) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        callService.deleteCall(id, tenantId, userId);
        return ResponseEntity.noContent().build();
    }
}
