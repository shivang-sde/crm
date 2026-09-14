package com.shivang.crm.modules.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.acquisition.config.LeadIngestionConfig;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.lead.entity.LeadSource;
import com.shivang.crm.modules.lead.repository.LeadSourceRepository;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.modules.workflow.entity.Workflow;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;
import com.shivang.crm.modules.workflow.entity.WorkflowVersion;
import com.shivang.crm.modules.workflow.entity.WorkflowVersionStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowEdgeRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowVersionRepository;
import com.shivang.crm.shared.base.BaseEntity;

public class WorkflowTriggerFilterValidationTest {

    private WorkflowVersionRepository versionRepo;
    private WorkflowNodeRepository nodeRepo;
    private WorkflowEdgeRepository edgeRepo;
    private LeadIngestionConfigRepository ingestionRepo;
    private LeadSourceRepository sourceRepo;
    private RecordTypeRepository recordTypeRepo;
    private WorkflowGraphValidationService service;

    private UUID tenantA;
    private UUID tenantB;
    private UUID configA;
    private UUID configB;
    private UUID sourceA;
    private UUID sourceB;
    private Workflow workflow;
    private WorkflowVersion version;
    private WorkflowNode trigger;
    private WorkflowNode end;

    @BeforeEach
    void setup() {
        versionRepo = mock(WorkflowVersionRepository.class);
        nodeRepo = mock(WorkflowNodeRepository.class);
        edgeRepo = mock(WorkflowEdgeRepository.class);
        ingestionRepo = mock(LeadIngestionConfigRepository.class);
        sourceRepo = mock(LeadSourceRepository.class);
        recordTypeRepo = mock(RecordTypeRepository.class);
        service = new WorkflowGraphValidationService(versionRepo, nodeRepo, edgeRepo, ingestionRepo, sourceRepo, recordTypeRepo);

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        configA = UUID.randomUUID();
        configB = UUID.randomUUID();
        sourceA = UUID.randomUUID();
        sourceB = UUID.randomUUID();

        workflow = Workflow.builder().id(UUID.randomUUID()).tenantId(tenantA).build();
        // Use BaseEntity to set id for workflow
        setId(workflow, workflow.getId());

        version = WorkflowVersion.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantA)
                .workflow(workflow)
                .versionNumber(1)
                .status(WorkflowVersionStatus.DRAFT)
                .triggerEntityType("LEAD")
                .triggerEventType("CREATED")
                .build();
        setId(version, version.getId());

        trigger = WorkflowNode.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantA)
                .workflowVersion(version)
                .nodeKey("trigger")
                .nodeType(WorkflowNodeType.TRIGGER)
                .name("Trigger")
                .configuration(Map.of("entityType", "LEAD", "eventType", "CREATED"))
                .build();
        setId(trigger, trigger.getId());

        end = WorkflowNode.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantA)
                .workflowVersion(version)
                .nodeKey("end")
                .nodeType(WorkflowNodeType.END)
                .name("End")
                .configuration(Map.of())
                .build();
        setId(end, end.getId());

        // Common stubs
        when(versionRepo.findByIdAndTenantIdAndDeletedFalse(version.getId(), tenantA)).thenReturn(Optional.of(version));
        when(nodeRepo.findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantA, version.getId())).thenReturn(List.of(trigger, end));
        when(edgeRepo.findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantA, version.getId())).thenReturn(List.of(
                com.shivang.crm.modules.workflow.entity.WorkflowEdge.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantA)
                        .workflowVersion(version)
                        .sourceNode(trigger)
                        .targetNode(end)
                        .build()
        ));
        // Mock ingestion configs
        LeadIngestionConfig cfgA = LeadIngestionConfig.builder().id(configA).tenantId(tenantA).build();
        setId(cfgA, configA);
        when(ingestionRepo.findByIdAndTenantIdAndDeletedFalse(configA, tenantA)).thenReturn(Optional.of(cfgA));
        when(ingestionRepo.findByIdAndTenantIdAndDeletedFalse(configB, tenantA)).thenReturn(Optional.empty());
        when(ingestionRepo.findByIdAndTenantIdAndDeletedFalse(configB, tenantB)).thenReturn(Optional.of(LeadIngestionConfig.builder().id(configB).tenantId(tenantB).build()));
        // Lead sources
        LeadSource srcA = LeadSource.builder().id(sourceA).tenantId(tenantA).name("Facebook").build();
        setId(srcA, sourceA);
        LeadSource srcB = LeadSource.builder().id(sourceB).tenantId(tenantB).name("Facebook B").build();
        setId(srcB, sourceB);
        when(sourceRepo.findByIdAndTenantId(sourceA, tenantA)).thenReturn(Optional.of(srcA));
        when(sourceRepo.findByIdAndTenantId(sourceB, tenantA)).thenReturn(Optional.empty());
        when(sourceRepo.findByIdAndTenantId(sourceB, tenantB)).thenReturn(Optional.of(srcB));
        when(sourceRepo.findByIdAndTenantId(sourceA, tenantB)).thenReturn(Optional.empty());
    }

    private void setId(BaseEntity entity, UUID id) {
        try {
            var f = BaseEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setTriggerFilter(Map<String, Object> filter) {
        version.setTriggerFilter(filter);
        // Also sync to node for consistency
        var cfg = new java.util.HashMap<>(trigger.getConfiguration());
        if (filter == null) cfg.remove("triggerFilter");
        else cfg.put("triggerFilter", filter);
        trigger.setConfiguration(cfg);
    }

    private boolean hasErrorWithCode(String code) {
        var errors = service.validate(tenantA, version.getId());
        return errors.stream().anyMatch(e -> code.equals(e.code()));
    }

    private boolean hasTriggerFilterError() {
        return hasErrorWithCode("WORKFLOW_TRIGGER_FILTER_INVALID");
    }

    // A. null/empty
    @Test
    void validNullFilter() {
        setTriggerFilter(null);
        assertThat(hasTriggerFilterError()).isFalse();
    }

    @Test
    void validEmptyFilter() {
        setTriggerFilter(Map.of());
        assertThat(hasTriggerFilterError()).isFalse();
    }

    // B. valid Created Via
    @Test
    void validCreatedViaManual() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", "MANUAL"))));
        assertThat(hasTriggerFilterError()).isFalse();
    }

    @Test
    void validCreatedViaLeadIngestion() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", "LEAD_INGESTION"))));
        assertThat(hasTriggerFilterError()).isFalse();
    }

    @Test
    void validCreatedViaImport() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", "IMPORT"))));
        assertThat(hasTriggerFilterError()).isFalse();
    }

    // C. invalid Created Via
    @Test
    void invalidCreatedViaFacebook() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", "FACEBOOK"))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    @Test
    void invalidCreatedViaUniversal() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", "UNIVERSAL_LEAD_INGESTION"))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    @Test
    void invalidCreatedViaBlank() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", ""))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    // D. valid ingestion config
    @Test
    void validIngestionConfig() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.ingestionConfigId", "operator", "EQUALS", "value", configA.toString()))));
        assertThat(hasTriggerFilterError()).isFalse();
    }

    // E. malformed ingestion config
    @Test
    void malformedIngestionConfig() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.ingestionConfigId", "operator", "EQUALS", "value", "not-a-uuid"))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    // F. cross-tenant ingestion
    @Test
    void crossTenantIngestionConfig() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.ingestionConfigId", "operator", "EQUALS", "value", configB.toString()))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    // G. deleted ingestion config — we simulate deleted by returning empty (as repo does for deletedFalse)
    @Test
    void deletedIngestionConfig() {
        UUID deletedId = UUID.randomUUID();
        when(ingestionRepo.findByIdAndTenantIdAndDeletedFalse(deletedId, tenantA)).thenReturn(Optional.empty());
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.ingestionConfigId", "operator", "EQUALS", "value", deletedId.toString()))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    // H. valid lead source
    @Test
    void validLeadSource() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "entity.sourceId", "operator", "EQUALS", "value", sourceA.toString()))));
        assertThat(hasTriggerFilterError()).isFalse();
    }

    // I. cross-tenant lead source
    @Test
    void crossTenantLeadSource() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "entity.sourceId", "operator", "EQUALS", "value", sourceB.toString()))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    // J. malformed sourceId
    @Test
    void malformedSourceId() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "entity.sourceId", "operator", "EQUALS", "value", "not-a-uuid"))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    // K. invalid field
    @Test
    void invalidField() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.thisDoesNotExist", "operator", "EQUALS", "value", "foo"))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    // L. invalid operator
    @Test
    void invalidOperator() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "operator", "FOOBAR", "value", "MANUAL"))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    // M. invalid logic
    @Test
    void invalidLogic() {
        setTriggerFilter(Map.of("logic", "XOR", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", "MANUAL"))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    // N. malformed conditions
    @Test
    void malformedConditionsNull() {
        setTriggerFilter(new java.util.HashMap<String, Object>() {{ put("logic", "AND"); put("conditions", null); }});
        assertThat(hasTriggerFilterError()).isTrue();
    }

    @Test
    void malformedConditionMissingField() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("operator", "EQUALS", "value", "MANUAL"))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    @Test
    void malformedConditionMissingOperator() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "value", "MANUAL"))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    @Test
    void malformedConditionMissingValue() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS"))));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    // O. valid combined
    @Test
    void validCombinedFilter() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(
                Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", "LEAD_INGESTION"),
                Map.of("field", "trigger.metadata.ingestionConfigId", "operator", "EQUALS", "value", configA.toString()),
                Map.of("field", "entity.sourceId", "operator", "EQUALS", "value", sourceA.toString())
        )));
        assertThat(hasTriggerFilterError()).isFalse();
    }

    // P. invalid combined (cross-tenant config)
    @Test
    void invalidCombinedFilterCrossTenant() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(
                Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", "LEAD_INGESTION"),
                Map.of("field", "trigger.metadata.ingestionConfigId", "operator", "EQUALS", "value", configB.toString()),
                Map.of("field", "entity.sourceId", "operator", "EQUALS", "value", sourceA.toString())
        )));
        assertThat(hasTriggerFilterError()).isTrue();
    }

    // Additional: IN operator
    @Test
    void validCreatedViaInOperator() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "operator", "IN", "value", List.of("MANUAL", "LEAD_INGESTION")))));
        assertThat(hasTriggerFilterError()).isFalse();
    }

    @Test
    void invalidCreatedViaInWithBadValue() {
        setTriggerFilter(Map.of("logic", "AND", "conditions", List.of(Map.of("field", "trigger.metadata.createdVia", "operator", "IN", "value", List.of("MANUAL", "FACEBOOK")))));
        assertThat(hasTriggerFilterError()).isTrue();
    }
}
