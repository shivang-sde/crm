package com.shivang.crm.modules.workflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.workflow.service.WorkflowHttpConnectionService;
import com.shivang.crm.modules.workflow.service.WorkflowHttpConnectionService.ConnectionTestRequest;
import com.shivang.crm.modules.workflow.service.WorkflowHttpConnectionService.ConnectionTestResponse;
import com.shivang.crm.modules.workflow.service.WorkflowHttpConnectionService.HttpConnectionRequest;
import com.shivang.crm.modules.workflow.service.WorkflowHttpConnectionService.HttpConnectionResponse;
import com.shivang.crm.shared.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Tenant-scoped provisioning for Outbound HTTP connections backing the
 * HTTP_API workflow action. Responses carry safe metadata only — credential
 * values are write-only and are never returned by any operation here.
 *
 * RBAC follows the workflow module convention via RbacFilter:
 * GET → workflow/read, POST/PUT → workflow/write, DELETE → workflow/delete.
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowHttpConnectionController {

    private final WorkflowHttpConnectionService connectionService;
    private final TenantContext tenantContext;

    @GetMapping("/http-connections")
    public ResponseEntity<ApiResponse<List<HttpConnectionResponse>>> listConnections() {
        return ResponseEntity.ok(ApiResponse.success(connectionService.list(tenantContext.requireTenantId())));
    }

    @GetMapping("/http-connections/{id}")
    public ResponseEntity<ApiResponse<HttpConnectionResponse>> getConnection(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(connectionService.get(tenantContext.requireTenantId(), id)));
    }

    @PostMapping("/http-connections")
    public ResponseEntity<ApiResponse<HttpConnectionResponse>> createConnection(@Valid @RequestBody HttpConnectionRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(connectionService.create(tenantId, tenantContext.getUserId(), request)));
    }

    @PutMapping("/http-connections/{id}")
    public ResponseEntity<ApiResponse<HttpConnectionResponse>> updateConnection(
            @PathVariable UUID id, @RequestBody HttpConnectionRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        return ResponseEntity.ok(ApiResponse.success(connectionService.update(tenantId, tenantContext.getUserId(), id, request)));
    }

    @DeleteMapping("/http-connections/{id}")
    public ResponseEntity<ApiResponse<String>> deleteConnection(@PathVariable UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        connectionService.delete(tenantId, tenantContext.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Connection deleted"));
    }

    @PostMapping("/http-connections/{id}/test")
    public ResponseEntity<ApiResponse<ConnectionTestResponse>> testConnection(
            @PathVariable UUID id, @Valid @RequestBody ConnectionTestRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        return ResponseEntity.ok(ApiResponse.success(connectionService.test(tenantId, tenantContext.getUserId(), id, request)));
    }
}
