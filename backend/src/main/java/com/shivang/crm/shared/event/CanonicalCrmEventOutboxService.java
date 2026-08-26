package com.shivang.crm.shared.event;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CanonicalCrmEventOutboxService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxEvent enqueue(CanonicalCrmEvent event) {
        return outboxEventRepository.save(OutboxEvent.builder()
            .eventId(event.eventId())
            .tenantId(event.tenantId())
            .eventType(event.eventType())
            .eventName(event.entityType() + "." + event.eventType())
            .aggregateType(event.entityType())
            .aggregateId(event.entityId())
            .payload(objectMapper.convertValue(event, MAP_TYPE))
            .status(OutboxEventStatus.PENDING)
            .attempts(0)
            .availableAt(event.occurredAt())
            .build());
    }
}