package com.shivang.crm.modules.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.times;

class ClickToCallServiceTest {

    @Test
    void executesProviderAndCreatesCallAndLink() {
        TenantContext tenantContext = mock(TenantContext.class);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(tenantContext.getTenantId()).thenReturn(tenantId);
        when(tenantContext.getUserId()).thenReturn(userId);

        EntityPhoneResolver resolver = mock(EntityPhoneResolver.class);
        when(resolver.resolvePhone(any(), any(), any())).thenReturn(
            com.shivang.crm.shared.service.EntityPhoneResolutionResult.builder().found(true).phone("+999").build()
        );

        ConnectorExecutionService execService = mock(ConnectorExecutionService.class);
        ConnectorExecutionResult execResult = new ConnectorExecutionResult(true, 200, Map.of("status", "initiated", "callId", "call_123"), Map.of(), Map.of(), null, 10L);
        UUID execId = UUID.randomUUID();
        execResult.setExecutionId(execId);
        when(execService.execute(any())).thenReturn(execResult);
        when(execService.findById(execId)).thenReturn(Optional.empty());

        com.shivang.crm.modules.call.service.CallService callService = mock(com.shivang.crm.modules.call.service.CallService.class);
        CallResponse callResp = CallResponse.builder().id(UUID.randomUUID()).build();
        when(callService.createCall(any(), any(), any(CallCreateRequest.class))).thenReturn(callResp);

        CallRepository callRepo = mock(CallRepository.class);
        com.shivang.crm.modules.call.entity.Call callEntity = com.shivang.crm.modules.call.entity.Call.builder().id(callResp.getId()).subject("Stub").build();
        when(callRepo.findById(callResp.getId())).thenReturn(Optional.of(callEntity));

        CallProviderLinkService linkService = mock(CallProviderLinkService.class);
        ActivityService activityService = mock(ActivityService.class);

        DefaultClickToCallService svc = new DefaultClickToCallService(tenantContext, resolver, execService, callService, linkService, callRepo, activityService);

        ClickToCallRequest req = ClickToCallRequest.builder().entityType("LEAD").entityId(UUID.randomUUID()).build();
        var resp = svc.clickToCall(req);

        assertThat(resp.getExternalCallId()).isEqualTo("call_123");
        verify(linkService).save(any());

        ArgumentCaptor<java.util.Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(activityService, times(1)).logActivity(eq(tenantId), any(), any(), eq("CALL"), any(), eq(userId), metaCaptor.capture());
        java.util.Map<String, Object> meta = metaCaptor.getValue();
        assertThat(meta).containsKeys("crmCallId", "providerKey", "externalCallId", "connectorExecutionId");
        assertThat(meta).containsEntry("subType", "CALL_INITIATED");
        assertThat(meta).doesNotContainKey("password");
        assertThat(meta).doesNotContainKey("token");
        assertThat(meta).doesNotContainKey("authorization");
        assertThat(meta).doesNotContainKey("secret");
        assertThat(meta).doesNotContainKey("credential");
    }
}
