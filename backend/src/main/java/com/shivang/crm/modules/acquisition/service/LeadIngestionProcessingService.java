package com.shivang.crm.modules.acquisition.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.acquisition.dto.MappedLeadData;
import com.shivang.crm.modules.acquisition.dto.ValidatedLeadIngestionData;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionEventRepository;
import com.shivang.crm.modules.lead.dto.LeadCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadResponse;
import com.shivang.crm.modules.lead.service.LeadService;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadIngestionProcessingService {

    private final LeadIngestionEventRepository leadIngestionEventRepository;
    private final LeadIngestionConfigRepository leadIngestionConfigRepository;
    private final LeadIngestionMappingService leadIngestionMappingService;
    private final LeadIngestionValidationService leadIngestionValidationService;
    private final LeadIngestionSystemActorService leadIngestionSystemActorService;
    private final LeadService leadService;

    @Transactional
    public LeadIngestionEvent processEvent(UUID tenantId, UUID configId, UUID eventId) {
        LeadIngestionEvent event = leadIngestionEventRepository
            .findByIdForUpdate(eventId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Ingestion event not found"));

        if (!tenantId.equals(event.getTenantId()) || !configId.equals(event.getIngestionConfigId())) {
            throw new BusinessException("VALIDATION_ERROR", "Ingestion event does not belong to the supplied config");
        }

        if (event.getLeadId() != null && event.getStatus() == LeadIngestionEventStatus.PROCESSED) {
            return event;
        }

        if (event.getStatus() == LeadIngestionEventStatus.PROCESSED || event.getStatus() == LeadIngestionEventStatus.REJECTED) {
            return event;
        }

        event.setStatus(LeadIngestionEventStatus.PROCESSING);
        leadIngestionEventRepository.save(event);

        try {
            MappedLeadData mappedLeadData = leadIngestionMappingService.preview(tenantId, configId, eventId);
            ValidatedLeadIngestionData validated = leadIngestionValidationService.validateAndNormalize(tenantId, mappedLeadData);

            if (!validated.getErrors().isEmpty()) {
                event.setStatus(LeadIngestionEventStatus.REJECTED);
                event.setErrorCode("VALIDATION_ERROR");
                event.setErrorMessage(getValidationMessage(validated));
                event.setProcessedAt(Instant.now());
                return leadIngestionEventRepository.save(event);
            }

            LeadCreateRequest request = buildLeadCreateRequest(validated);
            UUID systemActorId = leadIngestionSystemActorService.ensureSystemActor(tenantId);
            LeadResponse created = leadService.createLeadInternal(
                tenantId,
                systemActorId,
                request,
                Map.of(
                    "source", "UNIVERSAL_LEAD_INGESTION",
                    "ingestionConfigId", configId,
                    "ingestionEventId", eventId
                )
            );

            event.setLeadId(created.getId());
            event.setStatus(LeadIngestionEventStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            event.setErrorCode(null);
            event.setErrorMessage(null);
            return leadIngestionEventRepository.save(event);
        } catch (Exception ex) {
            log.error("Failed to process ingestion event {} for tenant={} configId={}", eventId, tenantId, configId, ex);
            event.setStatus(LeadIngestionEventStatus.FAILED);
            event.setErrorCode("PROCESSING_ERROR");
            event.setErrorMessage(ex.getMessage());
            event.setProcessedAt(Instant.now());
            return leadIngestionEventRepository.save(event);
        }
    }

    private LeadCreateRequest buildLeadCreateRequest(ValidatedLeadIngestionData validated) {
        if (validated == null) {
            throw new BusinessException("VALIDATION_ERROR", "Nothing to create from mapped ingestion data");
        }

        UUID statusId = readUuid(validated.getStatusValue());
        if (statusId == null) {
            throw new BusinessException("VALIDATION_ERROR", "Lead status is required for processed ingestion data");
        }

        return LeadCreateRequest.builder()
            .firstName(validated.getFirstName())
            .lastName(validated.getLastName())
            .email(validated.getEmail())
            .phone(validated.getPhone())
            .company(validated.getCompany())
            .statusId(statusId)
            .sourceId(readUuid(validated.getSourceValue()))
            .ownerUserId(null)
            .customData(validated.getCustomData())
            .build();
    }

    private UUID readUuid(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof UUID uuid) {
            return uuid;
        }
        if (rawValue instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String getValidationMessage(ValidatedLeadIngestionData validated) {
        if (validated == null || validated.getErrors() == null || validated.getErrors().isEmpty()) {
            return "Validation failed";
        }

        List<String> messages = validated.getErrors().stream()
            .map(error -> error.getCode() + ":" + error.getMessage())
            .toList();

        return String.join("; ", messages);
    }
}