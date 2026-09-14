package com.shivang.crm.modules.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.records.entity.RecordType;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.modules.workflow.entity.Workflow;
import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowNode;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;
import com.shivang.crm.modules.workflow.entity.WorkflowStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowVersion;
import com.shivang.crm.modules.workflow.entity.WorkflowVersionStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowEdgeRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowVersionRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.lead.repository.LeadSourceRepository;
import com.shivang.crm.shared.base.BaseEntity;
import com.shivang.crm.shared.event.CanonicalCrmEvent;

import tools.jackson.databind.ObjectMapper;

/**
 * WF-53 FINAL ACCEPTANCE matrix A-J for RECORD.RECEIVED
 * Covers trigger filter matching, tenant isolation, duplicate protection,
 * execution identity, and validation.
 */
public class WorkflowRecordReceivedTriggerAcceptanceTest {

    private WorkflowVersionRepository versionRepo;
    private WorkflowExecutionRepository executionRepo;
    private WorkflowNodeRepository nodeRepo;
    private WorkflowEdgeRepository edgeRepo;
    private LeadIngestionConfigRepository ingestionRepo;
    private LeadSourceRepository sourceRepo;
    private RecordTypeRepository recordTypeRepo;
    private ObjectMapper objectMapper;
    private WorkflowConditionEvaluator evaluator;
    private WorkflowEntityContextProviderRegistry registry;
    private WorkflowGraphValidationService validationService;
    private WorkflowTriggerService triggerService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID recordTypeX;
    private UUID recordTypeY;
    private UUID webhookId;
    private UUID deliveryId;
    private UUID recordId;

    @BeforeEach
    void setup() {
        versionRepo = mock(WorkflowVersionRepository.class);
        executionRepo = mock(WorkflowExecutionRepository.class);
        nodeRepo = mock(WorkflowNodeRepository.class);
        edgeRepo = mock(WorkflowEdgeRepository.class);
        ingestionRepo = mock(LeadIngestionConfigRepository.class);
        sourceRepo = mock(LeadSourceRepository.class);
        recordTypeRepo = mock(RecordTypeRepository.class);
        objectMapper = new ObjectMapper();
        evaluator = new WorkflowConditionEvaluator(new ContextWorkflowValueResolver());
        registry = mock(WorkflowEntityContextProviderRegistry.class);
        when(registry.load(any(), any(), any())).thenReturn(Optional.of(Map.of()));

        triggerService = new WorkflowTriggerService(versionRepo, executionRepo, objectMapper, evaluator, registry);
        validationService = new WorkflowGraphValidationService(versionRepo, nodeRepo, edgeRepo, ingestionRepo, sourceRepo, recordTypeRepo);

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        recordTypeX = UUID.randomUUID();
        recordTypeY = UUID.randomUUID();
        webhookId = UUID.randomUUID();
        deliveryId = UUID.randomUUID();
        recordId = UUID.randomUUID();

        // recordTypeRepo stubs for validation
        RecordType rtX = RecordType.builder().id(recordTypeX).tenantId(tenantA).key("cdr").name("CDR").isActive(true).build();
        setId(rtX, recordTypeX);
        RecordType rtY = RecordType.builder().id(recordTypeY).tenantId(tenantA).key("sellspark").name("Sellspark").isActive(true).build();
        setId(rtY, recordTypeY);
        RecordType rtB = RecordType.builder().id(UUID.randomUUID()).tenantId(tenantB).key("other").name("Other").isActive(true).build();
        // tenant A can find X/Y, tenant B cannot find X
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(recordTypeX, tenantA)).thenReturn(Optional.of(rtX));
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(recordTypeY, tenantA)).thenReturn(Optional.of(rtY));
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(recordTypeX, tenantB)).thenReturn(Optional.empty());
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(recordTypeY, tenantB)).thenReturn(Optional.empty());
        // inactive / deleted variants handled per-test
    }

    private void setId(BaseEntity e, UUID id) {
        try { var f = BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(e, id); } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private WorkflowVersion buildVersion(UUID tenant, UUID workflowId, UUID versionId, Map<String,Object> filter) {
        Workflow wf = Workflow.builder().id(workflowId).tenantId(tenant).status(WorkflowStatus.ACTIVE).build();
        setId(wf, workflowId);
        WorkflowVersion v = WorkflowVersion.builder()
            .id(versionId).tenantId(tenant).workflow(wf).versionNumber(1)
            .status(WorkflowVersionStatus.ACTIVE)
            .triggerEntityType("RECORD").triggerEventType("RECEIVED")
            .triggerFilter(filter)
            .build();
        setId(v, versionId);
        return v;
    }

    private CanonicalCrmEvent buildRecordEvent(UUID tenant, UUID entityId, UUID recordTypeId) {
        Map<String,Object> meta = Map.of("recordTypeId", recordTypeId.toString(), "webhookId", webhookId.toString(), "deliveryId", deliveryId.toString());
        return CanonicalCrmEvent.forEntity(CanonicalCrmEvent.RECORD_ENTITY_TYPE, CanonicalCrmEvent.RECEIVED_EVENT_TYPE, tenant, entityId, meta);
    }

    // A. Matching Record Type
    @Test
    void A_matchingRecordType_createsExecution() {
        UUID wfId = UUID.randomUUID(); UUID verId = UUID.randomUUID();
        Map<String,Object> filter = Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value",recordTypeX.toString())));
        WorkflowVersion ver = buildVersion(tenantA, wfId, verId, filter);
        when(versionRepo.findActiveMatches(eq(tenantA), eq("RECORD"), eq("RECEIVED"), eq(WorkflowStatus.ACTIVE), eq(WorkflowVersionStatus.ACTIVE))).thenReturn(List.of(ver));
        CanonicalCrmEvent event = buildRecordEvent(tenantA, recordId, recordTypeX);
        triggerService.process(event);
        verify(executionRepo, times(1)).insertIfAbsent(eq(tenantA), eq(wfId), eq(verId), eq(event.eventId()), eq("RECORD"), eq(recordId), eq("RECEIVED"), isNull(), isNull(), eq(WorkflowExecutionStatus.PENDING.name()), anyString(), isNull(), eq(event.eventId()), eq(0));
    }

    // B. Different Record Type Must Not Match
    @Test
    void B_differentRecordType_noExecution() {
        UUID wfId = UUID.randomUUID(); UUID verId = UUID.randomUUID();
        Map<String,Object> filter = Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value",recordTypeX.toString())));
        WorkflowVersion ver = buildVersion(tenantA, wfId, verId, filter);
        when(versionRepo.findActiveMatches(eq(tenantA), eq("RECORD"), eq("RECEIVED"), any(), any())).thenReturn(List.of(ver));
        CanonicalCrmEvent event = buildRecordEvent(tenantA, recordId, recordTypeY);
        triggerService.process(event);
        verify(executionRepo, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // C. No Trigger Filter
    @Test
    void C_nullFilter_matchesAllRecordTypes() {
        UUID wfId = UUID.randomUUID(); UUID verId = UUID.randomUUID();
        WorkflowVersion ver = buildVersion(tenantA, wfId, verId, null);
        when(versionRepo.findActiveMatches(eq(tenantA), eq("RECORD"), eq("RECEIVED"), any(), any())).thenReturn(List.of(ver));
        CanonicalCrmEvent eventX = buildRecordEvent(tenantA, UUID.randomUUID(), recordTypeX);
        CanonicalCrmEvent eventY = buildRecordEvent(tenantA, UUID.randomUUID(), recordTypeY);
        triggerService.process(eventX);
        triggerService.process(eventY);
        verify(executionRepo, times(2)).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void C_emptyFilter_matchesAll() {
        UUID wfId = UUID.randomUUID(); UUID verId = UUID.randomUUID();
        WorkflowVersion ver = buildVersion(tenantA, wfId, verId, Map.of());
        when(versionRepo.findActiveMatches(eq(tenantA), eq("RECORD"), eq("RECEIVED"), any(), any())).thenReturn(List.of(ver));
        CanonicalCrmEvent event = buildRecordEvent(tenantA, recordId, recordTypeY);
        triggerService.process(event);
        verify(executionRepo, times(1)).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // D. Duplicate Event
    @Test
    void D_duplicateEvent_insertIfAbsentCalledTwiceButProtectedByConstraint() {
        UUID wfId = UUID.randomUUID(); UUID verId = UUID.randomUUID();
        Map<String,Object> filter = Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value",recordTypeX.toString())));
        WorkflowVersion ver = buildVersion(tenantA, wfId, verId, filter);
        when(versionRepo.findActiveMatches(any(), any(), any(), any(), any())).thenReturn(List.of(ver));
        // Simulate DB constraint: first insert returns 1, second returns 0 (ON CONFLICT DO NOTHING)
        when(executionRepo.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1).thenReturn(0);
        CanonicalCrmEvent event = buildRecordEvent(tenantA, recordId, recordTypeX);
        triggerService.process(event);
        triggerService.process(event);
        verify(executionRepo, times(2)).insertIfAbsent(eq(tenantA), eq(wfId), eq(verId), eq(event.eventId()), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // E. Tenant Isolation - event tenant scope + RecordType ownership validation
    @Test
    void E_tenantIsolation_eventTenantDifferentFromWorkflowTenant_noMatch() {
        UUID wfIdB = UUID.randomUUID(); UUID verIdB = UUID.randomUUID();
        Map<String,Object> filter = Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value",recordTypeX.toString())));
        WorkflowVersion verB = buildVersion(tenantB, wfIdB, verIdB, filter);
        // Tenant A event should only query tenant A active matches; simulate that
        when(versionRepo.findActiveMatches(eq(tenantA), eq("RECORD"), eq("RECEIVED"), any(), any())).thenReturn(List.of());
        when(versionRepo.findActiveMatches(eq(tenantB), eq("RECORD"), eq("RECEIVED"), any(), any())).thenReturn(List.of(verB));
        CanonicalCrmEvent eventA = buildRecordEvent(tenantA, recordId, recordTypeX);
        triggerService.process(eventA);
        verify(executionRepo, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void E_crossTenantRecordType_validationFails() {
        // Attempt to publish workflow in tenantA referencing recordType that belongs to tenantB
        UUID crossId = UUID.randomUUID();
        RecordType rtCross = RecordType.builder().id(crossId).tenantId(tenantB).key("other").name("Other").isActive(true).build();
        setId(rtCross, crossId);
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(crossId, tenantA)).thenReturn(Optional.empty());
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(crossId, tenantB)).thenReturn(Optional.of(rtCross));

        UUID wfId = UUID.randomUUID(); UUID verId = UUID.randomUUID();
        Workflow wf = Workflow.builder().id(wfId).tenantId(tenantA).status(WorkflowStatus.ACTIVE).build(); setId(wf, wfId);
        WorkflowVersion ver = WorkflowVersion.builder().id(verId).tenantId(tenantA).workflow(wf).versionNumber(1).status(WorkflowVersionStatus.DRAFT).triggerEntityType("RECORD").triggerEventType("RECEIVED").triggerFilter(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value",crossId.toString())))).build(); setId(ver, verId);
        WorkflowNode trigger = WorkflowNode.builder().id(UUID.randomUUID()).tenantId(tenantA).workflowVersion(ver).nodeKey("trigger").nodeType(WorkflowNodeType.TRIGGER).name("Trigger").configuration(Map.of("entityType","RECORD","eventType","RECEIVED","triggerFilter", ver.getTriggerFilter())).build(); setId(trigger, trigger.getId());
        WorkflowNode end = WorkflowNode.builder().id(UUID.randomUUID()).tenantId(tenantA).workflowVersion(ver).nodeKey("end").nodeType(WorkflowNodeType.END).name("End").configuration(Map.of()).build(); setId(end, end.getId());
        when(versionRepo.findByIdAndTenantIdAndDeletedFalse(verId, tenantA)).thenReturn(Optional.of(ver));
        when(nodeRepo.findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantA, verId)).thenReturn(List.of(trigger, end));
        when(edgeRepo.findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantA, verId)).thenReturn(List.of(com.shivang.crm.modules.workflow.entity.WorkflowEdge.builder().id(UUID.randomUUID()).tenantId(tenantA).workflowVersion(ver).sourceNode(trigger).targetNode(end).build()));
        var errors = validationService.validate(tenantA, verId);
        assertThat(errors.stream().anyMatch(e -> e.code().equals("WORKFLOW_TRIGGER_FILTER_INVALID"))).isTrue();
    }

    // F. Inactive Workflow
    @Test
    void F_inactiveWorkflow_noExecution() {
        // findActiveMatches only returns ACTIVE/ACTIVE, so inactive returns empty
        when(versionRepo.findActiveMatches(eq(tenantA), eq("RECORD"), eq("RECEIVED"), eq(WorkflowStatus.ACTIVE), eq(WorkflowVersionStatus.ACTIVE))).thenReturn(List.of());
        CanonicalCrmEvent event = buildRecordEvent(tenantA, recordId, recordTypeX);
        triggerService.process(event);
        verify(executionRepo, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // G. No Matching Workflow
    @Test
    void G_noMatchingWorkflow_consumedSafely() {
        when(versionRepo.findActiveMatches(any(), any(), any(), any(), any())).thenReturn(List.of());
        CanonicalCrmEvent event = buildRecordEvent(tenantA, recordId, recordTypeX);
        // Should not throw
        triggerService.process(event);
        verify(executionRepo, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(executionRepo, never()).insertRejectedIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // H. Trigger Filter Activation Validation - invalid UUID, nonexistent, deleted, inactive, IN handling
    @Test
    void H_invalidUUID_rejected() {
        validateFilterExpectInvalid(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value","not-a-uuid"))));
    }

    @Test
    void H_nonexistentRecordType_rejected() {
        UUID fake = UUID.randomUUID();
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(fake, tenantA)).thenReturn(Optional.empty());
        validateFilterExpectInvalid(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value",fake.toString()))));
    }

    @Test
    void H_deletedRecordType_rejected() {
        UUID deletedId = UUID.randomUUID();
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(deletedId, tenantA)).thenReturn(Optional.empty());
        validateFilterExpectInvalid(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value",deletedId.toString()))));
    }

    @Test
    void H_inactiveRecordType_rejected() {
        UUID inactiveId = UUID.randomUUID();
        RecordType inactive = RecordType.builder().id(inactiveId).tenantId(tenantA).key("inactive").name("Inactive").isActive(false).build(); setId(inactive, inactiveId);
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(inactiveId, tenantA)).thenReturn(Optional.of(inactive));
        validateFilterExpectInvalid(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value",inactiveId.toString()))));
    }

    @Test
    void H_validActive_succeeds() {
        // recordTypeX is active valid
        validateFilterExpectValid(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value",recordTypeX.toString()))));
    }

    @Test
    void H_IN_operator_validAndInvalid() {
        // valid IN with two active IDs
        validateFilterExpectValid(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","IN","value", List.of(recordTypeX.toString(), recordTypeY.toString())))));
        // invalid IN with one bad UUID
        UUID fake = UUID.randomUUID();
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(fake, tenantA)).thenReturn(Optional.empty());
        validateFilterExpectInvalid(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","IN","value", List.of(recordTypeX.toString(), fake.toString())))));
        // invalid IN with inactive
        UUID inactiveId = UUID.randomUUID();
        RecordType inactive = RecordType.builder().id(inactiveId).tenantId(tenantA).key("inactive2").name("I2").isActive(false).build(); setId(inactive, inactiveId);
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(inactiveId, tenantA)).thenReturn(Optional.of(inactive));
        validateFilterExpectInvalid(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","NOT_IN","value", List.of(inactiveId.toString())))));
    }

    @Test
    void H_webhookId_deliveryId_UUID_validation() {
        // valid UUIDs pass
        validateFilterExpectValidForRecordWithExtraFields(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.webhookId","operator","EQUALS","value",UUID.randomUUID().toString()))));
        // invalid UUID fails
        validateFilterExpectInvalidForRecordWithExtraFields(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.webhookId","operator","EQUALS","value","bad-uuid"))));
        validateFilterExpectInvalidForRecordWithExtraFields(Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.deliveryId","operator","EQUALS","value","bad-uuid"))));
    }

    // I. RecordType Later Becomes Invalid - validation should reject re-publish
    @Test
    void I_laterInactive_revalidationFails() {
        UUID rtId = UUID.randomUUID();
        RecordType active = RecordType.builder().id(rtId).tenantId(tenantA).key("temp").name("Temp").isActive(true).build(); setId(active, rtId);
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(rtId, tenantA)).thenReturn(Optional.of(active));
        Map<String,Object> filter = Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value",rtId.toString())));
        // Initially valid
        validateFilterExpectValidCustom(filter, Map.of(rtId.toString(), active));
        // Later becomes inactive
        RecordType inactive = RecordType.builder().id(rtId).tenantId(tenantA).key("temp").name("Temp").isActive(false).build(); setId(inactive, rtId);
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(rtId, tenantA)).thenReturn(Optional.of(inactive));
        validateFilterExpectInvalid(filter);
        // Deleted also
        when(recordTypeRepo.findByIdAndTenantIdAndDeletedFalse(rtId, tenantA)).thenReturn(Optional.empty());
        validateFilterExpectInvalid(filter);
    }

    // J. Execution Identity / Historical Context
    @Test
    void J_executionIdentity_contextPreserved() {
        UUID wfId = UUID.randomUUID(); UUID verId = UUID.randomUUID();
        Map<String,Object> filter = Map.of("logic","AND","conditions", List.of(Map.of("field","trigger.metadata.recordTypeId","operator","EQUALS","value",recordTypeX.toString())));
        WorkflowVersion ver = buildVersion(tenantA, wfId, verId, filter);
        when(versionRepo.findActiveMatches(eq(tenantA), eq("RECORD"), eq("RECEIVED"), any(), any())).thenReturn(List.of(ver));
        UUID recId = UUID.randomUUID();
        CanonicalCrmEvent event = buildRecordEvent(tenantA, recId, recordTypeX);
        triggerService.process(event);
        // Capture triggerContext JSON string passed to insertIfAbsent (6th from last?)
        verify(executionRepo).insertIfAbsent(eq(tenantA), eq(wfId), eq(verId), eq(event.eventId()), eq("RECORD"), eq(recId), eq("RECEIVED"), isNull(), isNull(), eq("PENDING"), argThat(ctx -> {
            try {
                // ctx is JSON string of metadata
                return ctx != null && ctx.contains(recordTypeX.toString()) && ctx.contains(webhookId.toString()) && ctx.contains(deliveryId.toString());
            } catch (Exception e) { return false; }
        }), isNull(), eq(event.eventId()), eq(0));
    }

    // helpers for H/I
    private void validateFilterExpectInvalid(Map<String,Object> filter) {
        UUID wfId = UUID.randomUUID(); UUID verId = UUID.randomUUID();
        Workflow wf = Workflow.builder().id(wfId).tenantId(tenantA).status(WorkflowStatus.ACTIVE).build(); setId(wf, wfId);
        WorkflowVersion ver = WorkflowVersion.builder().id(verId).tenantId(tenantA).workflow(wf).versionNumber(1).status(WorkflowVersionStatus.DRAFT).triggerEntityType("RECORD").triggerEventType("RECEIVED").triggerFilter(filter).build(); setId(ver, verId);
        WorkflowNode trigger = WorkflowNode.builder().id(UUID.randomUUID()).tenantId(tenantA).workflowVersion(ver).nodeKey("trigger").nodeType(WorkflowNodeType.TRIGGER).name("Trigger").configuration(Map.of("entityType","RECORD","eventType","RECEIVED","triggerFilter",filter)).build(); setId(trigger, trigger.getId());
        WorkflowNode end = WorkflowNode.builder().id(UUID.randomUUID()).tenantId(tenantA).workflowVersion(ver).nodeKey("end").nodeType(WorkflowNodeType.END).name("End").configuration(Map.of()).build(); setId(end, end.getId());
        when(versionRepo.findByIdAndTenantIdAndDeletedFalse(verId, tenantA)).thenReturn(Optional.of(ver));
        when(nodeRepo.findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantA, verId)).thenReturn(List.of(trigger, end));
        when(edgeRepo.findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantA, verId)).thenReturn(List.of(com.shivang.crm.modules.workflow.entity.WorkflowEdge.builder().id(UUID.randomUUID()).tenantId(tenantA).workflowVersion(ver).sourceNode(trigger).targetNode(end).build()));
        var errors = validationService.validate(tenantA, verId);
        assertThat(errors.stream().anyMatch(e -> e.code().equals("WORKFLOW_TRIGGER_FILTER_INVALID"))).as("expected validation failure for filter " + filter).isTrue();
    }

    private void validateFilterExpectValid(Map<String,Object> filter) {
        UUID wfId = UUID.randomUUID(); UUID verId = UUID.randomUUID();
        Workflow wf = Workflow.builder().id(wfId).tenantId(tenantA).status(WorkflowStatus.ACTIVE).build(); setId(wf, wfId);
        WorkflowVersion ver = WorkflowVersion.builder().id(verId).tenantId(tenantA).workflow(wf).versionNumber(1).status(WorkflowVersionStatus.DRAFT).triggerEntityType("RECORD").triggerEventType("RECEIVED").triggerFilter(filter).build(); setId(ver, verId);
        WorkflowNode trigger = WorkflowNode.builder().id(UUID.randomUUID()).tenantId(tenantA).workflowVersion(ver).nodeKey("trigger").nodeType(WorkflowNodeType.TRIGGER).name("Trigger").configuration(Map.of("entityType","RECORD","eventType","RECEIVED","triggerFilter",filter)).build(); setId(trigger, trigger.getId());
        WorkflowNode end = WorkflowNode.builder().id(UUID.randomUUID()).tenantId(tenantA).workflowVersion(ver).nodeKey("end").nodeType(WorkflowNodeType.END).name("End").configuration(Map.of()).build(); setId(end, end.getId());
        when(versionRepo.findByIdAndTenantIdAndDeletedFalse(verId, tenantA)).thenReturn(Optional.of(ver));
        when(nodeRepo.findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantA, verId)).thenReturn(List.of(trigger, end));
        when(edgeRepo.findByTenantIdAndWorkflowVersionIdAndDeletedFalse(tenantA, verId)).thenReturn(List.of(com.shivang.crm.modules.workflow.entity.WorkflowEdge.builder().id(UUID.randomUUID()).tenantId(tenantA).workflowVersion(ver).sourceNode(trigger).targetNode(end).build()));
        var errors = validationService.validate(tenantA, verId);
        assertThat(errors.stream().filter(e -> e.code().equals("WORKFLOW_TRIGGER_FILTER_INVALID")).count()).as("expected no validation failure for filter " + filter).isEqualTo(0);
    }

    private void validateFilterExpectValidCustom(Map<String,Object> filter, Map<String, RecordType> extra) {
        validateFilterExpectValid(filter);
    }

    private void validateFilterExpectValidForRecordWithExtraFields(Map<String,Object> filter) {
        validateFilterExpectValid(filter);
    }
    private void validateFilterExpectInvalidForRecordWithExtraFields(Map<String,Object> filter) {
        validateFilterExpectInvalid(filter);
    }
}
