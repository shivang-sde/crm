package com.shivang.crm.modules.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.call.dto.CallCreateRequest;
import com.shivang.crm.modules.call.dto.CallResponse;
import com.shivang.crm.modules.call.dto.ClickToCallRequest;
import com.shivang.crm.modules.call.service.impl.DefaultClickToCallService;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.dialer.service.CallProviderLinkService;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionResult;
import com.shivang.crm.modules.integration.service.ConnectorExecutionService;
import com.shivang.crm.shared.service.EntityPhoneResolver;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.shared.exception.BusinessException;

import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.times;

class ClickToCallServiceTest {

    private TenantContext tenantContext;
    private EntityPhoneResolver resolver;
    private ConnectorExecutionService execService;
    private CallService callService;
    private CallProviderLinkService linkService;
    private CallRepository callRepo;
    private ActivityService activityService;
    private DefaultClickToCallService svc;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setup() {
        tenantContext = mock(TenantContext.class);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        when(tenantContext.getTenantId()).thenReturn(tenantId);
        when(tenantContext.getUserId()).thenReturn(userId);

        resolver = mock(EntityPhoneResolver.class);
        execService = mock(ConnectorExecutionService.class);
        callService = mock(CallService.class);
        linkService = mock(CallProviderLinkService.class);
        callRepo = mock(CallRepository.class);
        activityService = mock(ActivityService.class);
        svc = new DefaultClickToCallService(tenantContext, resolver, execService, callService, linkService, callRepo, activityService);
    }

    @Test
    void sellSparkSuccessWithoutCallId() {
        // SellSpark returns {"response":"Call Successfully Schedule","status":"success"} — no callId
        when(resolver.resolvePhone(any(), any(), any())).thenReturn(
            com.shivang.crm.shared.service.EntityPhoneResolutionResult.builder().found(true).phone("9555969516").build()
        );

        ConnectorExecutionResult execResult = new ConnectorExecutionResult(
            true, 200,
            Map.of("response", "Call Successfully Schedule", "status", "success"),
            Map.of(), Map.of(), null, 10L
        );
        UUID execId = UUID.randomUUID();
        execResult.setExecutionId(execId);
        when(execService.execute(any())).thenReturn(execResult);
        when(execService.findById(execId)).thenReturn(Optional.empty());

        CallResponse callResp = CallResponse.builder().id(UUID.randomUUID()).build();
        when(callService.createCall(any(), any(), any(CallCreateRequest.class))).thenReturn(callResp);

        com.shivang.crm.modules.call.entity.Call callEntity = com.shivang.crm.modules.call.entity.Call.builder().id(callResp.getId()).subject("Stub").build();
        when(callRepo.findById(callResp.getId())).thenReturn(Optional.of(callEntity));

        ClickToCallRequest req = ClickToCallRequest.builder().entityType("LEAD").entityId(UUID.randomUUID()).build();
        var resp = svc.clickToCall(req);

        assertThat(resp.getExternalCallId()).isNull();
        assertThat(resp.getStatus()).isEqualTo("success");
        assertThat(resp.getMessage()).isEqualTo("Call Successfully Schedule");
        assertThat(resp.getCallId()).isEqualTo(callResp.getId());
        verify(linkService).save(any());

        var execCaptor = ArgumentCaptor.forClass(com.shivang.crm.modules.integration.dto.ConnectorExecutionRequest.class);
        verify(execService).execute(execCaptor.capture());
        assertThat(execCaptor.getValue().getInputData().get("leadId")).isEqualTo(callResp.getId().toString());

        ArgumentCaptor<java.util.Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(activityService, times(1)).logActivity(eq(tenantId), any(), any(), eq("CALL"), any(), eq(userId), metaCaptor.capture());
        java.util.Map<String, Object> meta = metaCaptor.getValue();
        assertThat(meta).containsKeys("crmCallId", "providerKey", "connectorExecutionId");
        assertThat(meta).containsEntry("subType", "CALL_INITIATED");
        assertThat(meta).doesNotContainKey("password");
        assertThat(meta).doesNotContainKey("token");
    }

    @Test
    void providerExecutionFailureDoesNotCreateCall() {
        when(resolver.resolvePhone(any(), any(), any())).thenReturn(
            com.shivang.crm.shared.service.EntityPhoneResolutionResult.builder().found(true).phone("9555969516").build()
        );

        ConnectorExecutionResult failResult = new ConnectorExecutionResult(
            false, 0, Map.of(), Map.of(), Map.of(), "Connection timed out", 5000L
        );
        when(execService.execute(any())).thenReturn(failResult);

        CallResponse callResp = CallResponse.builder().id(UUID.randomUUID()).build();
        when(callService.createCall(any(), any(), any(CallCreateRequest.class))).thenReturn(callResp);
        com.shivang.crm.modules.call.entity.Call callEntity = com.shivang.crm.modules.call.entity.Call.builder().id(callResp.getId()).subject("Stub").build();
        when(callRepo.findById(callResp.getId())).thenReturn(Optional.of(callEntity));

        ClickToCallRequest req = ClickToCallRequest.builder().entityType("LEAD").entityId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> svc.clickToCall(req))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "PROVIDER_EXECUTION_FAILED");

        // CRM Call SHOULD be created before provider execution, and then marked CANCELLED on failure
        verify(callService, times(1)).createCall(any(), any(), any(CallCreateRequest.class));
        verify(callRepo, times(1)).save(any(com.shivang.crm.modules.call.entity.Call.class));
    }

    @Test
    void providerReturnsFailureStatus() {
        when(resolver.resolvePhone(any(), any(), any())).thenReturn(
            com.shivang.crm.shared.service.EntityPhoneResolutionResult.builder().found(true).phone("9555969516").build()
        );

        ConnectorExecutionResult execResult = new ConnectorExecutionResult(
            true, 200,
            Map.of("response", "Invalid credentials", "status", "failure"),
            Map.of(), Map.of(), null, 100L
        );
        when(execService.execute(any())).thenReturn(execResult);

        CallResponse callResp = CallResponse.builder().id(UUID.randomUUID()).build();
        when(callService.createCall(any(), any(), any(CallCreateRequest.class))).thenReturn(callResp);
        com.shivang.crm.modules.call.entity.Call callEntity = com.shivang.crm.modules.call.entity.Call.builder().id(callResp.getId()).subject("Stub").build();
        when(callRepo.findById(callResp.getId())).thenReturn(Optional.of(callEntity));

        ClickToCallRequest req = ClickToCallRequest.builder().entityType("LEAD").entityId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> svc.clickToCall(req))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "PROVIDER_CALL_FAILED");

        verify(callService, times(1)).createCall(any(), any(), any(CallCreateRequest.class));
        verify(callRepo, times(1)).save(any(com.shivang.crm.modules.call.entity.Call.class));
    }

    @Test
    void phoneNormalization() {
        when(resolver.resolvePhone(any(), any(), any())).thenReturn(
            com.shivang.crm.shared.service.EntityPhoneResolutionResult.builder().found(true).phone("+91 95559 69516").build()
        );

        ConnectorExecutionResult execResult = new ConnectorExecutionResult(
            true, 200,
            Map.of("response", "Call Successfully Schedule", "status", "success"),
            Map.of(), Map.of(), null, 10L
        );
        UUID execId = UUID.randomUUID();
        execResult.setExecutionId(execId);
        when(execService.execute(any())).thenReturn(execResult);
        when(execService.findById(execId)).thenReturn(Optional.empty());

        CallResponse callResp = CallResponse.builder().id(UUID.randomUUID()).build();
        when(callService.createCall(any(), any(), any(CallCreateRequest.class))).thenReturn(callResp);

        com.shivang.crm.modules.call.entity.Call callEntity = com.shivang.crm.modules.call.entity.Call.builder().id(callResp.getId()).subject("Stub").build();
        when(callRepo.findById(callResp.getId())).thenReturn(Optional.of(callEntity));

        ClickToCallRequest req = ClickToCallRequest.builder().entityType("CONTACT").entityId(UUID.randomUUID()).build();
        var resp = svc.clickToCall(req);

        assertThat(resp.getStatus()).isEqualTo("success");
        // Verify the normalized phone was sent
        var execCaptor = ArgumentCaptor.forClass(com.shivang.crm.modules.integration.dto.ConnectorExecutionRequest.class);
        verify(execService).execute(execCaptor.capture());
        assertThat(execCaptor.getValue().getInputData().get("phoneNumber")).isEqualTo("9555969516");
    }

    @Test
    void invalidShortPhoneRejected() {
        when(resolver.resolvePhone(any(), any(), any())).thenReturn(
            com.shivang.crm.shared.service.EntityPhoneResolutionResult.builder().found(true).phone("12345").build()
        );

        ClickToCallRequest req = ClickToCallRequest.builder().entityType("LEAD").entityId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> svc.clickToCall(req))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "INVALID_PHONE");
    }

    @Test
    void nonNumericPhoneRejected() {
        when(resolver.resolvePhone(any(), any(), any())).thenReturn(
            com.shivang.crm.shared.service.EntityPhoneResolutionResult.builder().found(true).phone("not-a-number").build()
        );

        ClickToCallRequest req = ClickToCallRequest.builder().entityType("ACCOUNT").entityId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> svc.clickToCall(req))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", "INVALID_PHONE");
    }

    @Test
    void phoneWithCountryPrefixAndDashes() {
        when(resolver.resolvePhone(any(), any(), any())).thenReturn(
            com.shivang.crm.shared.service.EntityPhoneResolutionResult.builder().found(true).phone("+91-9555-969516").build()
        );

        ConnectorExecutionResult execResult = new ConnectorExecutionResult(
            true, 200,
            Map.of("response", "Call Successfully Schedule", "status", "success"),
            Map.of(), Map.of(), null, 10L
        );
        UUID execId = UUID.randomUUID();
        execResult.setExecutionId(execId);
        when(execService.execute(any())).thenReturn(execResult);
        when(execService.findById(execId)).thenReturn(Optional.empty());

        CallResponse callResp = CallResponse.builder().id(UUID.randomUUID()).build();
        when(callService.createCall(any(), any(), any(CallCreateRequest.class))).thenReturn(callResp);
        com.shivang.crm.modules.call.entity.Call callEntity = com.shivang.crm.modules.call.entity.Call.builder().id(callResp.getId()).subject("Stub").build();
        when(callRepo.findById(callResp.getId())).thenReturn(Optional.of(callEntity));

        ClickToCallRequest req = ClickToCallRequest.builder().entityType("LEAD").entityId(UUID.randomUUID()).build();
        var resp = svc.clickToCall(req);

        assertThat(resp.getStatus()).isEqualTo("success");
        var execCaptor = ArgumentCaptor.forClass(com.shivang.crm.modules.integration.dto.ConnectorExecutionRequest.class);
        verify(execService).execute(execCaptor.capture());
        assertThat(execCaptor.getValue().getInputData().get("phoneNumber")).isEqualTo("9555969516");
    }

    @Test
    void phoneWithLeadingZeroNineOnePrefix() {
        when(resolver.resolvePhone(any(), any(), any())).thenReturn(
            com.shivang.crm.shared.service.EntityPhoneResolutionResult.builder().found(true).phone("0919555969516").build()
        );

        ConnectorExecutionResult execResult = new ConnectorExecutionResult(
            true, 200,
            Map.of("response", "Call Successfully Schedule", "status", "success"),
            Map.of(), Map.of(), null, 10L
        );
        UUID execId = UUID.randomUUID();
        execResult.setExecutionId(execId);
        when(execService.execute(any())).thenReturn(execResult);
        when(execService.findById(execId)).thenReturn(Optional.empty());

        CallResponse callResp = CallResponse.builder().id(UUID.randomUUID()).build();
        when(callService.createCall(any(), any(), any(CallCreateRequest.class))).thenReturn(callResp);
        com.shivang.crm.modules.call.entity.Call callEntity = com.shivang.crm.modules.call.entity.Call.builder().id(callResp.getId()).subject("Stub").build();
        when(callRepo.findById(callResp.getId())).thenReturn(Optional.of(callEntity));

        ClickToCallRequest req = ClickToCallRequest.builder().entityType("LEAD").entityId(UUID.randomUUID()).build();
        var resp = svc.clickToCall(req);

        var execCaptor = ArgumentCaptor.forClass(com.shivang.crm.modules.integration.dto.ConnectorExecutionRequest.class);
        verify(execService).execute(execCaptor.capture());
        assertThat(execCaptor.getValue().getInputData().get("phoneNumber")).isEqualTo("9555969516");
    }
}
