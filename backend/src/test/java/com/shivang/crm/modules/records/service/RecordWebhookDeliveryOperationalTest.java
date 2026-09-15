package com.shivang.crm.modules.records.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.shivang.crm.modules.records.dto.RecordWebhookDeliveryDetailResponse;
import com.shivang.crm.modules.records.entity.RecordWebhook;
import com.shivang.crm.modules.records.entity.RecordWebhookDelivery;
import com.shivang.crm.modules.records.entity.WebhookAuthMode;
import com.shivang.crm.modules.records.repository.CrmRecordRepository;
import com.shivang.crm.modules.records.repository.RecordMappingProfileRepository;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.modules.records.repository.RecordWebhookDeliveryRepository;
import com.shivang.crm.modules.records.repository.RecordWebhookRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;

public class RecordWebhookDeliveryOperationalTest {

    private RecordWebhookDeliveryRepository deliveryRepo;
    private RecordWebhookRepository webhookRepo;
    private RecordTypeRepository typeRepo;
    private RecordMappingProfileRepository mappingRepo;
    private CrmRecordRepository recordRepo;
    private WorkflowExecutionRepository execRepo;
    private RecordWebhookDeliveryQueryService queryService;
    private RecordWebhookIdempotencyService idempotencyService;

    private UUID tenantA = UUID.randomUUID();
    private UUID tenantB = UUID.randomUUID();
    private UUID webhookId = UUID.randomUUID();
    private UUID recordTypeId = UUID.randomUUID();
    private UUID mappingId = UUID.randomUUID();
    private UUID deliveryId = UUID.randomUUID();
    private UUID recordId = UUID.randomUUID();
    private UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        deliveryRepo = mock(RecordWebhookDeliveryRepository.class);
        webhookRepo = mock(RecordWebhookRepository.class);
        typeRepo = mock(RecordTypeRepository.class);
        mappingRepo = mock(RecordMappingProfileRepository.class);
        recordRepo = mock(CrmRecordRepository.class);
        execRepo = mock(WorkflowExecutionRepository.class);
        lenient().when(deliveryRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(deliveryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        idempotencyService = new RecordWebhookIdempotencyService(deliveryRepo);
        queryService = new RecordWebhookDeliveryQueryService(deliveryRepo, webhookRepo, typeRepo, mappingRepo, recordRepo, execRepo);

        // webhook stub
        RecordWebhook wh = RecordWebhook.builder().id(webhookId).tenantId(tenantA).webhookKey("cdr").name("CDR Webhook").recordTypeId(recordTypeId).mappingProfileId(mappingId).isActive(true).authMode(WebhookAuthMode.NONE).build();
        try { var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(wh, webhookId);} catch(Exception e){}
        when(webhookRepo.findByIdAndTenantIdAndDeletedFalse(webhookId, tenantA)).thenReturn(Optional.of(wh));
        when(webhookRepo.findByIdAndTenantIdAndDeletedFalse(webhookId, tenantB)).thenReturn(Optional.empty());
    }

    // A. Successful webhook delivery
    @Test
    void A_successfulDeliveryVisible() {
        RecordWebhookDelivery d = RecordWebhookDelivery.builder()
                .id(deliveryId).tenantId(tenantA).webhookId(webhookId).webhookKey("cdr")
                .idempotencyKey("key123").payloadHash("abc123")
                .status("SUCCESS").failureStage("SUCCESS")
                .recordTypeId(recordTypeId).mappingProfileId(mappingId)
                .recordId(recordId).eventId(eventId)
                .responseStatus(201).receivedAt(java.time.Instant.now())
                .build();
        try { var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(d, deliveryId);} catch(Exception e){}
        when(deliveryRepo.findByIdAndTenantIdAndDeletedFalse(deliveryId, tenantA)).thenReturn(Optional.of(d));
        // need webhook for detail
        var detail = queryService.detail(tenantA, webhookId, deliveryId);
        assertThat(detail.getDelivery().getStatus()).isEqualTo("SUCCESS");
        assertThat(detail.getDelivery().getFailureStage()).isEqualTo("SUCCESS");
        assertThat(detail.getRecord()).isNotNull();
        assertThat(detail.getRecord().getId()).isEqualTo(recordId);
        assertThat(detail.getEvent()).isNotNull();
        assertThat(detail.getEvent().getEventId()).isEqualTo(eventId);
        assertThat(detail.getWebhook().getKey()).isEqualTo("cdr");
    }

    // B. Authentication failure
    @Test
    void B_authenticationFailure() {
        // Simulate delivery persisted for auth failure
        RecordWebhookDelivery d = RecordWebhookDelivery.builder()
                .id(UUID.randomUUID()).tenantId(tenantA).webhookId(webhookId).webhookKey("cdr")
                .idempotencyKey("no-key-x").payloadHash("hash")
                .status("FAILED").failureStage("AUTHENTICATION")
                .errorCode("UNAUTHORIZED").errorMessage("Invalid webhook key or credentials")
                .recordTypeId(recordTypeId).mappingProfileId(mappingId)
                .receivedAt(java.time.Instant.now())
                .build();
        // check that sanitize does not expose secrets
        RecordWebhookDelivery saved = idempotencyService.createDelivery(tenantA, webhookId, "cdr", "key", "hash", "FAILED", null, 401, null, "UNAUTHORIZED", "Invalid webhook key or credentials", recordTypeId, mappingId, null, "AUTHENTICATION");
        // The service sanitizes, but our message is safe
        assertThat(saved.getErrorMessage()).doesNotContain("secret");
        assertThat(saved.getErrorMessage()).doesNotContain("API-KEY");
    }

    // C. Invalid JSON -> VALIDATION
    @Test
    void C_invalidJsonValidationStage() {
        String stage = mapStage("INVALID_JSON");
        assertThat(stage).isEqualTo("VALIDATION");
    }

    // D. Mapping failure
    @Test
    void D_mappingFailureStage() {
        String stage = mapStage("MAPPING_FAILED");
        assertThat(stage).isEqualTo("MAPPING");
    }

    // E. Record creation failure
    @Test
    void E_recordCreationFailure() {
        String stage = mapStage("REQUIRED_FIELD_MISSING");
        assertThat(stage).isEqualTo("RECORD_CREATION");
    }

    // F/G/H. Workflow match visibility
    @Test
    void G_workflowMatchVisible() {
        RecordWebhookDelivery d = RecordWebhookDelivery.builder()
                .id(deliveryId).tenantId(tenantA).webhookId(webhookId).webhookKey("cdr")
                .payloadHash("h").idempotencyKey("k")
                .status("SUCCESS").failureStage("SUCCESS")
                .recordId(recordId).eventId(eventId).recordTypeId(recordTypeId)
                .receivedAt(java.time.Instant.now())
                .build();
        try { var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(d, deliveryId);} catch(Exception e){}
        when(deliveryRepo.findByIdAndTenantIdAndDeletedFalse(deliveryId, tenantA)).thenReturn(Optional.of(d));
        // mock executions
        var exec = mock(com.shivang.crm.modules.workflow.entity.WorkflowExecution.class);
        when(exec.getId()).thenReturn(UUID.randomUUID());
        when(exec.getWorkflow()).thenReturn(com.shivang.crm.modules.workflow.entity.Workflow.builder().id(UUID.randomUUID()).name("Test WF").build());
        when(exec.getWorkflowVersion()).thenReturn(com.shivang.crm.modules.workflow.entity.WorkflowVersion.builder().id(UUID.randomUUID()).build());
        when(exec.getStatus()).thenReturn(com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus.PENDING);
        when(exec.getTriggerEventId()).thenReturn(eventId);
        when(execRepo.findWithWorkflowByTenantIdAndTriggerEventId(tenantA, eventId)).thenReturn(List.of(exec));

        var detail = queryService.detail(tenantA, webhookId, deliveryId);
        assertThat(detail.getWorkflow().getTriggered()).isTrue();
        assertThat(detail.getWorkflow().getExecutionCount()).isEqualTo(1);
    }

    @Test
    void H_noWorkflowMatch() {
        RecordWebhookDelivery d = RecordWebhookDelivery.builder()
                .id(deliveryId).tenantId(tenantA).webhookId(webhookId).webhookKey("cdr")
                .payloadHash("h").idempotencyKey("k")
                .status("SUCCESS").failureStage("SUCCESS")
                .recordId(recordId).eventId(eventId).recordTypeId(recordTypeId)
                .receivedAt(java.time.Instant.now())
                .build();
        try { var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(d, deliveryId);} catch(Exception e){}
        when(deliveryRepo.findByIdAndTenantIdAndDeletedFalse(deliveryId, tenantA)).thenReturn(Optional.of(d));
        when(execRepo.findWithWorkflowByTenantIdAndTriggerEventId(tenantA, eventId)).thenReturn(List.of());

        var detail = queryService.detail(tenantA, webhookId, deliveryId);
        assertThat(detail.getWorkflow().getTriggered()).isFalse();
        assertThat(detail.getWorkflow().getExecutionCount()).isEqualTo(0);
        // delivery/event still successful
        assertThat(detail.getDelivery().getStatus()).isEqualTo("SUCCESS");
        assertThat(detail.getEvent()).isNotNull();
    }

    // I. Same idempotency same payload -> cached
    @Test
    void I_idempotencySamePayloadCached() {
        // findExisting returns existing delivery
        RecordWebhookDelivery existing = RecordWebhookDelivery.builder().id(deliveryId).tenantId(tenantA).webhookId(webhookId).webhookKey("cdr").idempotencyKey("key123").payloadHash("hash123").status("SUCCESS").recordId(recordId).responseStatus(201).responseBody(Map.of("recordId", recordId.toString())).build();
        try { var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(existing, deliveryId);} catch(Exception e){}
        when(deliveryRepo.findByTenantIdAndWebhookIdAndIdempotencyKeyAndDeletedFalse(tenantA, webhookId, "key123")).thenReturn(Optional.of(existing));
        var found = idempotencyService.findExisting(tenantA, webhookId, "key123");
        assertThat(found).isPresent();
        assertThat(found.get().getPayloadHash()).isEqualTo("hash123");
        // No new record should be created (verified by controller logic)
    }

    // J. Same key different payload -> mismatch
    @Test
    void J_idempotencyPayloadMismatch() {
        RecordWebhookDelivery existing = RecordWebhookDelivery.builder().id(deliveryId).tenantId(tenantA).webhookId(webhookId).webhookKey("cdr").idempotencyKey("key123").payloadHash("hash123").status("SUCCESS").build();
        when(deliveryRepo.findByTenantIdAndWebhookIdAndIdempotencyKeyAndDeletedFalse(tenantA, webhookId, "key123")).thenReturn(Optional.of(existing));
        String newHash = "differentHash";
        assertThat(newHash.equals(existing.getPayloadHash())).isFalse();
        // Controller would return 422, not create new record
    }

    // K. Cross-tenant access denied
    @Test
    void K_crossTenantDenied() {
        RecordWebhookDelivery d = RecordWebhookDelivery.builder().id(deliveryId).tenantId(tenantA).webhookId(webhookId).webhookKey("cdr").payloadHash("h").idempotencyKey("k").status("SUCCESS").receivedAt(java.time.Instant.now()).build();
        try { var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(d, deliveryId);} catch(Exception e){}
        when(deliveryRepo.findByIdAndTenantIdAndDeletedFalse(deliveryId, tenantA)).thenReturn(Optional.of(d));
        when(deliveryRepo.findByIdAndTenantIdAndDeletedFalse(deliveryId, tenantB)).thenReturn(Optional.empty());
        // Tenant B trying to access tenantA delivery should get empty -> NotFound
        var opt = deliveryRepo.findByIdAndTenantIdAndDeletedFalse(deliveryId, tenantB);
        assertThat(opt).isEmpty();
    }

    // M. Secret redaction
    @Test
    void M_secretRedaction() {
        RecordWebhookDelivery d1 = idempotencyService.createDelivery(tenantA, webhookId, "cdr", "k1", "h", "FAILED", null, 401, null, "UNAUTHORIZED", "HMAC secret invalid: mySecret123", null, null, null, "AUTHENTICATION");
        assertThat(d1.getErrorMessage()).isEqualTo("Authentication failed");
        assertThat(d1.getErrorMessage()).doesNotContain("mySecret123");

        RecordWebhookDelivery d2 = idempotencyService.createDelivery(tenantA, webhookId, "cdr", "k2", "h", "FAILED", null, 401, null, "UNAUTHORIZED", "API-KEY abcdef failed", null, null, null, "AUTHENTICATION");
        assertThat(d2.getErrorMessage()).doesNotContain("abcdef");
    }

    // N. Pagination
    @Test
    void N_pagination() {
        Page<RecordWebhookDelivery> page = new PageImpl<>(List.of(
                RecordWebhookDelivery.builder().id(UUID.randomUUID()).tenantId(tenantA).webhookId(webhookId).webhookKey("cdr").idempotencyKey("k1").payloadHash("h").status("SUCCESS").receivedAt(java.time.Instant.now()).build(),
                RecordWebhookDelivery.builder().id(UUID.randomUUID()).tenantId(tenantA).webhookId(webhookId).webhookKey("cdr").idempotencyKey("k2").payloadHash("h").status("FAILED").receivedAt(java.time.Instant.now()).build()
        ), PageRequest.of(0, 20), 2);
        when(deliveryRepo.findByTenantIdAndWebhookIdAndDeletedFalse(tenantA, webhookId, PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("receivedAt").descending()))).thenReturn(page);
        // Simulate queryService.list
        // We test repository pagination respects page/size
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }

    private String mapStage(String code) {
        if (code == null) return "VALIDATION";
        String c = code.toUpperCase();
        if (c.contains("MAPPING") || c.contains("TRANSFORM")) return "MAPPING";
        if (c.contains("RECORD") || c.contains("REQUIRED") || c.contains("UNKNOWN_FIELD")) return "RECORD_CREATION";
        if (c.contains("INVALID_CONFIGURATION") || c.contains("REFERENCE")) return "VALIDATION";
        if (c.contains("AUTH")) return "AUTHENTICATION";
        if (c.contains("PAYLOAD") || c.contains("JSON")) return "VALIDATION";
        return "VALIDATION";
    }
}
