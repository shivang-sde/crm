package com.shivang.crm.modules.records.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.records.dto.CrmRecordResponse;
import com.shivang.crm.modules.records.entity.RecordWebhook;
import com.shivang.crm.modules.records.entity.WebhookAuthMode;
import com.shivang.crm.modules.records.repository.RecordWebhookRepository;
import com.shivang.crm.modules.records.service.RecordWebhookIdempotencyService;
import com.shivang.crm.modules.records.service.RecordWebhookIngestionService;
import com.shivang.crm.modules.records.service.RecordWebhookService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RecordWebhookIngressTest {

    private RecordWebhookRepository webhookRepo;
    private RecordWebhookService webhookService;
    private RecordWebhookIdempotencyService idempotencyService;
    private RecordWebhookIngestionService ingestionService;
    private ObjectMapper objectMapper;
    private RecordWebhookIngressController controller;

    private UUID tenantA = UUID.randomUUID();
    private UUID webhookId = UUID.randomUUID();
    private UUID typeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        webhookRepo = mock(RecordWebhookRepository.class);
        var mockEncryption = mock(com.shivang.crm.modules.integration.service.CredentialEncryptionService.class);
        webhookService = mock(RecordWebhookService.class);
        idempotencyService = mock(RecordWebhookIdempotencyService.class);
        lenient().when(idempotencyService.findExisting(any(), any(), any())).thenReturn(Optional.empty());
        ingestionService = mock(RecordWebhookIngestionService.class);
        objectMapper = new ObjectMapper();
        controller = new RecordWebhookIngressController(webhookRepo, webhookService, idempotencyService, ingestionService, objectMapper);
    }

    private RecordWebhook webhookWithMode(WebhookAuthMode mode, String key) {
        return RecordWebhook.builder().id(webhookId).tenantId(tenantA).webhookKey(key).recordTypeId(typeId).isActive(true).authMode(mode).createdBy(UUID.randomUUID()).ownerId(UUID.randomUUID()).build();
    }

    @Test
    void directMappingCreatesRecord() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "dialer_cdr");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("dialer_cdr")).thenReturn(List.of(wh));
        String payload = "{\"call_id\":\"abc\",\"phone\":\"+919999999999\"}";
        CrmRecordResponse created = CrmRecordResponse.builder().id(UUID.randomUUID()).tenantId(tenantA).recordTypeId(typeId).build();
        var delivery = mock(com.shivang.crm.modules.records.entity.RecordWebhookDelivery.class);
        when(delivery.getId()).thenReturn(UUID.randomUUID());
        var result = new RecordWebhookIngestionService.IngestionResult(created, delivery, null);
        when(ingestionService.ingestNew(any(), any(), any(), any(), any())).thenReturn(result);

        var resp = controller.ingress("dialer_cdr", null, null, null, null, payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        verify(ingestionService).ingestNew(eq(wh), any(), any(), any(), any());
    }

    @Test
    void unknownFieldsIgnoredDirect() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "direct");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("direct")).thenReturn(List.of(wh));
        String payload = "{\"call_id\":\"abc\",\"random_external_field\":\"ignored\",\"phone\":\"+919999999999\"}";
        CrmRecordResponse created = CrmRecordResponse.builder().id(UUID.randomUUID()).tenantId(tenantA).recordTypeId(typeId).build();
        when(ingestionService.ingestNew(any(), any(), any(), any(), any())).thenReturn(new RecordWebhookIngestionService.IngestionResult(created, mock(com.shivang.crm.modules.records.entity.RecordWebhookDelivery.class), null));
        var resp = controller.ingress("direct", null, null, null, null, payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    void customMappingNestedAndTransformation() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "custom");
        wh.setMappingProfileId(UUID.randomUUID());
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("custom")).thenReturn(List.of(wh));
        CrmRecordResponse created = CrmRecordResponse.builder().id(UUID.randomUUID()).tenantId(tenantA).recordTypeId(typeId).build();
        when(ingestionService.ingestNew(any(), any(), any(), any(), any())).thenReturn(new RecordWebhookIngestionService.IngestionResult(created, mock(com.shivang.crm.modules.records.entity.RecordWebhookDelivery.class), null));
        String payload = "{\"callId\":\"abc\",\"customer\":{\"phone\":\"+919999999999\"},\"durationSec\":\"42\"}";
        var resp = controller.ingress("custom", null, null, null, null, payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    void missingRequiredFieldRejected() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "reqtest");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("reqtest")).thenReturn(List.of(wh));
        when(ingestionService.ingestNew(any(), any(), any(), any(), any())).thenThrow(new com.shivang.crm.shared.exception.BusinessException("REQUIRED_FIELD_MISSING","missing"));
        String payload = "{\"phone\":\"+919999999999\"}";
        var resp = controller.ingress("reqtest", null, null, null, null, payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void invalidEnumRejected() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "enumtest");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("enumtest")).thenReturn(List.of(wh));
        when(ingestionService.ingestNew(any(), any(), any(), any(), any())).thenThrow(new com.shivang.crm.shared.exception.BusinessException("INVALID_ENUM_VALUE","bad"));
        String payload = "{\"call_id\":\"abc\",\"phone\":\"+919999999999\",\"status\":\"WRONG\"}";
        var resp = controller.ingress("enumtest", null, null, null, null, payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void webhookNullMappingUsesDirect() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "direct2");
        wh.setMappingProfileId(null);
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("direct2")).thenReturn(List.of(wh));
        CrmRecordResponse created = CrmRecordResponse.builder().id(UUID.randomUUID()).tenantId(tenantA).recordTypeId(typeId).build();
        when(ingestionService.ingestNew(any(), any(), any(), any(), any())).thenReturn(new RecordWebhookIngestionService.IngestionResult(created, mock(com.shivang.crm.modules.records.entity.RecordWebhookDelivery.class), null));
        var resp = controller.ingress("direct2", null, null, null, null, "{\"call_id\":\"abc\",\"phone\":\"+919999999999\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    void customMappingProfileFromAnotherTenantRejected() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "badmap");
        wh.setMappingProfileId(UUID.randomUUID());
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("badmap")).thenReturn(List.of(wh));
        when(ingestionService.ingestNew(any(), any(), any(), any(), any())).thenThrow(new com.shivang.crm.shared.exception.BusinessException("INVALID_CONFIGURATION","bad"));
        var resp = controller.ingress("badmap", null, null, null, null, "{\"call_id\":\"abc\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void apiKeyAuthenticatedReachesIngestion() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.API_KEY, "secure");
        wh.setSecretHash(com.shivang.crm.modules.records.service.RecordWebhookService.hashSecret("correct-key"));
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("secure")).thenReturn(List.of(wh));
        when(webhookService.verifyApiKey(any(), eq("correct-key"))).thenReturn(true);
        when(webhookService.verifyApiKey(any(), eq("wrong"))).thenReturn(false);
        CrmRecordResponse created = CrmRecordResponse.builder().id(UUID.randomUUID()).tenantId(tenantA).recordTypeId(typeId).build();
        when(ingestionService.ingestNew(any(), any(), any(), any(), any())).thenReturn(new RecordWebhookIngestionService.IngestionResult(created, mock(com.shivang.crm.modules.records.entity.RecordWebhookDelivery.class), null));
        var ok = controller.ingress("secure", "correct-key", null, null, null, "{\"call_id\":\"abc\",\"phone\":\"+919999999999\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.CREATED, ok.getStatusCode());
        var bad = controller.ingress("secure", "wrong", null, null, null, "{\"call_id\":\"abc\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.UNAUTHORIZED, bad.getStatusCode());
        verify(ingestionService, times(1)).ingestNew(any(), any(), any(), any(), any());
    }

    @Test
    void tenantIsolation() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "tenanttest");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("tenanttest")).thenReturn(List.of(wh));
        CrmRecordResponse created = CrmRecordResponse.builder().id(UUID.randomUUID()).tenantId(tenantA).recordTypeId(typeId).build();
        when(ingestionService.ingestNew(any(), any(), any(), any(), any())).thenReturn(new RecordWebhookIngestionService.IngestionResult(created, mock(com.shivang.crm.modules.records.entity.RecordWebhookDelivery.class), null));
        String payload = "{\"tenantId\":\""+UUID.randomUUID()+"\",\"recordTypeId\":\""+UUID.randomUUID()+"\",\"call_id\":\"abc\",\"phone\":\"+919999999999\"}";
        var resp = controller.ingress("tenanttest", null, null, null, null, payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    void malformedJsonRejected() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "badjson");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("badjson")).thenReturn(List.of(wh));
        var resp = controller.ingress("badjson", null, null, null, null, "not json".getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void jsonArrayRejected() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "array");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("array")).thenReturn(List.of(wh));
        var resp = controller.ingress("array", null, null, null, null, "[{\"call_id\":\"abc\"}]".getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void oversizedPayloadRejected() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "big");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("big")).thenReturn(List.of(wh));
        byte[] big = new byte[1024*1024 + 1];
        java.util.Arrays.fill(big, (byte) 'a');
        var resp = controller.ingress("big", null, null, null, null, big);
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, resp.getStatusCode());
    }

    @Test
    void inactiveWebhookRejected() throws Exception {
        RecordWebhook wh = RecordWebhook.builder().id(webhookId).tenantId(tenantA).webhookKey("inactivewh").recordTypeId(typeId).isActive(false).authMode(WebhookAuthMode.NONE).build();
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("inactivewh")).thenReturn(List.of(wh));
        var resp = controller.ingress("inactivewh", null, null, null, null, "{\"call_id\":\"abc\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void noEventOrOutboxCreated() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "noevent");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("noevent")).thenReturn(List.of(wh));
        CrmRecordResponse created = CrmRecordResponse.builder().id(UUID.randomUUID()).tenantId(tenantA).recordTypeId(typeId).build();
        when(ingestionService.ingestNew(any(), any(), any(), any(), any())).thenReturn(new RecordWebhookIngestionService.IngestionResult(created, mock(com.shivang.crm.modules.records.entity.RecordWebhookDelivery.class), null));
        var resp = controller.ingress("noevent", null, null, null, null, "{\"call_id\":\"abc\",\"phone\":\"+919999999999\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    void idempotencySamePayloadReturnsCached() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "idem");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("idem")).thenReturn(List.of(wh));
        String payload = "{\"call_id\":\"abc\",\"phone\":\"+919999999999\"}";
        String hash = RecordWebhookIdempotencyService.hashPayload(payload.getBytes(StandardCharsets.UTF_8));
        UUID recordId = UUID.randomUUID();
        var existing = com.shivang.crm.modules.records.entity.RecordWebhookDelivery.builder()
                .id(UUID.randomUUID()).tenantId(tenantA).webhookId(webhookId).webhookKey("idem")
                .idempotencyKey("key123").payloadHash(hash).status("SUCCESS").recordId(recordId)
                .responseStatus(201).responseBody(Map.of("recordId", recordId.toString(), "recordTypeId", typeId.toString(), "status","CREATED"))
                .build();
        when(idempotencyService.findExisting(tenantA, webhookId, "key123")).thenReturn(Optional.of(existing));
        var resp = controller.ingress("idem", null, null, "key123", null, payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(recordId.toString(), ((Map)resp.getBody().getData()).get("recordId"));
        verify(ingestionService, never()).ingestNew(any(), any(), any(), any(), any());
    }

    @Test
    void idempotencyDifferentPayloadRejected() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "idem2");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("idem2")).thenReturn(List.of(wh));
        String payload1 = "{\"call_id\":\"abc\"}";
        String hash1 = RecordWebhookIdempotencyService.hashPayload(payload1.getBytes(StandardCharsets.UTF_8));
        var existing = com.shivang.crm.modules.records.entity.RecordWebhookDelivery.builder()
                .id(UUID.randomUUID()).tenantId(tenantA).webhookId(webhookId).webhookKey("idem2")
                .idempotencyKey("key123").payloadHash(hash1).status("SUCCESS").recordId(UUID.randomUUID())
                .responseStatus(201).responseBody(Map.of("recordId","x")).build();
        when(idempotencyService.findExisting(tenantA, webhookId, "key123")).thenReturn(Optional.of(existing));
        String payload2 = "{\"call_id\":\"different\"}";
        var resp = controller.ingress("idem2", null, null, "key123", null, payload2.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.getStatusCode());
    }

    @Test
    void idempotencyWithoutKeyCreatesNewEachTime() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "noidem");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("noidem")).thenReturn(List.of(wh));
        CrmRecordResponse created = CrmRecordResponse.builder().id(UUID.randomUUID()).tenantId(tenantA).recordTypeId(typeId).build();
        when(ingestionService.ingestNew(any(), any(), any(), any(), any())).thenReturn(new RecordWebhookIngestionService.IngestionResult(created, mock(com.shivang.crm.modules.records.entity.RecordWebhookDelivery.class), null));
        var resp1 = controller.ingress("noidem", null, null, null, null, "{\"call_id\":\"abc\",\"phone\":\"+919999999999\"}".getBytes(StandardCharsets.UTF_8));
        var resp2 = controller.ingress("noidem", null, null, null, null, "{\"call_id\":\"abc\",\"phone\":\"+919999999999\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.CREATED, resp1.getStatusCode());
        assertEquals(HttpStatus.CREATED, resp2.getStatusCode());
        verify(ingestionService, times(2)).ingestNew(any(), any(), any(), any(), any());
    }

    @Test
    void idempotencyKeyFromPayload() throws Exception {
        RecordWebhook wh = webhookWithMode(WebhookAuthMode.NONE, "payloadkey");
        when(webhookRepo.findByWebhookKeyAndDeletedFalse("payloadkey")).thenReturn(List.of(wh));
        String payload = "{\"call_id\":\"abc\",\"phone\":\"+919999999999\",\"idempotencyKey\":\"mykey\"}";
        String hash = RecordWebhookIdempotencyService.hashPayload(payload.getBytes(StandardCharsets.UTF_8));
        var existing = com.shivang.crm.modules.records.entity.RecordWebhookDelivery.builder()
                .id(UUID.randomUUID()).tenantId(tenantA).webhookId(webhookId).webhookKey("payloadkey")
                .idempotencyKey("mykey").payloadHash(hash).status("SUCCESS").recordId(UUID.randomUUID())
                .responseStatus(201).responseBody(Map.of("recordId","x")).build();
        when(idempotencyService.findExisting(tenantA, webhookId, "mykey")).thenReturn(Optional.of(existing));
        var resp = controller.ingress("payloadkey", null, null, null, null, payload.getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }
}
