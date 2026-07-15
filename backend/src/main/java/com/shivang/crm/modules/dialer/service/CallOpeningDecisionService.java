package com.shivang.crm.modules.dialer.service;

import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.dialer.dto.CallOpeningInstruction;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;

public interface CallOpeningDecisionService {
    record DecisionResult(CallOpeningInstruction instruction, boolean shouldOpen, String triggerKey, String reason) {}

    DecisionResult decide(UUID tenantId, NormalizedCallWebhookEvent event);
}
