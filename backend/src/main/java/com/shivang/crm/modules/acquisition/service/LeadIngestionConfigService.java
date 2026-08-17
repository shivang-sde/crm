package com.shivang.crm.modules.acquisition.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.acquisition.config.LeadIngestionConfig;
import com.shivang.crm.modules.acquisition.config.LeadIngestionTransportType;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionConfigCreateRequest;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionConfigResponse;
import com.shivang.crm.modules.acquisition.dto.LeadIngestionConfigUpdateRequest;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LeadIngestionConfigService {

    private static final String INBOUND_PATH_PREFIX = "/api/v1/public/acquisition/";
    private static final String PUBLIC_KEY_PREFIX = "acq_";
    private static final int RANDOM_BYTES_LENGTH = 18;
    private static final int MAX_KEY_GENERATION_ATTEMPTS = 10;

    private final LeadIngestionConfigRepository leadIngestionConfigRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public LeadIngestionConfigResponse create(UUID tenantId, LeadIngestionConfigCreateRequest request) {
        log.info("Creating lead ingestion config for tenant: {}", tenantId);

        validateNameIfProvided(request.getName());

        LeadIngestionConfig entity = LeadIngestionConfig.builder()
            .tenantId(tenantId)
            .name(request.getName().trim())
            .transportType(request.getTransportType())
            .active(request.getActive() != null ? request.getActive() : Boolean.TRUE)
            .settings(request.getSettings())
            .build();

        ensureWebhookPublicKey(entity);

        LeadIngestionConfig saved = leadIngestionConfigRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<LeadIngestionConfigResponse> list(UUID tenantId) {
        List<LeadIngestionConfig> configs =
            leadIngestionConfigRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId);

        return configs.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LeadIngestionConfigResponse getById(UUID tenantId, UUID id) {
        LeadIngestionConfig config = findByIdAndTenant(tenantId, id);
        return toResponse(config);
    }

    public LeadIngestionConfigResponse update(UUID tenantId, UUID id, LeadIngestionConfigUpdateRequest request) {
        log.info("Updating lead ingestion config: {} for tenant: {}", id, tenantId);

        LeadIngestionConfig config = findByIdAndTenant(tenantId, id);
        LeadIngestionTransportType previousTransportType = config.getTransportType();

        if (request.getName() != null) {
            validateNameIfProvided(request.getName());
            config.setName(request.getName().trim());
        }

        if (request.getTransportType() != null) {
            config.setTransportType(request.getTransportType());
        }

        if (request.getActive() != null) {
            config.setActive(request.getActive());
        }

        if (request.getSettings() != null) {
            config.setSettings(request.getSettings());
        }

        if (previousTransportType != LeadIngestionTransportType.WEBHOOK
            && config.getTransportType() == LeadIngestionTransportType.WEBHOOK
            && (config.getPublicKey() == null || config.getPublicKey().isBlank())) {
            config.setPublicKey(generateUniquePublicKey());
        }

        if (previousTransportType == LeadIngestionTransportType.WEBHOOK
            && config.getTransportType() == LeadIngestionTransportType.WEBHOOK
            && (config.getPublicKey() == null || config.getPublicKey().isBlank())) {
            config.setPublicKey(generateUniquePublicKey());
        }

        LeadIngestionConfig updated = leadIngestionConfigRepository.save(config);
        return toResponse(updated);
    }

    public void softDelete(UUID tenantId, UUID id, UUID userId) {
        log.info("Soft deleting lead ingestion config: {} for tenant: {}", id, tenantId);

        LeadIngestionConfig config = findByIdAndTenant(tenantId, id);
        config.softDelete(userId);
        config.setActive(false);
        leadIngestionConfigRepository.save(config);
    }

    private LeadIngestionConfig findByIdAndTenant(UUID tenantId, UUID id) {
        return leadIngestionConfigRepository
            .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Lead ingestion configuration not found"));
    }

    private void validateNameIfProvided(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Configuration name is required");
        }
    }

    private void ensureWebhookPublicKey(LeadIngestionConfig config) {
        if (config.getTransportType() == LeadIngestionTransportType.WEBHOOK
            && (config.getPublicKey() == null || config.getPublicKey().isBlank())) {
            config.setPublicKey(generateUniquePublicKey());
        }
    }

    private String generateUniquePublicKey() {
        for (int attempt = 0; attempt < MAX_KEY_GENERATION_ATTEMPTS; attempt++) {
            byte[] randomBytes = new byte[RANDOM_BYTES_LENGTH];
            secureRandom.nextBytes(randomBytes);

            String candidate = PUBLIC_KEY_PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

            if (!leadIngestionConfigRepository.existsByPublicKey(candidate)) {
                return candidate;
            }
        }

        throw new BusinessException("KEY_GENERATION_FAILED", "Unable to generate a unique public key");
    }

    private LeadIngestionConfigResponse toResponse(LeadIngestionConfig config) {
        String inboundPath = null;
        if (config.getPublicKey() != null && !config.getPublicKey().isBlank()) {
            inboundPath = INBOUND_PATH_PREFIX + config.getPublicKey();
        }

        return LeadIngestionConfigResponse.builder()
            .id(config.getId())
            .name(config.getName())
            .transportType(config.getTransportType())
            .publicKey(config.getPublicKey())
            .inboundPath(inboundPath)
            .active(config.getActive())
            .settings(config.getSettings())
            .createdAt(config.getCreatedAt())
            .updatedAt(config.getUpdatedAt())
            .build();
    }
}