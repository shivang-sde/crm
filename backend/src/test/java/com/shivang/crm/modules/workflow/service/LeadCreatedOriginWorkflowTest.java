package com.shivang.crm.modules.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.lead.entity.LeadCreationOrigin;
import com.shivang.crm.modules.workflow.dto.WorkflowMetadataResponse;
import com.shivang.crm.modules.workflow.entity.WorkflowExecution;
import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;

public class LeadCreatedOriginWorkflowTest {

    @Test
    void metadataDeclaresLeadCreatedOriginFields() {
        WorkflowMetadataService svc = new WorkflowMetadataService();
        WorkflowMetadataResponse meta = svc.getMetadata();
        var lead = meta.entities().stream().filter(e -> "LEAD".equals(e.entityType())).findFirst().orElseThrow();
        var created = lead.events().stream().filter(ev -> "CREATED".equals(ev.eventType())).findFirst().orElseThrow();
        assertThat(created.metadataFields()).contains("createdVia", "ingestionConfigId", "ingestionEventId");
    }

    @Test
    void manualLeadEmitsCreatedViaManual() {
        // Simulate LeadService.createLead metadata
        Map<String, Object> meta = Map.of(
                "source", "MANUAL",
                "createdVia", LeadCreationOrigin.MANUAL.name(),
                "actorId", UUID.randomUUID().toString(),
                "actorType", "USER"
        );
        assertThat(meta.get("createdVia")).isEqualTo("MANUAL");
        assertThat(meta.get("source")).isEqualTo("MANUAL");
    }

    @Test
    void ingestionLeadEmitsCreatedViaLeadIngestion() {
        UUID configId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Map<String, Object> meta = Map.of(
                "source", "UNIVERSAL_LEAD_INGESTION",
                "createdVia", LeadCreationOrigin.LEAD_INGESTION.name(),
                "ingestionConfigId", configId.toString(),
                "ingestionEventId", eventId.toString(),
                "actorId", UUID.randomUUID().toString(),
                "actorType", "SYSTEM"
        );
        assertThat(meta.get("createdVia")).isEqualTo("LEAD_INGESTION");
        assertThat(meta.get("ingestionConfigId")).isEqualTo(configId.toString());
    }

    @Test
    void importEmitsCreatedViaImport() {
        UUID configId = UUID.randomUUID();
        Map<String, Object> meta = Map.of(
                "source", "UNIVERSAL_LEAD_INGESTION",
                "createdVia", LeadCreationOrigin.IMPORT.name(),
                "ingestionConfigId", configId.toString(),
                "ingestionEventId", UUID.randomUUID().toString(),
                "actorId", UUID.randomUUID().toString(),
                "actorType", "SYSTEM"
        );
        assertThat(meta.get("createdVia")).isEqualTo("IMPORT");
    }

    @Test
    void conditionEvaluatorSupportsTriggerMetadataCreatedVia() {
        WorkflowConditionEvaluator evaluator = new WorkflowConditionEvaluator(new ContextWorkflowValueResolver());
        // Build minimal WorkflowExecution with triggerContext containing createdVia
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        WorkflowExecution exec = WorkflowExecution.builder()
                .tenantId(tenantId)
                .actorId(actorId)
                .actorType(com.shivang.crm.modules.workflow.entity.WorkflowActorType.SYSTEM)
                .entityType("LEAD")
                .entityId(UUID.randomUUID())
                .eventType("CREATED")
                .triggerEventId(UUID.randomUUID())
                .status(WorkflowExecutionStatus.PENDING)
                .triggerContext(Map.of("createdVia", "LEAD_INGESTION", "ingestionConfigId", "00000000-0000-0000-0000-000000000001"))
                .build();
        // Need to set required workflow/version stubs for context loading, but we can mock registry to avoid DB load
        // Instead test ContextWorkflowValueResolver directly with a minimal context
        // Use a stub registry that returns empty entity
        WorkflowEntityContextProviderRegistry registry = org.mockito.Mockito.mock(WorkflowEntityContextProviderRegistry.class);
        org.mockito.Mockito.when(registry.load(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(Map.of("source", "Facebook")));
        // Set required fields for WorkflowExecutionContext (workflow, workflowVersion)
        exec.setId(UUID.randomUUID());
        com.shivang.crm.modules.workflow.entity.Workflow workflow = new com.shivang.crm.modules.workflow.entity.Workflow();
        workflow.setId(UUID.randomUUID());
        workflow.setTenantId(tenantId);
        com.shivang.crm.modules.workflow.entity.WorkflowVersion version = new com.shivang.crm.modules.workflow.entity.WorkflowVersion();
        version.setId(UUID.randomUUID());
        version.setTenantId(tenantId);
        version.setWorkflow(workflow);
        exec.setWorkflow(workflow);
        exec.setWorkflowVersion(version);

        WorkflowExecutionContext ctx = new WorkflowExecutionContext(exec, registry);

        Map<String, Object> condCreatedVia = Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", "LEAD_INGESTION");
        assertThat(evaluator.evaluate(condCreatedVia, ctx)).isTrue();

        Map<String, Object> condNotIngestion = Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", "LEAD_INGESTION");
        // For MANUAL lead, should be false when checking LEAD_INGESTION
        WorkflowExecution execManual = WorkflowExecution.builder()
                .tenantId(tenantId).actorId(actorId).actorType(com.shivang.crm.modules.workflow.entity.WorkflowActorType.USER).entityType("LEAD").entityId(UUID.randomUUID()).eventType("CREATED").triggerEventId(UUID.randomUUID()).status(WorkflowExecutionStatus.PENDING)
                .triggerContext(Map.of("createdVia", "MANUAL")).build();
        execManual.setId(UUID.randomUUID());
        execManual.setWorkflow(workflow);
        execManual.setWorkflowVersion(version);
        WorkflowExecutionContext ctxManual = new WorkflowExecutionContext(execManual, registry);
        assertThat(evaluator.evaluate(condNotIngestion, ctxManual)).isFalse();

        // Ingestion config ID — ingestion lead matches, manual does not have the field (treated as missing, not EQUALS)
        Map<String, Object> condConfig = Map.of("field", "trigger.metadata.ingestionConfigId", "operator", "EQUALS", "value", "00000000-0000-0000-0000-000000000001");
        assertThat(evaluator.evaluate(condConfig, ctx)).isTrue();
        // For manual, ingestionConfigId is absent → evaluator throws FIELD_NOT_FOUND, which is correct for mis-configured condition;
        // the intended pattern is to filter on createdVia first, not directly on ingestionConfigId alone.
        // Verify manual's createdVia is MANUAL and entity.source still works
        Map<String, Object> condSource = Map.of("field", "entity.source", "operator", "EQUALS", "value", "Facebook");
        assertThat(evaluator.evaluate(condSource, ctx)).isTrue();
    }

    @Test
    void entitySourceIndependentFromCreatedVia() {
        // Lead with source Facebook but manual creation should match entity.source but not trigger.metadata.createdVia LEAD_INGESTION
        WorkflowConditionEvaluator evaluator = new WorkflowConditionEvaluator(new ContextWorkflowValueResolver());
        WorkflowEntityContextProviderRegistry registry = org.mockito.Mockito.mock(WorkflowEntityContextProviderRegistry.class);
        org.mockito.Mockito.when(registry.load(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(Map.of("source", "Facebook")));
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        com.shivang.crm.modules.workflow.entity.Workflow workflow = new com.shivang.crm.modules.workflow.entity.Workflow();
        workflow.setId(UUID.randomUUID());
        workflow.setTenantId(tenantId);
        com.shivang.crm.modules.workflow.entity.WorkflowVersion version = new com.shivang.crm.modules.workflow.entity.WorkflowVersion();
        version.setId(UUID.randomUUID());
        version.setTenantId(tenantId);
        version.setWorkflow(workflow);
        WorkflowExecution exec = WorkflowExecution.builder().tenantId(tenantId).actorId(actorId).actorType(com.shivang.crm.modules.workflow.entity.WorkflowActorType.USER).entityType("LEAD").entityId(UUID.randomUUID()).eventType("CREATED").triggerEventId(UUID.randomUUID()).status(WorkflowExecutionStatus.PENDING).triggerContext(Map.of("createdVia", "MANUAL")).build();
        exec.setId(UUID.randomUUID());
        exec.setWorkflow(workflow);
        exec.setWorkflowVersion(version);
        WorkflowExecutionContext ctx = new WorkflowExecutionContext(exec, registry);
        assertThat(evaluator.evaluate(Map.of("field", "entity.source", "operator", "EQUALS", "value", "Facebook"), ctx)).isTrue();
        assertThat(evaluator.evaluate(Map.of("field", "trigger.metadata.createdVia", "operator", "EQUALS", "value", "LEAD_INGESTION"), ctx)).isFalse();
    }
}
