package com.shivang.crm.modules.workflow.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.dto.WorkflowEdgeRequest;
import com.shivang.crm.modules.workflow.dto.WorkflowGraphEdgeResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowGraphNodeResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowGraphResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowGraphValidationError;
import com.shivang.crm.modules.workflow.dto.WorkflowNodeRequest;
import com.shivang.crm.modules.workflow.dto.WorkflowResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowVersionCreateRequest;
import com.shivang.crm.modules.workflow.dto.WorkflowVersionResponse;
import com.shivang.crm.modules.workflow.entity.Workflow;
import com.shivang.crm.modules.workflow.entity.WorkflowEdge;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;
import com.shivang.crm.modules.workflow.entity.WorkflowStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowVersion;
import com.shivang.crm.modules.workflow.entity.WorkflowVersionStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowEdgeRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowVersionRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkflowDefinitionService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final WorkflowEdgeRepository workflowEdgeRepository;
    private final WorkflowGraphValidationService graphValidationService;

    public UUID createWorkflow(UUID tenantId, String name) {
        return workflowRepository.save(Workflow.builder()
            .tenantId(tenantId)
            .name(name.trim())
            .status(WorkflowStatus.DRAFT)
            .build()).getId();
    }

    @Transactional(readOnly = true)
    public Page<Workflow> listWorkflows(UUID tenantId, int page, int size) {
        return workflowRepository.findByTenantIdAndDeletedFalse(
            tenantId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
    }

    @Transactional(readOnly = true)
    public Workflow getWorkflow(UUID tenantId, UUID workflowId) {
        return requireWorkflow(workflowId, tenantId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UUID> getActiveVersionIds(UUID tenantId) {
        return workflowVersionRepository
            .findByTenantIdAndStatusAndDeletedFalse(tenantId, WorkflowVersionStatus.ACTIVE)
            .stream()
            .collect(Collectors.toMap(
                version -> version.getWorkflow().getId(),
                WorkflowVersion::getId,
                (first, second) -> first
            ));
    }

    @Transactional(readOnly = true)
    public List<WorkflowVersionResponse> listVersions(UUID tenantId, UUID workflowId) {
        requireWorkflow(workflowId, tenantId);

        return workflowVersionRepository
            .findByWorkflowIdAndTenantIdAndDeletedFalseOrderByVersionNumberDesc(workflowId, tenantId)
            .stream()
            .map(this::toVersionResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowVersion getVersion(UUID tenantId, UUID versionId) {
        return requireVersion(tenantId, versionId);
    }

    @Transactional(readOnly = true)
    public WorkflowGraphResponse getGraph(UUID tenantId, UUID versionId) {
        WorkflowVersion version = requireVersion(tenantId, versionId);

        List<WorkflowNode> nodes = workflowNodeRepository
            .findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantId, versionId)
            .stream()
            .sorted(Comparator.comparing(WorkflowNode::getCreatedAt)
                .thenComparing(WorkflowNode::getNodeKey))
            .toList();
        List<WorkflowEdge> edges = workflowEdgeRepository
            .findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantId, versionId)
            .stream()
            .sorted(Comparator.comparing(WorkflowEdge::getCreatedAt))
            .toList();

        return new WorkflowGraphResponse(
            toVersionResponse(version),
            nodes.stream().map(this::toGraphNodeResponse).toList(),
            edges.stream().map(this::toGraphEdgeResponse).toList()
        );
    }

    private WorkflowVersionResponse toVersionResponse(WorkflowVersion version) {
        return new WorkflowVersionResponse(
            version.getId(),
            version.getWorkflow().getId(),
            version.getVersionNumber(),
            version.getStatus(),
            version.getTriggerEntityType(),
            version.getTriggerEventType(),
            version.getCreatedAt(),
            version.getUpdatedAt()
        );
    }

    private WorkflowGraphNodeResponse toGraphNodeResponse(WorkflowNode node) {
        return new WorkflowGraphNodeResponse(
            node.getId(),
            node.getNodeKey(),
            node.getNodeType(),
            node.getName(),
            node.getConfiguration(),
            node.getCreatedAt(),
            node.getUpdatedAt()
        );
    }

    private WorkflowGraphEdgeResponse toGraphEdgeResponse(WorkflowEdge edge) {
        return new WorkflowGraphEdgeResponse(
            edge.getId(),
            edge.getSourceNode().getId(),
            edge.getTargetNode().getId(),
            edge.getEdgeKey(),
            edge.getConfiguration(),
            edge.getCreatedAt(),
            edge.getUpdatedAt()
        );
    }

    public UUID createDraftVersion(UUID tenantId, UUID workflowId, WorkflowVersionCreateRequest request) {
        Workflow workflow = workflowRepository.findByIdAndTenantIdAndDeletedFalse(workflowId, tenantId)
            .orElseThrow(() -> notFound("Workflow not found"));
        int nextVersion = workflowVersionRepository
            .findByWorkflowIdAndTenantIdAndDeletedFalseOrderByVersionNumberDesc(workflowId, tenantId)
            .stream().findFirst().map(version -> version.getVersionNumber() + 1).orElse(1);

        return workflowVersionRepository.save(WorkflowVersion.builder()
            .tenantId(tenantId)
            .workflow(workflow)
            .versionNumber(nextVersion)
            .status(WorkflowVersionStatus.DRAFT)
            .triggerEntityType(request.getTriggerEntityType().trim().toUpperCase())
            .triggerEventType(request.getTriggerEventType().trim().toUpperCase())
            .build()).getId();
    }

    public UUID addNode(UUID tenantId, UUID versionId, WorkflowNodeRequest request) {
        WorkflowVersion version = requireDraftVersion(tenantId, versionId);
        if (workflowNodeRepository.findByTenantIdAndWorkflowVersionIdAndNodeKeyAndDeletedFalse(tenantId, versionId, request.getNodeKey()).isPresent()) {
            throw new BusinessException("WORKFLOW_DUPLICATE_NODE_KEY", "Node key already exists in this workflow version");
        }
        return workflowNodeRepository.save(WorkflowNode.builder()
            .tenantId(tenantId)
            .workflowVersion(version)
            .nodeKey(request.getNodeKey().trim())
            .nodeType(request.getNodeType())
            .name(request.getName().trim())
            .configuration(request.getConfiguration())
            .build()).getId();
    }

    public void updateNode(UUID tenantId, UUID versionId, UUID nodeId, WorkflowNodeRequest request) {
        requireDraftVersion(tenantId, versionId);
        WorkflowNode node = workflowNodeRepository.findByIdAndTenantIdAndWorkflowVersionIdAndDeletedFalse(nodeId, tenantId, versionId)
            .orElseThrow(() -> notFound("Workflow node not found"));
        workflowNodeRepository.findByTenantIdAndWorkflowVersionIdAndNodeKeyAndDeletedFalse(tenantId, versionId, request.getNodeKey())
            .filter(existing -> !existing.getId().equals(nodeId))
            .ifPresent(existing -> { throw new BusinessException("WORKFLOW_DUPLICATE_NODE_KEY", "Node key already exists in this workflow version"); });
        node.setNodeKey(request.getNodeKey().trim());
        node.setNodeType(request.getNodeType());
        node.setName(request.getName().trim());
        node.setConfiguration(request.getConfiguration());
    }

    public void deleteNode(UUID tenantId, UUID versionId, UUID nodeId, UUID userId) {
        requireDraftVersion(tenantId, versionId);
        WorkflowNode node = workflowNodeRepository.findByIdAndTenantIdAndWorkflowVersionIdAndDeletedFalse(nodeId, tenantId, versionId)
            .orElseThrow(() -> notFound("Workflow node not found"));
        node.softDelete(userId);
    }

    public UUID addEdge(UUID tenantId, UUID versionId, WorkflowEdgeRequest request) {
        WorkflowVersion version = requireDraftVersion(tenantId, versionId);
        WorkflowNode source = workflowNodeRepository.findByIdAndTenantIdAndWorkflowVersionIdAndDeletedFalse(request.getSourceNodeId(), tenantId, versionId)
            .orElseThrow(() -> new BusinessException("WORKFLOW_CROSS_VERSION_EDGE", "Source node is not in this workflow version"));
        WorkflowNode target = workflowNodeRepository.findByIdAndTenantIdAndWorkflowVersionIdAndDeletedFalse(request.getTargetNodeId(), tenantId, versionId)
            .orElseThrow(() -> new BusinessException("WORKFLOW_CROSS_VERSION_EDGE", "Target node is not in this workflow version"));
        if (source.getNodeType() == WorkflowNodeType.END) {
            throw new BusinessException("WORKFLOW_END_OUTGOING_EDGE", "END nodes cannot have outgoing edges");
        }
        return workflowEdgeRepository.save(WorkflowEdge.builder()
            .tenantId(tenantId)
            .workflowVersion(version)
            .sourceNode(source)
            .targetNode(target)
            .edgeKey(request.getEdgeKey())
            .configuration(request.getConfiguration())
            .build()).getId();
    }

    public void updateEdge(UUID tenantId, UUID versionId, UUID edgeId, WorkflowEdgeRequest request) {
        requireDraftVersion(tenantId, versionId);
        WorkflowEdge edge = workflowEdgeRepository.findByIdAndTenantIdAndWorkflowVersionIdAndDeletedFalse(edgeId, tenantId, versionId)
            .orElseThrow(() -> notFound("Workflow edge not found"));
        WorkflowNode source = workflowNodeRepository.findByIdAndTenantIdAndWorkflowVersionIdAndDeletedFalse(request.getSourceNodeId(), tenantId, versionId)
            .orElseThrow(() -> new BusinessException("WORKFLOW_CROSS_VERSION_EDGE", "Source node is not in this workflow version"));
        WorkflowNode target = workflowNodeRepository.findByIdAndTenantIdAndWorkflowVersionIdAndDeletedFalse(request.getTargetNodeId(), tenantId, versionId)
            .orElseThrow(() -> new BusinessException("WORKFLOW_CROSS_VERSION_EDGE", "Target node is not in this workflow version"));
        if (source.getNodeType() == WorkflowNodeType.END) {
            throw new BusinessException("WORKFLOW_END_OUTGOING_EDGE", "END nodes cannot have outgoing edges");
        }
        edge.setSourceNode(source);
        edge.setTargetNode(target);
        edge.setEdgeKey(request.getEdgeKey());
        edge.setConfiguration(request.getConfiguration());
    }

    public void deleteEdge(UUID tenantId, UUID versionId, UUID edgeId, UUID userId) {
        requireDraftVersion(tenantId, versionId);
        WorkflowEdge edge = workflowEdgeRepository.findByIdAndTenantIdAndWorkflowVersionIdAndDeletedFalse(edgeId, tenantId, versionId)
            .orElseThrow(() -> notFound("Workflow edge not found"));
        edge.softDelete(userId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowGraphValidationError> validate(UUID tenantId, UUID versionId) {
        requireVersion(tenantId, versionId);
        return graphValidationService.validate(tenantId, versionId);
    }

    public void activate(UUID tenantId, UUID versionId) {
        WorkflowVersion version = requireDraftVersion(tenantId, versionId);
        List<WorkflowGraphValidationError> errors = graphValidationService.validate(tenantId, versionId);
        if (!errors.isEmpty()) {
            throw new BusinessException("WORKFLOW_INVALID_GRAPH", errors.stream().map(error -> error.code() + ": " + error.message()).reduce((left, right) -> left + "; " + right).orElse("Workflow graph is invalid"));
        }
        workflowVersionRepository
            .findByWorkflowIdAndTenantIdAndDeletedFalseOrderByVersionNumberDesc(version.getWorkflow().getId(), tenantId)
            .stream()
            .filter(existing -> existing.getStatus() == WorkflowVersionStatus.ACTIVE)
            .forEach(existing -> existing.setStatus(WorkflowVersionStatus.ARCHIVED));
        workflowVersionRepository.flush();
        version.setStatus(WorkflowVersionStatus.ACTIVE);
        version.getWorkflow().setStatus(WorkflowStatus.ACTIVE);
    }

    public void deactivate(UUID tenantId, UUID workflowId) {
        Workflow workflow = workflowRepository.findByIdAndTenantIdAndDeletedFalse(workflowId, tenantId)
            .orElseThrow(() -> notFound("Workflow not found"));
        if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
            throw new BusinessException("WORKFLOW_NOT_ACTIVE", "Only ACTIVE workflows can be deactivated");
        }
        workflow.setStatus(WorkflowStatus.INACTIVE);
    }

    private Workflow requireWorkflow(UUID workflowId, UUID tenantId) {
        return workflowRepository.findByIdAndTenantIdAndDeletedFalse(workflowId, tenantId)
            .orElseThrow(() -> notFound("Workflow not found"));
    }

    private WorkflowVersion requireDraftVersion(UUID tenantId, UUID versionId) {
        WorkflowVersion version = requireVersion(tenantId, versionId);
        if (version.getStatus() != WorkflowVersionStatus.DRAFT) {
            throw new BusinessException("WORKFLOW_VERSION_IMMUTABLE", "Only DRAFT workflow versions can be modified");
        }
        return version;
    }

    private WorkflowVersion requireVersion(UUID tenantId, UUID versionId) {
        WorkflowVersion version = workflowVersionRepository.findByIdAndTenantIdAndDeletedFalse(versionId, tenantId)
            .orElseThrow(() -> notFound("Workflow version not found"));
        if (version.getWorkflow() == null || !tenantId.equals(version.getWorkflow().getTenantId())) {
            throw new BusinessException("WORKFLOW_CROSS_TENANT_REFERENCE", "Workflow version does not belong to this tenant");
        }
        return version;
    }

    private BusinessException notFound(String message) {
        return new BusinessException("NOT_FOUND", message);
    }
}