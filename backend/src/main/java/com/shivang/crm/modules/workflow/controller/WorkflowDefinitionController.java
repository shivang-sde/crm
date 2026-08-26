package com.shivang.crm.modules.workflow.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.workflow.dto.WorkflowCreateRequest;
import com.shivang.crm.modules.workflow.dto.WorkflowExecutionControlResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowGraphResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowMetadataResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowVersionResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowEdgeRequest;
import com.shivang.crm.modules.workflow.dto.WorkflowExecutionDetailResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowExecutionReplayResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowExecutionSummaryResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowGraphValidationError;
import com.shivang.crm.modules.workflow.dto.WorkflowNodeExecutionResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowNodeRequest;
import com.shivang.crm.modules.workflow.dto.WorkflowVersionCreateRequest;
import com.shivang.crm.modules.workflow.entity.Workflow;
import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowVersion;
import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeExecution;
import com.shivang.crm.modules.workflow.service.WorkflowExecutionControlService;
import com.shivang.crm.modules.workflow.service.WorkflowDefinitionService;
import com.shivang.crm.modules.workflow.service.WorkflowExecutionQueryService;
import com.shivang.crm.modules.workflow.service.WorkflowExecutionReplayService;
import com.shivang.crm.modules.workflow.service.WorkflowMetadataService;
import com.shivang.crm.shared.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowExecutionReplayService workflowExecutionReplayService;
    private final WorkflowExecutionControlService workflowExecutionControlService;
    private final WorkflowExecutionQueryService workflowExecutionQueryService;
    private final WorkflowMetadataService workflowMetadataService;
    private final TenantContext tenantContext;

    @GetMapping("/metadata")
    public ResponseEntity<ApiResponse<WorkflowMetadataResponse>> getMetadata() {
        return ResponseEntity.ok(ApiResponse.success(workflowMetadataService.getMetadata()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> createWorkflow(@Valid @RequestBody WorkflowCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(workflowDefinitionService.createWorkflow(tenant(), request.getName())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkflowResponse>>> listWorkflows(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<Workflow> workflows = workflowDefinitionService.listWorkflows(tenant(), page, size);
        Map<UUID, UUID> activeVersionsByWorkflow = workflowDefinitionService.getActiveVersionIds(tenant());
        List<WorkflowResponse> response = workflows.getContent().stream()
            .map(workflow -> toWorkflowResponse(workflow, activeVersionsByWorkflow.get(workflow.getId())))
            .toList();
        Map<String, Object> meta = Map.of(
            "page", workflows.getNumber(),
            "size", workflows.getSize(),
            "total", workflows.getTotalElements(),
            "totalPages", workflows.getTotalPages()
        );
        return ResponseEntity.ok(ApiResponse.success(response, meta));
    }

    @GetMapping("/{workflowId}")
    public ResponseEntity<ApiResponse<WorkflowResponse>> getWorkflow(@PathVariable UUID workflowId) {
        Workflow workflow = workflowDefinitionService.getWorkflow(tenant(), workflowId);
        return ResponseEntity.ok(ApiResponse.success(toWorkflowResponse(workflow, null)));
    }

    @GetMapping("/{workflowId}/versions")
    public ResponseEntity<ApiResponse<List<WorkflowVersionResponse>>> listVersions(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(ApiResponse.success(workflowDefinitionService.listVersions(tenant(), workflowId)));
    }

    @GetMapping("/versions/{versionId}")
    public ResponseEntity<ApiResponse<WorkflowVersionResponse>> getVersion(@PathVariable UUID versionId) {
        WorkflowVersion version = workflowDefinitionService.getVersion(tenant(), versionId);
        return ResponseEntity.ok(ApiResponse.success(new WorkflowVersionResponse(
            version.getId(),
            version.getWorkflow().getId(),
            version.getVersionNumber(),
            version.getStatus(),
            version.getTriggerEntityType(),
            version.getTriggerEventType(),
            version.getCreatedAt(),
            version.getUpdatedAt()
        )));
    }

    @GetMapping("/versions/{versionId}/graph")
    public ResponseEntity<ApiResponse<WorkflowGraphResponse>> getGraph(@PathVariable UUID versionId) {
        return ResponseEntity.ok(ApiResponse.success(workflowDefinitionService.getGraph(tenant(), versionId)));
    }

    private WorkflowResponse toWorkflowResponse(Workflow workflow, UUID activeVersionId) {
        return new WorkflowResponse(
            workflow.getId(),
            workflow.getName(),
            workflow.getStatus(),
            activeVersionId,
            workflow.getCreatedAt(),
            workflow.getUpdatedAt()
        );
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

    @PostMapping("/{workflowId}/deactivate")
    public ResponseEntity<ApiResponse<String>> deactivate(@PathVariable UUID workflowId) {
        workflowDefinitionService.deactivate(tenant(), workflowId);
        return ResponseEntity.ok(ApiResponse.success("Workflow deactivated"));
    }

    @PostMapping("/executions/{executionId}/retry")
    public ResponseEntity<ApiResponse<WorkflowExecutionControlResponse>> retryExecution(@PathVariable UUID executionId) {
        return ResponseEntity.ok(ApiResponse.success(
            workflowExecutionControlService.retryExecution(tenant(), executionId)));
    }

    @PostMapping("/executions/{executionId}/replay")
    public ResponseEntity<ApiResponse<WorkflowExecutionReplayResponse>> replay(@PathVariable UUID executionId) {
        WorkflowExecution replay = workflowExecutionReplayService.replay(tenant(), executionId);
        WorkflowExecutionReplayResponse response = new WorkflowExecutionReplayResponse(
            replay.getId(),
            replay.getStatus(),
            replay.getReplayedFromExecutionId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/executions")
    public ResponseEntity<ApiResponse<List<WorkflowExecutionSummaryResponse>>> listExecutions(
        @RequestParam(required = false) WorkflowExecutionStatus status,
        @RequestParam(required = false) UUID workflowId,
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<WorkflowExecution> executions = workflowExecutionQueryService.list(
            tenant(), status, workflowId, entityType, entityId, page, size
        );
        java.util.Map<String, Object> meta = java.util.Map.of(
            "page", executions.getNumber(),
            "size", executions.getSize(),
            "total", executions.getTotalElements(),
            "totalPages", executions.getTotalPages()
        );
        return ResponseEntity.ok(ApiResponse.success(executions.getContent().stream().map(this::toSummary).toList(), meta));
    }

    @GetMapping("/executions/{executionId}")
    public ResponseEntity<ApiResponse<WorkflowExecutionDetailResponse>> getExecution(@PathVariable UUID executionId) {
        UUID tenantId = tenant();
        WorkflowExecution execution = workflowExecutionQueryService.getExecution(tenantId, executionId);
        List<WorkflowNodeExecution> nodeExecutions = workflowExecutionQueryService.getNodeExecutions(tenantId, executionId);
        return ResponseEntity.ok(ApiResponse.success(toDetail(execution, nodeExecutions)));
    }

    private WorkflowExecutionSummaryResponse toSummary(WorkflowExecution execution) {
        return new WorkflowExecutionSummaryResponse(
            execution.getId(),
            execution.getWorkflow().getId(),
            execution.getWorkflowVersion().getId(),
            execution.getEntityType(),
            execution.getEntityId(),
            execution.getEventType(),
            execution.getStatus(),
            execution.getStartedAt(),
            execution.getCompletedAt(),
            execution.getCreatedAt(),
            execution.getUpdatedAt(),
            execution.getErrorCode(),
            execution.getErrorMessage()
        );
    }

    private WorkflowExecutionDetailResponse toDetail(WorkflowExecution execution, List<WorkflowNodeExecution> nodeExecutions) {
        return new WorkflowExecutionDetailResponse(
            execution.getId(),
            execution.getWorkflow().getId(),
            execution.getWorkflowVersion().getId(),
            execution.getEntityType(),
            execution.getEntityId(),
            execution.getEventType(),
            execution.getStatus(),
            execution.getStartedAt(),
            execution.getCompletedAt(),
            execution.getCreatedAt(),
            execution.getUpdatedAt(),
            execution.getAttemptCount(),
            execution.getLastHeartbeatAt(),
            execution.getLastErrorCode(),
            execution.getLastErrorMessage(),
            execution.getReplayedFromExecutionId(),
            execution.getCausedByExecutionId(),
            execution.getCausedByEventId(),
            execution.getChainDepth(),
            nodeExecutions.stream().map(this::toNodeExecution).toList()
        );
    }

    private WorkflowNodeExecutionResponse toNodeExecution(WorkflowNodeExecution nodeExecution) {
        return new WorkflowNodeExecutionResponse(
            nodeExecution.getId(),
            nodeExecution.getWorkflowNode().getId(),
            nodeExecution.getNodeKey(),
            nodeExecution.getNodeType(),
            nodeExecution.getStatus(),
            nodeExecution.getAttemptCount(),
            nodeExecution.getStartedAt(),
            nodeExecution.getCompletedAt(),
            nodeExecution.getNextAttemptAt(),
            nodeExecution.getOutputContext(),
            nodeExecution.getLastErrorCode(),
            nodeExecution.getLastErrorMessage()
        );
    }

    private UUID tenant() { return tenantContext.requireTenantId(); }

    private UUID user() {
        if (!tenantContext.hasUser()) throw new IllegalStateException("User context is not available");
        return tenantContext.getUserId();
    }
}