package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.repository.CallRepository;

@Component
public class CallWorkflowEntityContextProvider implements WorkflowEntityContextProvider {

    private final CallRepository callRepository;
    private final WorkflowRelatedRecordResolver relatedRecordResolver;

    public CallWorkflowEntityContextProvider(CallRepository callRepository, WorkflowRelatedRecordResolver relatedRecordResolver) {
        this.callRepository = callRepository;
        this.relatedRecordResolver = relatedRecordResolver;
    }

    @Override
    public String entityType() {
        return "CALL";
    }

    @Override
    public Optional<Map<String, Object>> load(UUID tenantId, UUID entityId) {
        return callRepository.findByIdAndTenantIdAndDeletedFalse(entityId, tenantId)
            .map(this::toContext);
    }

    private Map<String, Object> toContext(Call call) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("id", call.getId());
        context.put("tenantId", call.getTenantId());
        context.put("ownerId", call.getOwnerId());
        context.put("createdBy", call.getCreatedBy());
        context.put("subject", call.getSubject());
        context.put("description", call.getDescription());
        context.put("callType", call.getCallType() == null ? null : call.getCallType().name());
        context.put("status", call.getStatus() == null ? null : call.getStatus().name());
        context.put("phoneNumber", call.getPhoneNumber());
        context.put("startTime", call.getStartTime());
        context.put("endTime", call.getEndTime());
        context.put("disposition", call.getDisposition());
        context.put("notes", call.getNotes());
        context.put("nextAction", call.getNextAction());
        context.put("followUpAt", call.getFollowUpAt());
        context.put("entityType", call.getEntityType());
        context.put("entityId", call.getEntityId());
        context.put("createdAt", call.getCreatedAt());
        context.put("updatedAt", call.getUpdatedAt());
        context.put("customFields", call.getCustomData() == null ? Map.of() : call.getCustomData());
        // Controlled one-hop relationship: Call → linked CRM record (LEAD/CONTACT/ACCOUNT/DEAL).
        context.put("related", relatedRecordResolver
            .related(call.getEntityType(), call.getTenantId(), call.getEntityId())
            .orElse(null));
        return context;
    }
}
