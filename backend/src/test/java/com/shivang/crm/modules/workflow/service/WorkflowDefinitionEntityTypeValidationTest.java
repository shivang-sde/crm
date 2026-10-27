package com.shivang.crm.modules.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.workflow.entity.Workflow;
import com.shivang.crm.modules.workflow.entity.WorkflowVersion;
import com.shivang.crm.modules.workflow.entity.WorkflowVersionStatus;
import com.shivang.crm.modules.workflow.dto.WorkflowVersionCreateRequest;
import com.shivang.crm.modules.workflow.dto.WorkflowVersionUpdateRequest;
import com.shivang.crm.modules.workflow.repository.WorkflowEdgeRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowVersionRepository;
import com.shivang.crm.shared.exception.BusinessException;

class WorkflowDefinitionEntityTypeValidationTest {

    private WorkflowRepository workflowRepo;
    private WorkflowVersionRepository versionRepo;
    private WorkflowNodeRepository nodeRepo;
    private WorkflowEdgeRepository edgeRepo;
    private WorkflowGraphValidationService graphValidationService;
    private WorkflowEntityContextProviderRegistry contextRegistry;
    private WorkflowDefinitionService service;

    private UUID tenantId;
    private UUID workflowId;
    private Workflow workflow;

    @BeforeEach
    void setup() {
        workflowRepo = mock(WorkflowRepository.class);
        versionRepo = mock(WorkflowVersionRepository.class);
        nodeRepo = mock(WorkflowNodeRepository.class);
        edgeRepo = mock(WorkflowEdgeRepository.class);
        graphValidationService = mock(WorkflowGraphValidationService.class);
        contextRegistry = registryWith("LEAD", "DEAL");

        service = new WorkflowDefinitionService(
                workflowRepo, versionRepo, nodeRepo, edgeRepo, graphValidationService, contextRegistry);

        tenantId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
        workflow = Workflow.builder().tenantId(tenantId).name("wf").build();
        workflow.setId(workflowId);

        when(workflowRepo.findByIdAndTenantIdAndDeletedFalse(workflowId, tenantId))
                .thenReturn(Optional.of(workflow));
        when(versionRepo.findByWorkflowIdAndTenantIdAndDeletedFalseOrderByVersionNumberDesc(workflowId, tenantId))
                .thenReturn(List.of());
        when(versionRepo.save(any(WorkflowVersion.class)))
                .thenAnswer(invocation -> {
                    WorkflowVersion saved = invocation.getArgument(0);
                    // Simulate the DB-generated identifier.
                    saved.setId(UUID.randomUUID());
                    return saved;
                });
        when(nodeRepo.findByTenantIdAndWorkflowVersionIdAndDeletedFalse(any(), any()))
                .thenReturn(List.of());
    }

    // --- createDraftVersion ---

    @Test
    void createDraftVersionAcceptsRegisteredEntityType() {
        UUID id = service.createDraftVersion(tenantId, workflowId, createRequest("DEAL", "CREATED"));
        assertThat(id).isNotNull();
    }

    @Test
    void createDraftVersionAcceptsEntityTypeInAnyCase() {
        UUID id = service.createDraftVersion(tenantId, workflowId, createRequest("  deal  ", "CREATED"));
        assertThat(id).isNotNull();
    }

    @Test
    void createDraftVersionRejectsUnknownEntityType() {
        WorkflowVersionCreateRequest request = createRequest("TICKETS", "CREATED");

        assertThatThrownBy(() -> service.createDraftVersion(tenantId, workflowId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TICKETS");
    }

    @Test
    void createDraftVersionRejectsBlankEntityType() {
        WorkflowVersionCreateRequest request = createRequest("  ", "CREATED");

        assertThatThrownBy(() -> service.createDraftVersion(tenantId, workflowId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createDraftVersionRejectsNullEntityType() {
        WorkflowVersionCreateRequest request = createRequest(null, "CREATED");

        assertThatThrownBy(() -> service.createDraftVersion(tenantId, workflowId, request))
                .isInstanceOf(BusinessException.class);
    }

    // --- updateDraftVersionTrigger ---

    @Test
    void updateDraftTriggerAcceptsRegisteredEntityType() {
        WorkflowVersion draft = draftVersion();

        service.updateDraftVersionTrigger(tenantId, draft.getId(), updateRequest("lead", "CREATED"));

        assertThat(draft.getTriggerEntityType()).isEqualTo("LEAD");
    }

    @Test
    void updateDraftTriggerRejectsUnknownEntityType() {
        WorkflowVersion draft = draftVersion();
        WorkflowVersionUpdateRequest request = updateRequest("CUSTOMER_HEALTH", "CHANGED");

        assertThatThrownBy(() -> service.updateDraftVersionTrigger(tenantId, draft.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CUSTOMER_HEALTH");

        // Version must be left untouched
        assertThat(draft.getTriggerEntityType()).isEqualTo("LEAD");
    }

    @Test
    void updateDraftTriggerRejectsNullEntityType() {
        WorkflowVersion draft = draftVersion();
        WorkflowVersionUpdateRequest request = updateRequest(null, "CREATED");

        assertThatThrownBy(() -> service.updateDraftVersionTrigger(tenantId, draft.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void supportedEntityTypesComeFromContextProviderRegistry() {
        assertThat(contextRegistry.supportedEntityTypes()).containsExactlyInAnyOrder("LEAD", "DEAL");
    }

    // --- helpers ---

    private WorkflowVersion draftVersion() {
        WorkflowVersion version = WorkflowVersion.builder()
                .tenantId(tenantId)
                .workflow(workflow)
                .versionNumber(1)
                .status(WorkflowVersionStatus.DRAFT)
                .triggerEntityType("LEAD")
                .triggerEventType("CREATED")
                .build();
        version.setId(UUID.randomUUID());
        when(versionRepo.findByIdAndTenantIdAndDeletedFalse(version.getId(), tenantId))
                .thenReturn(Optional.of(version));
        return version;
    }

    private WorkflowVersionCreateRequest createRequest(String entityType, String eventType) {
        WorkflowVersionCreateRequest request = new WorkflowVersionCreateRequest();
        request.setTriggerEntityType(entityType);
        request.setTriggerEventType(eventType);
        return request;
    }

    private WorkflowVersionUpdateRequest updateRequest(String entityType, String eventType) {
        WorkflowVersionUpdateRequest request = new WorkflowVersionUpdateRequest();
        request.setTriggerEntityType(entityType);
        request.setTriggerEventType(eventType);
        return request;
    }

    private WorkflowEntityContextProviderRegistry registryWith(String... entityTypes) {
        return new WorkflowEntityContextProviderRegistry(List.of(entityTypes).stream()
                .map(type -> {
                    WorkflowEntityContextProvider provider = mock(WorkflowEntityContextProvider.class);
                    when(provider.entityType()).thenReturn(type);
                    when(provider.load(any(), any())).thenReturn(Optional.empty());
                    return provider;
                })
                .toList());
    }
}