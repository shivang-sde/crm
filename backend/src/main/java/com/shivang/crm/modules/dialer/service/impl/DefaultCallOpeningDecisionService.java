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
public class DefaultCallOpeningDecisionService
        implements CallOpeningDecisionService {

    private final CallConnectTriggerService triggerService;
    private final CallEntityResolutionService resolver;

    @Override
    public DecisionResult decide(
            UUID tenantId,
            NormalizedCallWebhookEvent event,
            CallProviderLink link) {

        if (tenantId == null || event == null) {
            return new DecisionResult(
                    defaultInstruction(event),
                    false,
                    null,
                    "Tenant or webhook event is missing"
            );
        }

        String direction =
                normalizeDirection(event.getDirection());

        List<CallConnectTrigger> triggers =
                triggerService.findActiveByTenantAndDirection(
                        tenantId,
                        direction
                );

        if (triggers == null || triggers.isEmpty()) {
            return new DecisionResult(
                    defaultInstruction(event),
                    false,
                    null,
                    "No active trigger found for direction " + direction
            );
        }

        for (CallConnectTrigger trigger : triggers) {
            var resolution =
                    resolver.resolveByTrigger(
                            tenantId,
                            event,
                            link,
                            trigger
                    );

            if (!resolution.resolved()) {
                continue;
            }

            String actionType =
                    normalizeActionType(
                            trigger.getOpenActionType()
                    );

            CallOpeningInstruction instruction =
                    CallOpeningInstruction.builder()
                            .actionType(actionType)
                            .displayMode(
                                    resolveDisplayMode(actionType)
                            )
                            .entityType(
                                    resolution.entityType()
                            )
                            .entityId(
                                    resolution.entityId() == null
                                            ? null
                                            : resolution.entityId()
                                                    .toString()
                            )
                            .callId(resolveCallId(link))
                            .externalCallId(
                                    event.getExternalCallId()
                            )
                            .layoutId(
                                    resolveConfigString(
                                            trigger,
                                            "layoutId"
                                    )
                            )
                            .route(
                                    resolveRoute(
                                            trigger,
                                            actionType,
                                            link
                                    )
                            )
                            .resolved(true)
                            .reason(resolution.reason())
                            .build();

            return new DecisionResult(
                    instruction,
                    true,
                    trigger.getTriggerKey(),
                    resolution.reason()
            );
        }

        return new DecisionResult(
                defaultInstruction(event),
                false,
                null,
                "No trigger successfully resolved the call"
        );
    }

    private CallOpeningInstruction defaultInstruction(
            NormalizedCallWebhookEvent event) {

        return CallOpeningInstruction.builder()
                .actionType("NO_ACTION")
                .displayMode("NONE")
                .resolved(false)
                .externalCallId(
                        event == null
                            ? null
                            : event.getExternalCallId()
                )
                .callId(null)
                .build();
    }

    private String resolveCallId(
            CallProviderLink link) {

        if (link == null || link.getCall() == null) {
            return null;
        }

        return link.getCall().getId().toString();
    }

    private String resolveRoute(
            CallConnectTrigger trigger,
            String actionType,
            CallProviderLink link) {

        if (trigger.getTargetRoute() != null
                && !trigger.getTargetRoute().isBlank()) {

            return trigger.getTargetRoute();
        }

        if ("OPEN_CALL_LAYOUT".equals(actionType)) {
            String callId = resolveCallId(link);

            if (callId != null) {
                return "/calls/active/" + callId;
            }
        }

        return null;
    }

    private String resolveConfigString(
            CallConnectTrigger trigger,
            String key) {

        if (trigger.getConfig() == null) {
            return null;
        }

        Object value = trigger.getConfig().get(key);

        if (value == null) {
            return null;
        }

        String stringValue = String.valueOf(value);

        return stringValue.isBlank()
                || "null".equalsIgnoreCase(stringValue)
                    ? null
                    : stringValue;
    }

    private String normalizeActionType(
            String actionType) {

        if (actionType == null || actionType.isBlank()) {
            return "OPEN_CALL_LAYOUT";
        }

        return switch (actionType.trim().toUpperCase()) {
            case "OPEN_PAGE" -> "OPEN_PAGE";
            case "OPEN_MODAL" -> "OPEN_MODAL";
            case "OPEN_SIDEBAR" -> "OPEN_SIDEBAR";
            case "OPEN_CALL_LAYOUT" -> "OPEN_CALL_LAYOUT";
            case "NO_ACTION", "NONE" -> "NO_ACTION";
            default -> "OPEN_CALL_LAYOUT";
        };
    }

    private String resolveDisplayMode(
            String actionType) {

        return switch (actionType) {
            case "OPEN_PAGE" -> "PAGE";
            case "OPEN_MODAL" -> "MODAL";
            case "OPEN_SIDEBAR" -> "SIDEBAR";
            case "OPEN_CALL_LAYOUT" -> "LAYOUT";
            default -> "NONE";
        };
    }

    private String normalizeDirection(
            String direction) {

        if (direction == null || direction.isBlank()) {
            return "INBOUND";
        }

        return switch (
                direction.trim().toUpperCase()
        ) {
            case "INCOMING", "INBOUND", "IN" ->
                    "INBOUND";

            case "OUTGOING", "OUTBOUND", "OUT" ->
                    "OUTBOUND";

            default ->
                    direction.trim().toUpperCase();
        };
    }
}