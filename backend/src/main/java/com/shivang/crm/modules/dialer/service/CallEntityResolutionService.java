package com.shivang.crm.modules.dialer.service;

import java.util.Optional;
import java.util.UUID;

import com.shivang.crm.modules.dialer.entity.CallProviderLink;
import com.shivang.crm.modules.integration.webhook.NormalizedCallWebhookEvent;

public interface CallEntityResolutionService {
    record ResolutionResult(boolean resolved, String entityType, UUID entityId, String reason) {}

    ResolutionResult resolveByTrigger(UUID tenantId, NormalizedCallWebhookEvent event, CallProviderLink link, com.shivang.crm.modules.dialer.entity.CallConnectTrigger trigger);
}
