package com.shivang.crm.modules.acquisition.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadIngestionFailureService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final LeadIngestionEventRepository leadIngestionEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LeadIngestionEvent markFailed(UUID tenantId, UUID eventId, String errorCode, String errorMessage) {
        LeadIngestionEvent event = leadIngestionEventRepository
            .findByIdAndTenantIdAndDeletedFalse(eventId, tenantId)
            .orElse(null);

        if (event == null) {
            log.warn("Ingestion event {} not found for tenant {}; failure state not recorded", eventId, tenantId);
            return null;
        }

        if (event.getStatus() == LeadIngestionEventStatus.PROCESSED || event.getStatus() == LeadIngestionEventStatus.REJECTED) {
            log.warn("Ingestion event {} already in terminal status {}; failure state not overwritten", eventId, event.getStatus());
            return event;
        }

        event.setStatus(LeadIngestionEventStatus.FAILED);
        event.setErrorCode(errorCode);
        event.setErrorMessage(sanitizeErrorMessage(errorMessage));
        event.setProcessedAt(Instant.now());
        LeadIngestionEvent saved = leadIngestionEventRepository.save(event);
        log.error("Ingestion event {} marked FAILED for tenant={} configId={} errorCode={}",
            eventId, tenantId, saved.getIngestionConfigId(), errorCode);
        return saved;
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Lead ingestion processing failed";
        }
        String trimmed = errorMessage.trim();
        return trimmed.length() <= MAX_ERROR_MESSAGE_LENGTH ? trimmed : trimmed.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
