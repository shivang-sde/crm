package com.shivang.crm.modules.call.service.impl;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.call.dto.CallCreateRequest;
import com.shivang.crm.modules.call.dto.CallResponse;
import com.shivang.crm.modules.call.dto.ClickToCallRequest;
import com.shivang.crm.modules.call.dto.ClickToCallResponse;
import com.shivang.crm.modules.call.service.CallService;
import com.shivang.crm.modules.call.service.ClickToCallService;
import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.dialer.service.CallProviderLinkService;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionRequest;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionResult;
import com.shivang.crm.modules.integration.entity.ConnectorExecution;
import com.shivang.crm.modules.integration.service.ConnectorExecutionService;
import com.shivang.crm.shared.service.EntityPhoneResolver;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.call.entity.Call;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultClickToCallService implements ClickToCallService {

    private final TenantContext tenantContext;
    private final EntityPhoneResolver phoneResolver;
    private final ConnectorExecutionService connectorExecutionService;
    private final CallService callService;
    private final CallProviderLinkService callProviderLinkService;
    private final CallRepository callRepository;
    private final ActivityService activityService;

    @Override
    @Transactional
    public ClickToCallResponse clickToCall(ClickToCallRequest request) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();

        // Resolve phone
        String phone = request.getPhoneNumber();
        if (phone == null || phone.isBlank()) {
            var res = phoneResolver.resolvePhone(request.getEntityType(), request.getEntityId(), tenantId);
            if (!res.isFound()) {
                throw new com.shivang.crm.shared.exception.BusinessException("PHONE_NOT_FOUND", "Unable to resolve phone for entity");
            }
            phone = res.getPhone();
        }

        // Build connector execution request
        ConnectorExecutionRequest execRequest = new ConnectorExecutionRequest();
        execRequest.setTenantId(tenantId);
        execRequest.setUserId(userId);
        execRequest.setProviderKey("sellspark_voice");
        execRequest.setActionKey("CLICK_TO_CALL");
        execRequest.setEntityType(request.getEntityType());
        execRequest.setEntityId(request.getEntityId());
        execRequest.setEntityData(Map.of("phone", phone, "id", request.getEntityId()));
        execRequest.setInputData(Map.of("phoneNumber", phone));

        ConnectorExecutionResult result = connectorExecutionService.execute(execRequest);

        String externalCallId = null;
        String status = null;
        if (result.getResponseBody() != null) {
            Object st = result.getResponseBody().get("status");
            Object cid = result.getResponseBody().get("callId");
            status = st != null ? st.toString() : null;
            externalCallId = cid != null ? cid.toString() : null;
        }

        if (externalCallId == null) {
            throw new com.shivang.crm.shared.exception.BusinessException("PROVIDER_RESPONSE_INVALID", "Provider did not return callId");
        }

        // Create CRM Call
        CallCreateRequest createReq = CallCreateRequest.builder()
            .subject(request.getSubject() != null ? request.getSubject() : "Click-to-Call")
            .callType(com.shivang.crm.modules.call.entity.Call.CallType.OUTGOING)
            .phoneNumber(phone)
            .entityType(request.getEntityType())
            .entityId(request.getEntityId())
            .build();

        CallResponse callResponse = callService.createCall(tenantId, userId, createReq);

        // Link provider
        ConnectorExecution connectorExecutionEntity = null;
        if (result.getExecutionId() != null) {
            connectorExecutionEntity = connectorExecutionService.findById(result.getExecutionId()).orElse(null);
        }

        UUID callId = callResponse.getId();
        Call callEntity = callRepository.findById(callId).orElseThrow(() -> new RuntimeException("Call entity not found"));

        CallProviderLink link = CallProviderLink.builder()
            .tenantId(tenantId)
            .call(callEntity)
            .externalCallId(externalCallId)
            .linkedAt(java.time.Instant.now())
            .createdBy(userId)
            .metadata(Map.of("providerKey", "sellspark_voice", "status", status))
            .build();

        if (connectorExecutionEntity != null) {
            link.setConnectorExecution(connectorExecutionEntity);
            link.setProvider(connectorExecutionEntity.getConnectorInstance().getProvider());
        }

        callProviderLinkService.save(link);

        // Log activity
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("crmCallId", callResponse.getId());
        metadata.put("providerKey", "sellspark_voice");
        metadata.put("externalCallId", externalCallId);
        metadata.put("connectorExecutionId", result.getExecutionId());
        // Include subtype for UI filtering while keeping activityType generic
        metadata.put("subType", "CALL_INITIATED");

        // Create activity entry linked to the original entity. This is executed within the same transaction
        // so Call, CallProviderLink and Activity are saved atomically.
        String description = "Call initiated to " + phone;
        activityService.logActivity(tenantId, request.getEntityId(), request.getEntityType(), "CALL", description, userId, metadata);

        ClickToCallResponse resp = ClickToCallResponse.builder()
            .callId(callResponse.getId())
            .externalCallId(externalCallId)
            .status(status)
            .call(callResponse)
            .build();

        return resp;
    }
}
