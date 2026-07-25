package com.shivang.crm.modules.dialer.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.dialer.dto.CallOpeningInstruction;
import com.shivang.crm.modules.dialer.entity.CallConnectTrigger;
import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.dialer.service.CallConnectTriggerService;
import com.shivang.crm.modules.dialer.service.CallEntityResolutionService;
import com.shivang.crm.modules.dialer.service.CallOpeningDecisionService;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultCallOpeningDecisionService implements CallOpeningDecisionService {

    private final CallConnectTriggerService triggerService;
    private final CallEntityResolutionService resolver;

    @Override
    public DecisionResult decide(UUID tenantId, NormalizedCallWebhookEvent event, CallProviderLink link) {
        String direction = normalizeDirection(event.getDirection());
        List<CallConnectTrigger> triggers = triggerService.findActiveByTenantAndDirection(tenantId, direction);
        if (triggers.isEmpty()) {
            return new DecisionResult(defaultInstruction(event), false, null, "No triggers");
        }

        for (CallConnectTrigger t : triggers) {
            var res = resolver.resolveByTrigger(tenantId, event, link, t);
            if (res.resolved()) {
                var instr = CallOpeningInstruction.builder()
                    .actionType(t.getOpenActionType())
                    .entityType(res.entityType())
                    .entityId(res.entityId() == null ? null : res.entityId().toString())
                    .callId(link != null && link.getCall() != null ? link.getCall().getId().toString()  : null )
                    .externalCallId(event.getExternalCallId())
                    .layoutId(t.getConfig() == null ? null : String.valueOf(t.getConfig().get("layoutId")))
                    .route(t.getTargetRoute())
                    .resolved(true)
                    .reason(res.reason())
                    .build();
                return new DecisionResult(instr, true, t.getTriggerKey(), res.reason());
            }
        }

        return new DecisionResult(defaultInstruction(event), false, null, "No matching trigger resolution");
    }

    private CallOpeningInstruction defaultInstruction(NormalizedCallWebhookEvent event) {
        return CallOpeningInstruction.builder()
            .actionType("NONE")
            .displayMode("NONE")
            .resolved(false)
            .externalCallId(event.getExternalCallId())
            .callId(null)
            .build();
    }

    private String normalizeDirection(String direction) {
    if (direction == null || direction.isBlank()) {
        return "INBOUND";
    }

    return switch (direction.trim().toUpperCase()) {
        case "INCOMING", "INBOUND", "IN" -> "INBOUND";
        case "OUTGOING", "OUTBOUND", "OUT" -> "OUTBOUND";
        default -> direction.trim().toUpperCase();
    };
}
}
