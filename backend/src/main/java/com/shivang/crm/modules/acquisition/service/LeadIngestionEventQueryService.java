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
            .userMessage(deriveUserMessage(event))
            .retryable(isRetryable(event))
            .failureStage(event.getFailureStage())
            .attemptCount(event.getAttemptCount())
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
            .userMessage(deriveUserMessage(event))
            .technicalMessage(deriveTechnicalMessage(event))
            .retryable(isRetryable(event))
            .duplicateMatchType(deriveDuplicateMatchType(event))
            .failureStage(event.getFailureStage())
            .attemptCount(event.getAttemptCount())
            .receivedAt(event.getReceivedAt())
            .processedAt(event.getProcessedAt())
            .rawPayload(event.getRawPayload())
            .headers(headerSanitizer.sanitizeStored(event.getHeaders()))
            .createdAt(event.getCreatedAt())
            .updatedAt(event.getUpdatedAt())
            .build();
    }

    private String deriveUserMessage(LeadIngestionEvent event) {
        if (event == null) return null;
        // DUPLICATE is the business duplicate case
        if (event.getStatus() == LeadIngestionEventStatus.DUPLICATE) {
            String raw = event.getErrorMessage();
            if (raw != null && (raw.toLowerCase().contains("phone") || raw.toLowerCase().contains("email"))) {
                return raw;
            }
            // Fallback friendly with match type
            String match = deriveDuplicateMatchType(event);
            if ("PHONE".equals(match)) return "A lead with this phone number already exists.";
            if ("EMAIL".equals(match)) return "A lead with this email address already exists.";
            if ("BOTH".equals(match)) return "A lead with the same phone number and email already exists.";
            return raw != null && !raw.isBlank() ? raw : "Duplicate lead detected. A matching lead already exists.";
        }
        if (event.getStatus() == LeadIngestionEventStatus.PROCESSED) {
            return "Lead created successfully";
        }
        if (event.getStatus() == LeadIngestionEventStatus.REJECTED) {
            // Validation/mapping errors are already user-friendly; ensure not technical
            String raw = event.getErrorMessage();
            if (raw != null && raw.toLowerCase().contains("transaction")) {
                return "Lead information needs attention. Please check required fields.";
            }
            return raw;
        }
        if (event.getStatus() == LeadIngestionEventStatus.FAILED) {
            String raw = event.getErrorMessage();
            if (raw != null && (raw.toLowerCase().contains("transaction silently") || raw.toLowerCase().contains("rollback-only") || raw.toLowerCase().contains("unexpectedrollback"))) {
                return "We couldn't process this lead right now. Please try again or contact an administrator.";
            }
            // For failed with processing error, provide friendly fallback if raw looks technical
            if (raw == null || raw.isBlank() || raw.toLowerCase().contains("dataintegrity") || raw.toLowerCase().contains("could not execute")) {
                return "We couldn't process this lead right now. Please try again or contact an administrator.";
            }
            return raw;
        }
        return event.getErrorMessage();
    }

    private String deriveTechnicalMessage(LeadIngestionEvent event) {
        if (event == null || event.getErrorMessage() == null) return null;
        String raw = event.getErrorMessage();
        String lower = raw.toLowerCase();
        // Only surface technical if it was originally technical; after fix most are user-friendly, so return null to avoid duplication
        if (lower.contains("transaction silently") || lower.contains("rollback-only") || lower.contains("dataintegrity") || lower.contains("constraint") || lower.contains("exception")) {
            return raw;
        }
        // For system failures, technical is same as errorCode + raw for debugging, but keep null for business cases to keep primary clean
        if (event.getStatus() == LeadIngestionEventStatus.FAILED && "PROCESSING_ERROR".equals(event.getErrorCode())) {
            return raw;
        }
        return null;
    }

    private Boolean isRetryable(LeadIngestionEvent event) {
        if (event == null || event.getStatus() == null) return null;
        return switch (event.getStatus()) {
            case FAILED, REJECTED -> true;
            case DUPLICATE, PROCESSED -> false;
            case RECEIVED, PROCESSING -> null;
        };
    }

    private String deriveDuplicateMatchType(LeadIngestionEvent event) {
        if (event == null || event.getStatus() != LeadIngestionEventStatus.DUPLICATE) return null;
        String msg = event.getErrorMessage() != null ? event.getErrorMessage().toLowerCase() : "";
        boolean hasPhone = msg.contains("phone");
        boolean hasEmail = msg.contains("email");
        // Also check rawPayload if message not specific
        if (!hasPhone && !hasEmail && event.getRawPayload() != null) {
            Object phone = event.getRawPayload().get("phone");
            Object email = event.getRawPayload().get("email");
            // fallback via validated data not available here; use errorMessage heuristic above
            // If both present in payload, assume both
            hasPhone = phone != null && !String.valueOf(phone).isBlank();
            hasEmail = email != null && !String.valueOf(email).isBlank();
            // But we prefer message-based; if both true, return BOTH
        }
        if (hasPhone && hasEmail) return "BOTH";
        if (hasPhone) return "PHONE";
        if (hasEmail) return "EMAIL";
        return "UNKNOWN";
    }
}
