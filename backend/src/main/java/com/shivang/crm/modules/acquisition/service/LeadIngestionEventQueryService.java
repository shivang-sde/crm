package com.shivang.crm.modules.acquisition.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.acquisition.dto.LeadIngestionEventDetailResponse;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionEventSummaryResponse;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionEventRepository;
import com.shivang.crm.modules.integration.webhook.HeaderSanitizer;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadIngestionEventQueryService {

    private final LeadIngestionConfigRepository leadIngestionConfigRepository;
    private final LeadIngestionEventRepository leadIngestionEventRepository;
    private final HeaderSanitizer headerSanitizer;

    @Transactional(readOnly = true)
    public Page<LeadIngestionEventSummaryResponse> listEvents(
            UUID tenantId,
            UUID configId,
            LeadIngestionEventStatus status,
            int page,
            int size) {
        ensureConfig(configId, tenantId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<LeadIngestionEvent> events = status == null
            ? leadIngestionEventRepository.findByTenantIdAndIngestionConfigIdAndDeletedFalse(tenantId, configId, pageable)
            : leadIngestionEventRepository.findByTenantIdAndIngestionConfigIdAndStatusAndDeletedFalse(tenantId, configId, status, pageable);

        return events.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public LeadIngestionEventDetailResponse getEventDetail(UUID tenantId, UUID configId, UUID eventId) {
        ensureConfig(configId, tenantId);

        LeadIngestionEvent event = leadIngestionEventRepository
            .findByIdAndTenantIdAndIngestionConfigIdAndDeletedFalse(eventId, tenantId, configId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Ingestion event not found"));

        return toDetail(event);
    }

    private void ensureConfig(UUID configId, UUID tenantId) {
        leadIngestionConfigRepository.findByIdAndTenantIdAndDeletedFalse(configId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Ingestion config not found"));
    }

    private LeadIngestionEventSummaryResponse toSummary(LeadIngestionEvent event) {
        return LeadIngestionEventSummaryResponse.builder()
            .id(event.getId())
            .ingestionConfigId(event.getIngestionConfigId())
            .status(event.getStatus())
            .externalEventId(event.getExternalEventId())
            .leadId(event.getLeadId())
            .errorCode(event.getErrorCode())
            .receivedAt(event.getReceivedAt())
            .processedAt(event.getProcessedAt())
            .build();
    }

    private LeadIngestionEventDetailResponse toDetail(LeadIngestionEvent event) {
        return LeadIngestionEventDetailResponse.builder()
            .id(event.getId())
            .ingestionConfigId(event.getIngestionConfigId())
            .externalEventId(event.getExternalEventId())
            .idempotencyKey(event.getIdempotencyKey())
            .status(event.getStatus())
            .leadId(event.getLeadId())
            .errorCode(event.getErrorCode())
            .errorMessage(event.getErrorMessage())
            .receivedAt(event.getReceivedAt())
            .processedAt(event.getProcessedAt())
            .rawPayload(event.getRawPayload())
            .headers(headerSanitizer.sanitizeStored(event.getHeaders()))
            .createdAt(event.getCreatedAt())
            .updatedAt(event.getUpdatedAt())
            .build();
    }
}
