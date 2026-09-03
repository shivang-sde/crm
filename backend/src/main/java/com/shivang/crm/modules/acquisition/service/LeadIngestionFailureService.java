package com.shivang.crm.modules.acquisition.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;
import com.shivang.crm.modules.acquisition.event.LeadIngestionFailureStage;
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

        if (event.getStatus() == LeadIngestionEventStatus.PROCESSED
            || event.getStatus() == LeadIngestionEventStatus.REJECTED
            || event.getStatus() == LeadIngestionEventStatus.DUPLICATE) {
            log.warn("Ingestion event {} already in terminal status {}; failure state not overwritten", eventId, event.getStatus());
            return event;
        }

        event.setStatus(LeadIngestionEventStatus.FAILED);
        event.setFailureStage(resolveFailureStage(errorCode));
        event.setErrorCode(errorCode);
        event.setErrorMessage(sanitizeErrorMessage(errorMessage));
        event.setProcessedAt(Instant.now());
        LeadIngestionEvent saved = leadIngestionEventRepository.save(event);
        log.error("Ingestion event {} marked FAILED for tenant={} configId={} errorCode={} stage={}",
            eventId, tenantId, saved.getIngestionConfigId(), errorCode, saved.getFailureStage());
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LeadIngestionEvent markDuplicate(UUID tenantId, UUID eventId, String errorMessage) {
        LeadIngestionEvent event = leadIngestionEventRepository
            .findByIdAndTenantIdAndDeletedFalse(eventId, tenantId)
            .orElse(null);

        if (event == null) {
            log.warn("Ingestion event {} not found for tenant {}; duplicate state not recorded", eventId, tenantId);
            return null;
        }

        if (event.getStatus() == LeadIngestionEventStatus.PROCESSED
            || event.getStatus() == LeadIngestionEventStatus.REJECTED
            || event.getStatus() == LeadIngestionEventStatus.DUPLICATE
            || event.getStatus() == LeadIngestionEventStatus.FAILED) {
            log.warn("Ingestion event {} already in terminal status {}; duplicate state not overwritten", eventId, event.getStatus());
            return event;
        }

        event.setStatus(LeadIngestionEventStatus.DUPLICATE);
        event.setFailureStage(LeadIngestionFailureStage.DEDUPLICATION);
        event.setErrorCode("DUPLICATE");
        event.setErrorMessage(sanitizeErrorMessage(errorMessage != null ? errorMessage : "Duplicate lead detected; no new lead created"));
        event.setProcessedAt(Instant.now());
        LeadIngestionEvent saved = leadIngestionEventRepository.save(event);
        log.info("Ingestion event {} marked DUPLICATE for tenant={} configId={}",
            eventId, tenantId, saved.getIngestionConfigId());
        return saved;
    }

    private LeadIngestionFailureStage resolveFailureStage(String errorCode) {
        if (errorCode == null) return LeadIngestionFailureStage.UNKNOWN;
        return switch (errorCode) {
            case "VALIDATION_ERROR", "MAPPING_ERROR" -> LeadIngestionFailureStage.VALIDATION;
            case "LEAD_CREATION_ERROR" -> LeadIngestionFailureStage.LEAD_CREATION;
            case "DUPLICATE" -> LeadIngestionFailureStage.DEDUPLICATION;
            default -> LeadIngestionFailureStage.UNKNOWN;
        };
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Lead ingestion processing failed";
        }
        String trimmed = errorMessage.trim();
        return trimmed.length() <= MAX_ERROR_MESSAGE_LENGTH ? trimmed : trimmed.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
