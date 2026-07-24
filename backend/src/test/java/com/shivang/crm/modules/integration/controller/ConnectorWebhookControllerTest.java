package com.shivang.crm.modules.integration.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ConnectorWebhookConfig;
import com.shivang.crm.modules.integration.entity.ConnectorWebhookEvent;
import com.shivang.crm.modules.integration.entity.ProviderDefinition;
import com.shivang.crm.modules.integration.service.ConnectorInstanceService;
import com.shivang.crm.modules.integration.service.ConnectorWebhookConfigService;
import com.shivang.crm.modules.integration.service.ConnectorWebhookService;
import com.shivang.crm.modules.integration.service.WebhookMappingService;
import com.shivang.crm.modules.integration.webhook.HeaderSanitizer;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookMapper;
import com.shivang.crm.modules.integration.webhook.WebhookVerificationService;
import com.shivang.crm.modules.integration.service.impl.CallWebhookMappingApplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;

public class ConnectorWebhookControllerTest {

    private ConnectorInstanceService connectorInstanceService;
    private ConnectorWebhookConfigService webhookConfigService;
    private ConnectorWebhookService webhookService;
    private WebhookVerificationService verificationService;
    private WebhookMappingService webhookMappingService;
    private HeaderSanitizer headerSanitizer;
    private NormalizedCallWebhookMapper normalizedCallWebhookMapper;
    private CallWebhookMappingApplier callWebhookMappingApplier;
    private ConnectorWebhookController controller;

    private ObjectMapper objectMapper = new ObjectMapper();

    private ConnectorInstance instance;
    private ConnectorWebhookConfig config;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        connectorInstanceService = mock(ConnectorInstanceService.class);
        webhookConfigService = mock(ConnectorWebhookConfigService.class);
        webhookService = mock(ConnectorWebhookService.class);
        verificationService = mock(WebhookVerificationService.class);
        webhookMappingService = mock(WebhookMappingService.class);
        headerSanitizer = mock(HeaderSanitizer.class);
        normalizedCallWebhookMapper = mock(NormalizedCallWebhookMapper.class);
        callWebhookMappingApplier = mock(CallWebhookMappingApplier.class);

        controller = new ConnectorWebhookController(connectorInstanceService, webhookConfigService, webhookService,
                verificationService, webhookMappingService, headerSanitizer, normalizedCallWebhookMapper,
                callWebhookMappingApplier);

        ProviderDefinition provider = ProviderDefinition.builder().providerKey("sellspark_voice").build();
        instance = ConnectorInstance.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .provider(provider)
                .connectorName("sellspark")
                .isActive(true)
                .build();

        config = ConnectorWebhookConfig.builder().id(UUID.randomUUID()).isActive(true).build();

        when(connectorInstanceService.findByProviderKeyAndTenantSlug(eq("sellspark_voice"), any()))
                .thenReturn(Optional.of(instance));
        when(headerSanitizer.sanitize(any())).thenAnswer(i -> Map.of("authorization", "***", "x-signature", "***"));
        when(webhookService.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private HttpServletRequest buildRequest(String body, HttpHeaders headers) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setContentType(MediaType.APPLICATION_JSON_VALUE);
        req.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (headers != null) {
            headers.forEach((k, v) -> req.addHeader(k, String.join(",", v)));
        }
        return req;
    }

    @Test
    public void validCallConnectWebhook_processed() throws Exception {
        when(webhookConfigService.findByTenantAndConnector(eq(instance.getTenantId()), eq(instance.getId())))
                .thenReturn(Optional.of(config));
        when(webhookConfigService.getDecryptedSecret(any())).thenReturn("secret");
        when(verificationService.verifyHmacSha256(any(), any(), any())).thenReturn(true);

        NormalizedCallWebhookEvent normalized = NormalizedCallWebhookEvent.builder()
                .externalCallId("ext-1")
                .externalEventId("evt-1")
                .eventTimestamp(Instant.now())
                .build();

        when(webhookMappingService.loadActiveMappings(any(), any(), eq("call-connect"))).thenReturn(List.of());
        when(normalizedCallWebhookMapper.map(any(), any())).thenReturn(normalized);

        String body = objectMapper.writeValueAsString(Map.of("call_id", "ext-1"));

        when(callWebhookMappingApplier.applyConnect(
                eq(instance.getTenantId()),
                eq(normalized),
                eq(body))).thenReturn("PROCESSED");

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Signature", "sig");

        org.springframework.http.ResponseEntity<?> resp = controller.receiveWebhook("tenantA", "sellspark_voice",
                "call-connect", headers, buildRequest(body, headers));
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("received", ((Map) resp.getBody()).get("status"));

        verify(webhookService, atLeastOnce()).save(any(ConnectorWebhookEvent.class));
        verify(callWebhookMappingApplier, times(1)).applyConnect(eq(instance.getTenantId()), eq(normalized), eq(body));
    }

    @Test
    public void validCdrWebhook_processed() throws Exception {
        when(webhookConfigService.findByTenantAndConnector(eq(instance.getTenantId()), eq(instance.getId())))
                .thenReturn(Optional.of(config));
        when(webhookConfigService.getDecryptedSecret(any())).thenReturn("secret");
        when(verificationService.verifyHmacSha256(any(), any(), any())).thenReturn(true);

        NormalizedCallWebhookEvent normalized = NormalizedCallWebhookEvent.builder()
                .externalCallId("ext-2")
                .externalEventId("evt-2")
                .eventTimestamp(Instant.now())
                .build();

        when(webhookMappingService.loadActiveMappings(any(), any(), eq("cdr"))).thenReturn(List.of());
        when(normalizedCallWebhookMapper.map(any(), any())).thenReturn(normalized);

        String body = objectMapper.writeValueAsString(Map.of("call_id", "ext-2"));

        when(callWebhookMappingApplier.applyCdr(eq(instance.getTenantId()), eq(normalized), eq(body)))
                .thenReturn("PROCESSED");

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Signature", "sig");

        org.springframework.http.ResponseEntity<?> resp = controller.receiveWebhook("tenantA", "sellspark_voice", "cdr",
                headers, buildRequest(body, headers));
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("received", ((Map) resp.getBody()).get("status"));

        verify(webhookService, atLeastOnce()).save(any(ConnectorWebhookEvent.class));
        verify(callWebhookMappingApplier, times(1)).applyCdr(eq(instance.getTenantId()), eq(normalized), eq(body));
    }

    @Test
    public void invalidSignature_blocksProcessing() throws Exception {
        when(webhookConfigService.findByTenantAndConnector(eq(instance.getTenantId()), eq(instance.getId())))
                .thenReturn(Optional.of(config));
        when(webhookConfigService.getDecryptedSecret(any())).thenReturn("secret");
        when(verificationService.verifyHmacSha256(any(), any(), any())).thenReturn(false);

        String body = objectMapper.writeValueAsString(Map.of("call_id", "ext-3"));
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Signature", "bad");

        org.springframework.http.ResponseEntity<?> resp = controller.receiveWebhook("tenantA", "sellspark_voice",
                "call-connect", headers, buildRequest(body, headers));
        assertEquals(401, resp.getStatusCode().value());

        verify(webhookMappingService, times(0)).loadActiveMappings(any(), any(), any());
        verify(callWebhookMappingApplier, never()).applyConnect(
                any(UUID.class),
                any(NormalizedCallWebhookEvent.class),
                anyString());
        verify(webhookService, atLeastOnce()).save(any(ConnectorWebhookEvent.class));
    }

    @Test
    public void missingSignature_blocksProcessing() throws Exception {
        when(webhookConfigService.findByTenantAndConnector(eq(instance.getTenantId()), eq(instance.getId())))
                .thenReturn(Optional.of(config));
        when(webhookConfigService.getDecryptedSecret(any())).thenReturn("secret");

        String body = objectMapper.writeValueAsString(Map.of("call_id", "ext-4"));
        HttpHeaders headers = new HttpHeaders();

        org.springframework.http.ResponseEntity<?> resp = controller.receiveWebhook("tenantA", "sellspark_voice",
                "call-connect", headers, buildRequest(body, headers));
        assertEquals(401, resp.getStatusCode().value());

        verify(webhookMappingService, times(0)).loadActiveMappings(any(), any(), any());
        verify(callWebhookMappingApplier, never()).applyConnect(
                any(UUID.class),
                any(NormalizedCallWebhookEvent.class),
                anyString());
    }

    @Test
    public void missingWebhookConfig_forActiveConnector_blocks() throws Exception {
        when(webhookConfigService.findByTenantAndConnector(eq(instance.getTenantId()), eq(instance.getId())))
                .thenReturn(Optional.empty());

        String body = objectMapper.writeValueAsString(Map.of("call_id", "ext-5"));
        HttpHeaders headers = new HttpHeaders();

        org.springframework.http.ResponseEntity<?> resp = controller.receiveWebhook("tenantA", "sellspark_voice",
                "call-connect", headers, buildRequest(body, headers));
        assertEquals(403, resp.getStatusCode().value());

        verify(webhookMappingService, times(0)).loadActiveMappings(any(), any(), any());
        verify(callWebhookMappingApplier, never()).applyConnect(
                any(UUID.class),
                any(NormalizedCallWebhookEvent.class),
                anyString());
    }

    @Test
    public void duplicateWebhook_skipsProcessing() throws Exception {
        when(webhookConfigService.findByTenantAndConnector(eq(instance.getTenantId()), eq(instance.getId())))
                .thenReturn(Optional.of(config));
        when(webhookConfigService.getDecryptedSecret(any())).thenReturn("secret");
        when(verificationService.verifyHmacSha256(any(), any(), any())).thenReturn(true);

        NormalizedCallWebhookEvent normalized = NormalizedCallWebhookEvent.builder()
                .externalCallId("dup-1")
                .externalEventId(null)
                .eventTimestamp(Instant.now())
                .build();

        when(webhookMappingService.loadActiveMappings(any(), any(), any())).thenReturn(List.of());
        when(normalizedCallWebhookMapper.map(any(), any())).thenReturn(normalized);

        when(webhookService.findByConnectorInstanceIdAndIdempotencyKey(eq(instance.getId()), any()))
                .thenReturn(Optional.of(ConnectorWebhookEvent.builder().id(UUID.randomUUID()).build()));

        String body = objectMapper.writeValueAsString(Map.of("call_id", "dup-1"));
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Signature", "sig");

        org.springframework.http.ResponseEntity<?> resp = controller.receiveWebhook("tenantA", "sellspark_voice",
                "call-connect", headers, buildRequest(body, headers));
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("duplicate", ((Map) resp.getBody()).get("status"));

        verify(callWebhookMappingApplier, never()).applyConnect(
                any(UUID.class),
                any(NormalizedCallWebhookEvent.class),
                anyString());
    }

    @Test
    public void mappingFailure_marksMappingFailed() throws Exception {
        when(webhookConfigService.findByTenantAndConnector(eq(instance.getTenantId()), eq(instance.getId())))
                .thenReturn(Optional.of(config));
        when(webhookConfigService.getDecryptedSecret(any())).thenReturn("secret");
        when(verificationService.verifyHmacSha256(any(), any(), any())).thenReturn(true);

        when(webhookMappingService.loadActiveMappings(any(), any(), any())).thenReturn(List.of());
        when(normalizedCallWebhookMapper.map(any(), any())).thenReturn(null);

        String body = objectMapper.writeValueAsString(Map.of("call_id", "mfail-1"));
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Signature", "sig");

        org.springframework.http.ResponseEntity<?> resp = controller.receiveWebhook("tenantA", "sellspark_voice",
                "call-connect", headers, buildRequest(body, headers));
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("mapping_failed", ((Map) resp.getBody()).get("status"));

        verify(callWebhookMappingApplier, never()).applyConnect(
                any(UUID.class),
                any(NormalizedCallWebhookEvent.class),
                anyString());
    }

    @Test
    public void unknownExternalCall_pendingCorrelation() throws Exception {
        when(webhookConfigService.findByTenantAndConnector(eq(instance.getTenantId()), eq(instance.getId())))
                .thenReturn(Optional.of(config));
        when(webhookConfigService.getDecryptedSecret(any())).thenReturn("secret");
        when(verificationService.verifyHmacSha256(any(), any(), any())).thenReturn(true);

        NormalizedCallWebhookEvent normalized = NormalizedCallWebhookEvent.builder()
                .externalCallId("unknown-1")
                .externalEventId("ev-1")
                .eventTimestamp(Instant.now())
                .build();

        when(webhookMappingService.loadActiveMappings(any(), any(), any())).thenReturn(List.of());
        when(normalizedCallWebhookMapper.map(any(), any())).thenReturn(normalized);

        String body = objectMapper.writeValueAsString(
                Map.of("call_id", "unknown-1"));

        when(callWebhookMappingApplier.applyConnect(
                eq(instance.getTenantId()),
                eq(normalized),
                eq(body))).thenReturn("PENDING_CORRELATION");

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Signature", "sig");

        org.springframework.http.ResponseEntity<?> resp = controller.receiveWebhook("tenantA", "sellspark_voice",
                "call-connect", headers, buildRequest(body, headers));
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("received", ((Map) resp.getBody()).get("status"));

        verify(callWebhookMappingApplier, times(1)).applyConnect(
                eq(instance.getTenantId()),
                eq(normalized),
                eq(body));
    }

    @Test
    public void headersAreSanitizedAndTenantIsolation() throws Exception {
        when(webhookConfigService.findByTenantAndConnector(eq(instance.getTenantId()), eq(instance.getId())))
                .thenReturn(Optional.of(config));
        when(webhookConfigService.getDecryptedSecret(any())).thenReturn("secret");
        when(verificationService.verifyHmacSha256(any(), any(), any())).thenReturn(true);

        NormalizedCallWebhookEvent normalized = NormalizedCallWebhookEvent.builder()
                .externalCallId("ext-9")
                .eventTimestamp(Instant.now())
                .build();

        when(webhookMappingService.loadActiveMappings(any(), any(), any())).thenReturn(List.of());
        when(normalizedCallWebhookMapper.map(any(), any())).thenReturn(normalized);

        String body = objectMapper.writeValueAsString(Map.of("tenantId", "otherTenant", "call_id", "ext-9"));

        when(callWebhookMappingApplier.applyConnect(eq(instance.getTenantId()), eq(normalized), eq(body)))
                .thenReturn("PROCESSED");

        // sanitized headers mock returns masked values
        when(headerSanitizer.sanitize(any()))
                .thenReturn(Map.of("authorization", "***", "x-signature", "***", "x-api-key", "***"));

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer secretvalue");
        headers.add("X-Signature", "sig");
        headers.add("X-API-Key", "apikey");

        org.springframework.http.ResponseEntity<?> resp = controller.receiveWebhook("tenantA", "sellspark_voice",
                "call-connect", headers, buildRequest(body, headers));
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("received", ((Map) resp.getBody()).get("status"));

        // verify saved event used instance tenant (tenant isolation) and headers were
        // sanitized
        verify(webhookService, atLeastOnce()).save(argThat(evt -> evt.getTenantId().equals(instance.getTenantId())
                && evt.getEventHeaders() != null && evt.getEventHeaders().containsKey("authorization")
                && "***".equals(evt.getEventHeaders().get("authorization"))));
    }
}
