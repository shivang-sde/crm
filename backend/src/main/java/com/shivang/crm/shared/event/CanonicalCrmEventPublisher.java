package com.shivang.crm.shared.event;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CanonicalCrmEventPublisher {

    private final CanonicalCrmEventOutboxService canonicalCrmEventOutboxService;

    public void publishLeadCreated(UUID tenantId, UUID leadId, UUID ingestionConfigId, UUID ingestionEventId) {
        publishLeadCreated(tenantId, leadId, Map.of(
            "source", CanonicalCrmEvent.UNIVERSAL_LEAD_INGESTION_SOURCE,
            "ingestionConfigId", ingestionConfigId,
            "ingestionEventId", ingestionEventId
        ));
    }

    public void publishLeadCreated(UUID tenantId, UUID leadId, Map<String, Object> metadata) {
        canonicalCrmEventOutboxService.enqueue(
            CanonicalCrmEvent.leadCreated(tenantId, leadId, metadata)
        );
    }
}