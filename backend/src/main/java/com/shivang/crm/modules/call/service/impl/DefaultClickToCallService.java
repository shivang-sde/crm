package com.shivang.crm.modules.call.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.call.dto.CallCreateRequest;
import com.shivang.crm.modules.call.dto.CallResponse;
import com.shivang.crm.modules.call.dto.ClickToCallRequest;
import com.shivang.crm.modules.call.dto.ClickToCallResponse;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.entity.Call.CallStatus;
import com.shivang.crm.modules.call.entity.Call.CallType;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.call.service.CallService;
import com.shivang.crm.modules.call.service.ClickToCallService;
import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.dialer.service.CallProviderLinkService;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionRequest;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionResult;
import com.shivang.crm.modules.integration.entity.ConnectorExecution;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.service.ConnectorExecutionService;
import com.shivang.crm.modules.integration.service.ConnectorInstanceService;
import com.shivang.crm.shared.service.EntityPhoneResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultClickToCallService implements ClickToCallService {

    private final TenantContext tenantContext;
    private final EntityPhoneResolver phoneResolver;
    private final ConnectorExecutionService connectorExecutionService;
    private final ConnectorInstanceService connectorInstanceService;
    private final CallService callService;
    private final CallProviderLinkService callProviderLinkService;
    private final CallRepository callRepository;
    private final ActivityService activityService;

    /**
     * Click-to-call orchestration.
     *
     * Transaction boundaries:
     *   1. CRM Call creation → own transaction (via CallService.createCall)
     *   2. Provider HTTP → NO active database transaction
     *   3. CallProviderLink save → own transaction
     *   4. Activity log → own call
     *   5. On failure → update CRM Call status in separate transaction
     *
     * This avoids holding a Hikari connection open during the external HTTP call.
     */
    @Override
    public ClickToCallResponse clickToCall(ClickToCallRequest request) {
        return clickToCall(tenantContext.getTenantId(), tenantContext.getUserId(), request);
    }

    public ClickToCallResponse clickToCall(UUID tenantId, UUID actorId, ClickToCallRequest request) {

        // ── Provider/instance must be explicit — no fallback (FE/BE-WF-28/32) ──
        String providerKey = request.getProviderKey();
        UUID connectorInstanceId = request.getConnectorInstanceId();
        if (connectorInstanceId != null) {
            ConnectorInstance instance = connectorInstanceService.findById(tenantId, connectorInstanceId)
                .orElseThrow(() -> new com.shivang.crm.shared.exception.BusinessException("CONNECTOR_NOT_FOUND", "Calling connection not found"));
            if (instance.getProvider() == null || instance.getProvider().getProviderKey() == null) {
                throw new com.shivang.crm.shared.exception.BusinessException("PROVIDER_NOT_FOUND", "Provider not found for connection");
            }
            providerKey = instance.getProvider().getProviderKey();
        } else {
            if (providerKey == null || providerKey.isBlank()) {
                throw new com.shivang.crm.shared.exception.BusinessException("PROVIDER_REQUIRED", "Click to Call requires a configured calling provider");
            }
            providerKey = providerKey.trim();
            // If tenant has multiple instances for this provider, the workflow-selected instance is preferred;
            // for direct calls without instance, the first active instance for the provider will be used by ConnectorExecutionService.
        }

        // ── Step 1: Resolve and normalize phone ──
        String phone = resolveAndNormalizePhone(request, tenantId);

        // ── Step 2: Create CRM Call FIRST (own transaction via CallService) ──
        CallCreateRequest createReq = CallCreateRequest.builder()
                .subject(request.getSubject() != null ? request.getSubject() : "Click-to-Call")
                .callType(CallType.OUTGOING)
                .status(CallStatus.PLANNED)
                .phoneNumber(phone)
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .build();

        CallResponse callResponse = callService.createCall(tenantId, actorId, createReq);
        UUID callId = callResponse.getId();
        log.info("Created CRM Call {} before provider execution for tenant {}", callId, tenantId);

        // ── Step 3: Execute provider HTTP (outside database transaction) ──
        ConnectorExecutionResult result;
        try {
            ConnectorExecutionRequest execRequest = new ConnectorExecutionRequest();
            execRequest.setTenantId(tenantId);
            execRequest.setUserId(actorId);
            execRequest.setProviderKey(providerKey);
            execRequest.setConnectorInstanceId(request.getConnectorInstanceId());
            execRequest.setActionKey("CLICK_TO_CALL");
            execRequest.setEntityType(request.getEntityType());
            execRequest.setEntityId(request.getEntityId());
            Map<String, Object> entityData = new HashMap<>();
            entityData.put("phone", phone);
            if (request.getEntityId() != null) {
                entityData.put("id", request.getEntityId().toString());
            }
            execRequest.setEntityData(entityData);
            // Use CRM Call ID as leadId for correlation
            execRequest.setInputData(Map.of("phoneNumber", phone, "leadId", callId.toString()));

            result = connectorExecutionService.execute(execRequest);
        } catch (Exception ex) {
            // Provider execution threw — mark the CRM Call as failed
            markCallFailed(callId, "Provider execution error: " + ex.getMessage());
            throw new com.shivang.crm.shared.exception.BusinessException("PROVIDER_EXECUTION_FAILED", ex.getMessage());
        }

        // ── Step 4: Check provider execution success ──
        if (!result.isSuccess()) {
            String errorMsg = result.getErrorMessage() != null ? result.getErrorMessage() : "Provider execution failed";
            markCallFailed(callId, errorMsg);
            throw new com.shivang.crm.shared.exception.BusinessException("PROVIDER_EXECUTION_FAILED", errorMsg);
        }

        // Parse SellSpark response
        String status = null;
        String providerMessage = null;
        if (result.getResponseBody() != null) {
            Object st = result.getResponseBody().get("status");
            Object resp = result.getResponseBody().get("response");
            status = st != null ? st.toString() : null;
            providerMessage = resp != null ? resp.toString() : null;
        }

        if (!"success".equalsIgnoreCase(status)) {
            String failMsg = providerMessage != null ? providerMessage : "Provider did not return success status";
            markCallFailed(callId, failMsg);
            throw new com.shivang.crm.shared.exception.BusinessException("PROVIDER_CALL_FAILED", failMsg);
        }

        // ── Step 5: Save CallProviderLink (own transaction) ──
        saveProviderLink(tenantId, actorId, callId, result, providerKey);

        // ── Step 6: Log activity ──
        logCallInitiatedActivity(tenantId, actorId, callId, phone, request, result, providerKey);

        String message = providerMessage != null ? providerMessage : "Call scheduled successfully";

        return ClickToCallResponse.builder()
            .callId(callId)
            .externalCallId(null) // SellSpark does not return a call ID
            .status(status)
            .message(message)
            .call(callResponse)
            .build();
    }

    private String resolveAndNormalizePhone(ClickToCallRequest request, UUID tenantId) {
        String phone = request.getPhoneNumber();
        if (phone == null || phone.isBlank()) {
            var res = phoneResolver.resolvePhone(request.getEntityType(), request.getEntityId(), tenantId);
            if (!res.isFound()) {
                throw new com.shivang.crm.shared.exception.BusinessException("PHONE_NOT_FOUND", "Unable to resolve phone for entity");
            }
            phone = res.getPhone();
        }

        if (phone != null) {
            phone = phone.replaceAll("\\s+", "").replace("+91", "").replaceAll("[^0-9]", "");
            if (phone.length() > 10) {
                phone = phone.substring(phone.length() - 10);
            }
            if (phone.length() != 10) {
                throw new com.shivang.crm.shared.exception.BusinessException("INVALID_PHONE", "Phone number must resolve to exactly 10 digits");
            }
        }
        return phone;
    }

    @Transactional
    protected void saveProviderLink(UUID tenantId, UUID actorId, UUID callId, ConnectorExecutionResult result, String providerKey) {
        Call callEntity = callRepository.findById(callId)
            .orElseThrow(() -> new RuntimeException("Call entity not found after creation: " + callId));

        ConnectorExecution connectorExecutionEntity = null;
        if (result.getExecutionId() != null) {
            connectorExecutionEntity = connectorExecutionService.findById(result.getExecutionId()).orElse(null);
        }

        Map<String, Object> linkMetadata = new HashMap<>();
        linkMetadata.put("providerKey", providerKey);

        CallProviderLink link = CallProviderLink.builder()
            .tenantId(tenantId)
            .call(callEntity)
            .externalCallId(null) // SellSpark does not return an external call ID
            .correlationKey(callId.toString()) // CRM Call ID as correlation key
            .linkedAt(java.time.Instant.now())
            .createdBy(actorId)
            .metadata(linkMetadata)
            .build();

        if (connectorExecutionEntity != null) {
            link.setConnectorExecution(connectorExecutionEntity);
            link.setProvider(connectorExecutionEntity.getConnectorInstance().getProvider());
        }

        callProviderLinkService.save(link);
        log.info("Saved CallProviderLink for CRM Call {} with correlationKey={}", callId, callId);
    }

    @Transactional
    protected void markCallFailed(UUID callId, String reason) {
        try {
            callRepository.findById(callId).ifPresent(call -> {
                call.setStatus(CallStatus.CANCELLED);
                Map<String, Object> custom = call.getCustomData() == null ? new HashMap<>() : new HashMap<>(call.getCustomData());
                custom.put("failureReason", reason);
                custom.put("failedAt", java.time.Instant.now().toString());
                call.setCustomData(custom);
                callRepository.save(call);
                log.warn("Marked CRM Call {} as CANCELLED due to provider failure: {}", callId, reason);
            });
        } catch (Exception e) {
            log.error("Failed to mark Call {} as CANCELLED: {}", callId, e.getMessage());
        }
    }

    private void logCallInitiatedActivity(UUID tenantId, UUID actorId, UUID callId, String phone,
                                          ClickToCallRequest request, ConnectorExecutionResult result, String providerKey) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("crmCallId", callId);
        metadata.put("providerKey", providerKey);
        metadata.put("connectorExecutionId", result.getExecutionId());
        metadata.put("subType", "CALL_INITIATED");

        String description = "Call initiated to " + phone;
        activityService.logActivity(tenantId, request.getEntityId(), request.getEntityType(), "CALL", description, actorId, metadata);
    }
}
