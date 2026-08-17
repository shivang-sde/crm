package com.shivang.crm.modules.workflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.workflow.dto.WorkflowCreateRequest;
import com.shivang.crm.modules.workflow.dto.WorkflowEdgeRequest;
import com.shivang.crm.modules.workflow.dto.WorkflowGraphValidationError;
import com.shivang.crm.modules.workflow.dto.WorkflowNodeRequest;
import com.shivang.crm.modules.workflow.dto.WorkflowVersionCreateRequest;
import com.shivang.crm.modules.workflow.service.WorkflowDefinitionService;
import com.shivang.crm.shared.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService workflowDefinitionService;
    private final TenantContext tenantContext;

    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> createWorkflow(@Valid @RequestBody WorkflowCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(workflowDefinitionService.createWorkflow(tenant(), request.getName())));
    }

    @PostMapping("/{workflowId}/versions")
    public ResponseEntity<ApiResponse<UUID>> createVersion(@PathVariable UUID workflowId, @Valid @RequestBody WorkflowVersionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(workflowDefinitionService.createDraftVersion(tenant(), workflowId, request)));
    }

    @PostMapping("/versions/{versionId}/nodes")
    public ResponseEntity<ApiResponse<UUID>> addNode(@PathVariable UUID versionId, @Valid @RequestBody WorkflowNodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(workflowDefinitionService.addNode(tenant(), versionId, request)));
    }

    @PutMapping("/versions/{versionId}/nodes/{nodeId}")
    public ResponseEntity<ApiResponse<String>> updateNode(@PathVariable UUID versionId, @PathVariable UUID nodeId, @Valid @RequestBody WorkflowNodeRequest request) {
        workflowDefinitionService.updateNode(tenant(), versionId, nodeId, request);
        return ResponseEntity.ok(ApiResponse.success("Workflow node updated"));
    }

    @DeleteMapping("/versions/{versionId}/nodes/{nodeId}")
    public ResponseEntity<ApiResponse<String>> deleteNode(@PathVariable UUID versionId, @PathVariable UUID nodeId) {
        workflowDefinitionService.deleteNode(tenant(), versionId, nodeId, user());
        return ResponseEntity.ok(ApiResponse.success("Workflow node deleted"));
    }

    @PostMapping("/versions/{versionId}/edges")
    public ResponseEntity<ApiResponse<UUID>> addEdge(@PathVariable UUID versionId, @Valid @RequestBody WorkflowEdgeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(workflowDefinitionService.addEdge(tenant(), versionId, request)));
    }

    @PutMapping("/versions/{versionId}/edges/{edgeId}")
    public ResponseEntity<ApiResponse<String>> updateEdge(@PathVariable UUID versionId, @PathVariable UUID edgeId, @Valid @RequestBody WorkflowEdgeRequest request) {
        workflowDefinitionService.updateEdge(tenant(), versionId, edgeId, request);
        return ResponseEntity.ok(ApiResponse.success("Workflow edge updated"));
    }

    @DeleteMapping("/versions/{versionId}/edges/{edgeId}")
    public ResponseEntity<ApiResponse<String>> deleteEdge(@PathVariable UUID versionId, @PathVariable UUID edgeId) {
        workflowDefinitionService.deleteEdge(tenant(), versionId, edgeId, user());
        return ResponseEntity.ok(ApiResponse.success("Workflow edge deleted"));
    }

    @PostMapping("/versions/{versionId}/validate")
    public ResponseEntity<ApiResponse<List<WorkflowGraphValidationError>>> validate(@PathVariable UUID versionId) {
        return ResponseEntity.ok(ApiResponse.success(workflowDefinitionService.validate(tenant(), versionId)));
    }

    @PostMapping("/versions/{versionId}/activate")
    public ResponseEntity<ApiResponse<String>> activate(@PathVariable UUID versionId) {
        workflowDefinitionService.activate(tenant(), versionId);
        return ResponseEntity.ok(ApiResponse.success("Workflow version activated"));
    }

    private UUID tenant() { return tenantContext.requireTenantId(); }

    private UUID user() {
        if (!tenantContext.hasUser()) throw new IllegalStateException("User context is not available");
        return tenantContext.getUserId();
    }
}