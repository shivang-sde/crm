package com.shivang.crm.modules.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.records.entity.CrmRecord;
import com.shivang.crm.modules.records.repository.CrmRecordRepository;
import com.shivang.crm.modules.workflow.entity.Workflow;
import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowVersion;

public class RecordWorkflowEntityContextProviderTest {

    private CrmRecordRepository recordRepo;
    private com.shivang.crm.modules.records.repository.RecordFieldRepository fieldRepo;
    private WorkflowRelatedRecordResolver relatedResolver;
    private RecordWorkflowEntityContextProvider provider;
    private WorkflowEntityContextProviderRegistry registry;
    private ContextWorkflowValueResolver resolver;
    private WorkflowConditionEvaluator evaluator;

    private UUID tenantA = UUID.randomUUID();
    private UUID tenantB = UUID.randomUUID();
    private UUID recordTypeId = UUID.randomUUID();
    private UUID recordId = UUID.randomUUID();
    private UUID ownerId = UUID.randomUUID();
    private UUID createdBy = UUID.randomUUID();

    @BeforeEach
    void setup() {
        recordRepo = mock(CrmRecordRepository.class);
        fieldRepo = mock(com.shivang.crm.modules.records.repository.RecordFieldRepository.class);
        relatedResolver = mock(WorkflowRelatedRecordResolver.class);
        lenient().when(fieldRepo.findActiveByRecordTypeIdAndTenantId(any(), any())).thenReturn(java.util.List.of());
        provider = new RecordWorkflowEntityContextProvider(recordRepo, fieldRepo, relatedResolver);
        // Build registry with RECORD provider plus a LEAD mock to test regression
        WorkflowEntityContextProvider leadMock = mock(WorkflowEntityContextProvider.class);
        when(leadMock.entityType()).thenReturn("LEAD");
        when(leadMock.load(any(), any())).thenReturn(Optional.empty());
        registry = new WorkflowEntityContextProviderRegistry(java.util.List.of(provider, leadMock));
        resolver = new ContextWorkflowValueResolver();
        evaluator = new WorkflowConditionEvaluator(resolver);
    }

    private CrmRecord buildRecord(UUID tenant, UUID id, Map<String,Object> data) {
        CrmRecord r = CrmRecord.builder()
                .id(id)
                .tenantId(tenant)
                .recordTypeId(recordTypeId)
                .ownerId(ownerId)
                .createdBy(createdBy)
                .data(data)
                .build();
        // set BaseEntity fields via reflection
        try {
            var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(r, id);
            var ca = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("createdAt"); ca.setAccessible(true); ca.set(r, Instant.parse("2026-09-14T10:00:00Z"));
            var ua = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("updatedAt"); ua.setAccessible(true); ua.set(r, Instant.parse("2026-09-14T11:00:00Z"));
        } catch (Exception e) { throw new RuntimeException(e); }
        r.setTenantId(tenant);
        return r;
    }

    private WorkflowExecution buildExecution(UUID tenant, UUID entityId, Map<String,Object> triggerContext) {
        Workflow wf = new Workflow(); wf.setId(UUID.randomUUID()); wf.setTenantId(tenant);
        WorkflowVersion ver = new WorkflowVersion(); ver.setId(UUID.randomUUID()); ver.setTenantId(tenant); ver.setWorkflow(wf);
        WorkflowExecution exec = WorkflowExecution.builder()
                .tenantId(tenant)
                .entityType("RECORD")
                .entityId(entityId)
                .eventType("RECEIVED")
                .triggerEventId(UUID.randomUUID())
                .status(WorkflowExecutionStatus.PENDING)
                .triggerContext(triggerContext)
                .build();
        exec.setId(UUID.randomUUID());
        exec.setWorkflow(wf);
        exec.setWorkflowVersion(ver);
        try { var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(exec, UUID.randomUUID()); } catch(Exception e){}
        return exec;
    }

    // A. Record provider loads correct Record
    @Test
    void A_providerLoadsCorrectRecord() {
        Map<String,Object> data = Map.of("customer_name", "Acme", "amount", 15000);
        CrmRecord rec = buildRecord(tenantA, recordId, data);
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantA)).thenReturn(Optional.of(rec));
        var ctxOpt = provider.load(tenantA, recordId);
        assertThat(ctxOpt).isPresent();
        var ctx = ctxOpt.get();
        assertThat(ctx.get("id")).isEqualTo(recordId);
        assertThat(ctx.get("recordTypeId")).isEqualTo(recordTypeId);
        assertThat(ctx.get("ownerId")).isEqualTo(ownerId);
        assertThat(ctx.get("createdBy")).isEqualTo(createdBy);
        assertThat(ctx.get("createdAt")).isNotNull();
        assertThat(ctx.get("updatedAt")).isNotNull();
        assertThat(ctx.get("data")).isEqualTo(data);
    }

    // B. Dynamic Record data and nested
    @Test
    void B_dynamicDataAndNested() {
        Map<String,Object> details = Map.of("city", "Delhi");
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("customerName", "Acme");
        data.put("amount", 15000);
        data.put("active", true);
        data.put("details", details);
        CrmRecord rec = buildRecord(tenantA, recordId, data);
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantA)).thenReturn(Optional.of(rec));
        var exec = buildExecution(tenantA, recordId, Map.of("recordTypeId", recordTypeId.toString(), "webhookId", UUID.randomUUID().toString(), "deliveryId", UUID.randomUUID().toString()));
        WorkflowExecutionContext ctx = new WorkflowExecutionContext(exec, registry);
        // resolver should handle nested
        assertThat(resolver.resolve(ctx, "entity.data.customerName").found()).isTrue();
        assertThat(resolver.resolve(ctx, "entity.data.customerName").value()).isEqualTo("Acme");
        assertThat(resolver.resolve(ctx, "entity.data.amount").value()).isEqualTo(15000);
        assertThat(resolver.resolve(ctx, "entity.data.active").value()).isEqualTo(true);
        assertThat(resolver.resolve(ctx, "entity.data.details.city").value()).isEqualTo("Delhi");
        // also via entity prefix
        assertThat(resolver.resolve(ctx, "entity.data.details.city").found()).isTrue();
    }

    @Test
    void B_nativeTypesPreserved() {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("str", "hello");
        data.put("intVal", 42);
        data.put("longVal", 1234567890123L);
        data.put("decimal", new java.math.BigDecimal("123.45"));
        data.put("bool", false);
        data.put("nullVal", null);
        data.put("arr", java.util.List.of(1,2,3));
        CrmRecord rec = buildRecord(tenantA, recordId, data);
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantA)).thenReturn(Optional.of(rec));
        var exec = buildExecution(tenantA, recordId, Map.of("recordTypeId", recordTypeId.toString()));
        var ctx = new WorkflowExecutionContext(exec, registry);
        assertThat(resolver.resolve(ctx, "entity.data.str").value()).isEqualTo("hello");
        assertThat(resolver.resolve(ctx, "entity.data.intVal").value()).isEqualTo(42);
        assertThat(resolver.resolve(ctx, "entity.data.decimal").value() instanceof java.math.BigDecimal).isTrue();
        assertThat(resolver.resolve(ctx, "entity.data.bool").value()).isEqualTo(false);
        assertThat(resolver.resolve(ctx, "entity.data.nullVal").value()).isNull();
        assertThat(resolver.resolve(ctx, "entity.data.nullVal").found()).isTrue(); // null is present
        assertThat(resolver.resolve(ctx, "entity.data.arr").value()).isEqualTo(java.util.List.of(1,2,3));
    }

    // C. Tenant isolation
    @Test
    void C_tenantIsolation() {
        CrmRecord recB = buildRecord(tenantB, recordId, Map.of("x", "y"));
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantA)).thenReturn(Optional.empty());
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantB)).thenReturn(Optional.of(recB));
        // tenantA cannot load tenantB record
        assertThat(provider.load(tenantA, recordId)).isEmpty();
        // Workflow context for tenantA should have empty entity
        var exec = buildExecution(tenantA, recordId, Map.of("recordTypeId", recordTypeId.toString()));
        var ctx = new WorkflowExecutionContext(exec, registry);
        assertThat(ctx.getEntity()).isEmpty(); // no data
        assertThat(resolver.resolve(ctx, "entity.data.x").found()).isFalse();
        // tenantB can
        assertThat(provider.load(tenantB, recordId)).isPresent();
    }

    // D. Missing Record
    @Test
    void D_missingRecord() {
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(any(), eq(tenantA))).thenReturn(Optional.empty());
        assertThat(provider.load(tenantA, UUID.randomUUID())).isEmpty();
        var exec = buildExecution(tenantA, UUID.randomUUID(), Map.of("recordTypeId", recordTypeId.toString()));
        var ctx = new WorkflowExecutionContext(exec, registry);
        assertThat(ctx.getEntity()).isEmpty();
        // missing field should be not found -> evaluator throws FIELD_NOT_FOUND -> workflow would treat as false for filter
        var res = resolver.resolve(ctx, "entity.data.foo");
        assertThat(res.found()).isFalse();
    }

    // E. Deleted Record (soft delete)
    @Test
    void E_deletedRecordNotExposed() {
        // repo method already filters deletedFalse, so deleted returns empty
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantA)).thenReturn(Optional.empty());
        assertThat(provider.load(tenantA, recordId)).isEmpty();
    }

    // F. RecordType identity
    @Test
    void F_recordTypeIdentity() {
        UUID otherType = UUID.randomUUID();
        CrmRecord rec = buildRecord(tenantA, recordId, Map.of("a","b"));
        // ensure recordTypeId is otherType
        rec.setRecordTypeId(otherType);
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantA)).thenReturn(Optional.of(rec));
        var exec = buildExecution(tenantA, recordId, Map.of("recordTypeId", recordTypeId.toString())); // trigger metadata has old type
        var ctx = new WorkflowExecutionContext(exec, registry);
        assertThat(resolver.resolve(ctx, "entity.recordTypeId").value()).isEqualTo(otherType);
        // trigger metadata still separate
        assertThat(resolver.resolve(ctx, "trigger.metadata.recordTypeId").value()).isEqualTo(recordTypeId.toString());
        assertThat(resolver.resolve(ctx, "entity.recordTypeId").value()).isNotEqualTo(resolver.resolve(ctx, "trigger.metadata.recordTypeId").value());
    }

    // G. Trigger metadata remains separate
    @Test
    void G_triggerMetadataSeparate() {
        UUID wh = UUID.randomUUID(); UUID del = UUID.randomUUID();
        Map<String,Object> triggerMeta = Map.of("recordTypeId", recordTypeId.toString(), "webhookId", wh.toString(), "deliveryId", del.toString());
        CrmRecord rec = buildRecord(tenantA, recordId, Map.of("customerName","Acme"));
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantA)).thenReturn(Optional.of(rec));
        var exec = buildExecution(tenantA, recordId, triggerMeta);
        var ctx = new WorkflowExecutionContext(exec, registry);
        assertThat(resolver.resolve(ctx, "trigger.metadata.recordTypeId").value()).isEqualTo(recordTypeId.toString());
        assertThat(resolver.resolve(ctx, "trigger.metadata.webhookId").value()).isEqualTo(wh.toString());
        assertThat(resolver.resolve(ctx, "trigger.metadata.deliveryId").value()).isEqualTo(del.toString());
        assertThat(resolver.resolve(ctx, "entity.data.customerName").value()).isEqualTo("Acme");
        // entity.recordTypeId is UUID, trigger is string - both present
        assertThat(resolver.resolve(ctx, "entity.recordTypeId").found()).isTrue();
    }

    // H. Condition evaluation
    @Test
    void H_conditionEvaluation() {
        Map<String,Object> data = Map.of("status", "OPEN", "amount", 15000, "customerName", "Acme Corp");
        CrmRecord rec = buildRecord(tenantA, recordId, data);
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantA)).thenReturn(Optional.of(rec));
        var exec = buildExecution(tenantA, recordId, Map.of("recordTypeId", recordTypeId.toString()));
        var ctx = new WorkflowExecutionContext(exec, registry);
        assertThat(evaluator.evaluate(Map.of("field","entity.data.status","operator","EQUALS","value","OPEN"), ctx)).isTrue();
        assertThat(evaluator.evaluate(Map.of("field","entity.data.status","operator","EQUALS","value","CLOSED"), ctx)).isFalse();
        assertThat(evaluator.evaluate(Map.of("field","entity.data.amount","operator","GREATER_THAN","value",10000), ctx)).isTrue();
        assertThat(evaluator.evaluate(Map.of("field","entity.data.amount","operator","LESS_THAN","value",10000), ctx)).isFalse();
        assertThat(evaluator.evaluate(Map.of("field","entity.data.customerName","operator","CONTAINS","value","Acme"), ctx)).isTrue();
        // also entity.recordTypeId
        assertThat(evaluator.evaluate(Map.of("field","entity.recordTypeId","operator","EQUALS","value",recordTypeId.toString()), ctx)).isTrue();
        assertThat(evaluator.evaluate(Map.of("field","entity.recordTypeId","operator","EQUALS","value",UUID.randomUUID().toString()), ctx)).isFalse();
    }

    // I. Value resolution via resolver (simulates {{entity.*}})
    @Test
    void I_valueResolution() {
        Map<String,Object> data = Map.of("customerName", "Acme");
        CrmRecord rec = buildRecord(tenantA, recordId, data);
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantA)).thenReturn(Optional.of(rec));
        var exec = buildExecution(tenantA, recordId, Map.of("recordTypeId", recordTypeId.toString()));
        var ctx = new WorkflowExecutionContext(exec, registry);
        assertThat(resolver.resolve(ctx, "entity.id").value()).isEqualTo(recordId);
        assertThat(resolver.resolve(ctx, "entity.recordTypeId").value()).isEqualTo(recordTypeId);
        assertThat(resolver.resolve(ctx, "entity.data.customerName").value()).isEqualTo("Acme");
        // trigger via same resolver
        assertThat(resolver.resolve(ctx, "trigger.metadata.recordTypeId").value()).isEqualTo(recordTypeId.toString());
    }

    // J. Existing CRM entities regression (lead still works)
    @Test
    void J_leadRegressionStillWorks() {
        // Registry should still load LEAD via lead provider mock
        // Simulate lead load
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(any(), any())).thenReturn(Optional.empty());
        // Lead provider mock already returns empty, but we test registry lookup doesn't break
        var execLead = WorkflowExecution.builder().tenantId(tenantA).entityType("LEAD").entityId(UUID.randomUUID()).eventType("CREATED").triggerEventId(UUID.randomUUID()).status(WorkflowExecutionStatus.PENDING).triggerContext(Map.of("createdVia","MANUAL")).build();
        execLead.setId(UUID.randomUUID());
        Workflow wf = new Workflow(); wf.setId(UUID.randomUUID()); wf.setTenantId(tenantA);
        WorkflowVersion ver = new WorkflowVersion(); ver.setId(UUID.randomUUID()); ver.setTenantId(tenantA); ver.setWorkflow(wf);
        execLead.setWorkflow(wf); execLead.setWorkflowVersion(ver);
        // This should not throw even with RECORD provider present
        var ctx = new WorkflowExecutionContext(execLead, registry);
        // Lead entity is empty due to mock, but trigger still resolves
        assertThat(resolver.resolve(ctx, "trigger.metadata.createdVia").value()).isEqualTo("MANUAL");
        // No exception
        assertThat(ctx.getEntity()).isEmpty();
    }
}
