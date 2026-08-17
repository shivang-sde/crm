package com.shivang.crm.shared.event;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxEventClaimService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public List<OutboxEvent> claim(int batchSize, long staleAfterSeconds) {
        List<OutboxEvent> events = outboxEventRepository.claimAvailable(batchSize, staleAfterSeconds);
        Instant now = Instant.now();
        events.forEach(event -> {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setProcessingStartedAt(now);
            event.setNextAttemptAt(null);
        });
        return outboxEventRepository.saveAll(events);
    }
}