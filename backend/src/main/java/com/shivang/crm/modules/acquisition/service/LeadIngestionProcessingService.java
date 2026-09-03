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
import com.shivang.crm.modules.acquisition.event.LeadIngestionFailureStage;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionEventRepository;
import com.shivang.crm.modules.lead.dto.LeadCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadResponse;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.repository.LeadRepository;
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
    private final LeadRepository leadRepository;

    @Transactional
    public LeadIngestionEvent reprocessEvent(UUID tenantId, UUID configId, UUID eventId) {
        LeadIngestionEvent event = leadIngestionEventRepository
            .findByIdForUpdate(eventId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Ingestion event not found"));

        if (!tenantId.equals(event.getTenantId()) || !configId.equals(event.getIngestionConfigId())) {
            throw new BusinessException("VALIDATION_ERROR", "Ingestion event does not belong to the supplied config");
        }

        leadIngestionConfigRepository.findByIdAndTenantIdAndDeletedFalse(configId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Ingestion config not found"));

        if (event.getStatus() == LeadIngestionEventStatus.PROCESSING) {
            throw new BusinessException("ALREADY_PROCESSING", "Ingestion event is already being processed");
        }
        if (event.getStatus() == LeadIngestionEventStatus.PROCESSED) {
            throw new BusinessException("NOT_REPROCESSABLE", "Processed events cannot be reprocessed");
        }
        if (event.getStatus() == LeadIngestionEventStatus.DUPLICATE) {
            throw new BusinessException("NOT_REPROCESSABLE", "Duplicate events cannot be reprocessed");
        }
        if (event.getStatus() != LeadIngestionEventStatus.FAILED
            && event.getStatus() != LeadIngestionEventStatus.REJECTED) {
            throw new BusinessException("NOT_REPROCESSABLE", "Only failed or rejected events can be reprocessed");
        }

        // Prepare for retry: increment attempt, reset to processing, clear previous outcome
        event.setAttemptCount((event.getAttemptCount() == null ? 1 : event.getAttemptCount()) + 1);
        event.setStatus(LeadIngestionEventStatus.PROCESSING);
        event.setFailureStage(null);
        event.setErrorCode(null);
        event.setErrorMessage(null);
        event.setLeadId(null);
        event.setProcessedAt(null);
        leadIngestionEventRepository.save(event);

        // Reuse same pipeline as initial processing (current mapping)
        MappedLeadData mappedLeadData = leadIngestionMappingService.preview(tenantId, configId, eventId);
        if (mappedLeadData.getErrors() != null && !mappedLeadData.getErrors().isEmpty()) {
            event.setStatus(LeadIngestionEventStatus.REJECTED);
            event.setFailureStage(LeadIngestionFailureStage.MAPPING);
            event.setErrorCode("MAPPING_ERROR");
            event.setErrorMessage(String.join("; ", mappedLeadData.getErrors()));
            event.setProcessedAt(Instant.now());
            return leadIngestionEventRepository.save(event);
        }

        ValidatedLeadIngestionData validated = leadIngestionValidationService.validateAndNormalize(tenantId, mappedLeadData);
        if (!validated.getErrors().isEmpty()) {
            event.setStatus(LeadIngestionEventStatus.REJECTED);
            event.setFailureStage(LeadIngestionFailureStage.VALIDATION);
            event.setErrorCode("VALIDATION_ERROR");
            event.setErrorMessage(getValidationMessage(validated));
            event.setProcessedAt(Instant.now());
            return leadIngestionEventRepository.save(event);
        }

        LeadCreateRequest request;
        try {
            request = buildLeadCreateRequest(validated);
        } catch (BusinessException ex) {
            event.setStatus(LeadIngestionEventStatus.REJECTED);
            event.setFailureStage(LeadIngestionFailureStage.VALIDATION);
            event.setErrorCode(ex.getErrorCode());
            event.setErrorMessage(ex.getMessage() != null && ex.getMessage().length() > 1000 ? ex.getMessage().substring(0, 1000) : ex.getMessage());
            event.setProcessedAt(Instant.now());
            return leadIngestionEventRepository.save(event);
        }

        UUID systemActorId = leadIngestionSystemActorService.ensureSystemActor(tenantId);
        LeadResponse created;
        try {
            created = leadService.createLeadInternal(
                tenantId, systemActorId, request,
                Map.of("source", "UNIVERSAL_LEAD_INGESTION", "ingestionConfigId", configId, "ingestionEventId", eventId)
            );
        } catch (BusinessException ex) {
            if ("DUPLICATE".equals(ex.getErrorCode())) {
                return handleDuplicate(tenantId, event, validated, ex.getMessage());
            }
            if ("VALIDATION_ERROR".equals(ex.getErrorCode())) {
                event.setStatus(LeadIngestionEventStatus.REJECTED);
                event.setFailureStage(LeadIngestionFailureStage.VALIDATION);
                event.setErrorCode(ex.getErrorCode());
                event.setErrorMessage(ex.getMessage() != null && ex.getMessage().length() > 1000 ? ex.getMessage().substring(0, 1000) : ex.getMessage());
                event.setProcessedAt(Instant.now());
                return leadIngestionEventRepository.save(event);
            }
            event.setStatus(LeadIngestionEventStatus.FAILED);
            event.setFailureStage(LeadIngestionFailureStage.LEAD_CREATION);
            event.setErrorCode(ex.getErrorCode());
            event.setErrorMessage(ex.getMessage() != null && ex.getMessage().length() > 1000 ? ex.getMessage().substring(0, 1000) : ex.getMessage());
            event.setProcessedAt(Instant.now());
            return leadIngestionEventRepository.save(event);
        } catch (Exception ex) {
            event.setStatus(LeadIngestionEventStatus.FAILED);
            event.setFailureStage(LeadIngestionFailureStage.LEAD_CREATION);
            event.setErrorCode("LEAD_CREATION_ERROR");
            String msg = ex.getMessage() != null ? ex.getMessage() : "Lead creation failed";
            event.setErrorMessage(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
            event.setProcessedAt(Instant.now());
            return leadIngestionEventRepository.save(event);
        }

        event.setLeadId(created.getId());
        event.setStatus(LeadIngestionEventStatus.PROCESSED);
        event.setFailureStage(null);
        event.setProcessedAt(Instant.now());
        event.setErrorCode(null);
        event.setErrorMessage(null);
        return leadIngestionEventRepository.save(event);
    }

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

        if (event.getStatus() == LeadIngestionEventStatus.PROCESSED
            || event.getStatus() == LeadIngestionEventStatus.REJECTED
            || event.getStatus() == LeadIngestionEventStatus.DUPLICATE
            || event.getStatus() == LeadIngestionEventStatus.FAILED) {
            return event;
        }

        event.setStatus(LeadIngestionEventStatus.PROCESSING);
        event.setFailureStage(null);
        leadIngestionEventRepository.save(event);

        MappedLeadData mappedLeadData = leadIngestionMappingService.preview(tenantId, configId, eventId);
        if (mappedLeadData.getErrors() != null && !mappedLeadData.getErrors().isEmpty()) {
            event.setStatus(LeadIngestionEventStatus.REJECTED);
            event.setFailureStage(LeadIngestionFailureStage.MAPPING);
            event.setErrorCode("MAPPING_ERROR");
            event.setErrorMessage(String.join("; ", mappedLeadData.getErrors()));
            event.setProcessedAt(Instant.now());
            return leadIngestionEventRepository.save(event);
        }

        ValidatedLeadIngestionData validated = leadIngestionValidationService.validateAndNormalize(tenantId, mappedLeadData);

        if (!validated.getErrors().isEmpty()) {
            event.setStatus(LeadIngestionEventStatus.REJECTED);
            event.setFailureStage(LeadIngestionFailureStage.VALIDATION);
            event.setErrorCode("VALIDATION_ERROR");
            event.setErrorMessage(getValidationMessage(validated));
            event.setProcessedAt(Instant.now());
            return leadIngestionEventRepository.save(event);
        }

        LeadCreateRequest request;
        try {
            request = buildLeadCreateRequest(validated);
        } catch (BusinessException ex) {
            event.setStatus(LeadIngestionEventStatus.REJECTED);
            event.setFailureStage(LeadIngestionFailureStage.VALIDATION);
            event.setErrorCode(ex.getErrorCode());
            event.setErrorMessage(ex.getMessage() != null && ex.getMessage().length() > 1000 ? ex.getMessage().substring(0, 1000) : ex.getMessage());
            event.setProcessedAt(Instant.now());
            return leadIngestionEventRepository.save(event);
        }

        UUID systemActorId = leadIngestionSystemActorService.ensureSystemActor(tenantId);
        LeadResponse created;
        try {
            created = leadService.createLeadInternal(
                tenantId,
                systemActorId,
                request,
                Map.of(
                    "source", "UNIVERSAL_LEAD_INGESTION",
                    "ingestionConfigId", configId,
                    "ingestionEventId", eventId
                )
            );
        } catch (BusinessException ex) {
            if ("DUPLICATE".equals(ex.getErrorCode())) {
                return handleDuplicate(tenantId, event, validated, ex.getMessage());
            }
            if ("VALIDATION_ERROR".equals(ex.getErrorCode())) {
                event.setStatus(LeadIngestionEventStatus.REJECTED);
                event.setFailureStage(LeadIngestionFailureStage.VALIDATION);
                event.setErrorCode(ex.getErrorCode());
                event.setErrorMessage(ex.getMessage() != null && ex.getMessage().length() > 1000 ? ex.getMessage().substring(0, 1000) : ex.getMessage());
                event.setProcessedAt(Instant.now());
                return leadIngestionEventRepository.save(event);
            }
            // Other business errors during lead creation (e.g. missing status, source)
            event.setStatus(LeadIngestionEventStatus.FAILED);
            event.setFailureStage(LeadIngestionFailureStage.LEAD_CREATION);
            event.setErrorCode(ex.getErrorCode());
            event.setErrorMessage(ex.getMessage() != null && ex.getMessage().length() > 1000 ? ex.getMessage().substring(0, 1000) : ex.getMessage());
            event.setProcessedAt(Instant.now());
            return leadIngestionEventRepository.save(event);
        } catch (Exception ex) {
            event.setStatus(LeadIngestionEventStatus.FAILED);
            event.setFailureStage(LeadIngestionFailureStage.LEAD_CREATION);
            event.setErrorCode("LEAD_CREATION_ERROR");
            String msg = ex.getMessage() != null ? ex.getMessage() : "Lead creation failed";
            event.setErrorMessage(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
            event.setProcessedAt(Instant.now());
            return leadIngestionEventRepository.save(event);
        }

        event.setLeadId(created.getId());
        event.setStatus(LeadIngestionEventStatus.PROCESSED);
        event.setFailureStage(null);
        event.setProcessedAt(Instant.now());
        event.setErrorCode(null);
        event.setErrorMessage(null);
        return leadIngestionEventRepository.save(event);
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

    private LeadIngestionEvent handleDuplicate(UUID tenantId, LeadIngestionEvent event,
            ValidatedLeadIngestionData validated, String causeMessage) {
        UUID existingLeadId = resolveDuplicateLeadId(tenantId, validated);
        event.setLeadId(existingLeadId);
        event.setStatus(LeadIngestionEventStatus.DUPLICATE);
        event.setFailureStage(LeadIngestionFailureStage.DEDUPLICATION);
        event.setErrorCode("DUPLICATE");
        String msg = causeMessage != null && !causeMessage.isBlank()
            ? causeMessage
            : "Duplicate lead detected; no new lead created";
        event.setErrorMessage(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
        event.setProcessedAt(Instant.now());
        log.info("Ingestion event {} marked DUPLICATE for tenant={} leadId={} cause={}",
            event.getId(), tenantId, existingLeadId, msg);
        return leadIngestionEventRepository.save(event);
    }

    private UUID resolveDuplicateLeadId(UUID tenantId, ValidatedLeadIngestionData validated) {
        if (validated == null) return null;
        if (validated.getEmail() != null && !validated.getEmail().isBlank()) {
            Optional<Lead> byEmail = leadRepository.findActiveLeadByEmailAndTenant(validated.getEmail(), tenantId);
            if (byEmail.isPresent()) return byEmail.get().getId();
        }
        if (validated.getPhone() != null && !validated.getPhone().isBlank()) {
            Optional<Lead> byPhone = leadRepository.findActiveLeadByPhoneAndTenant(validated.getPhone(), tenantId);
            if (byPhone.isPresent()) return byPhone.get().getId();
        }
        return null;
    }
}