package com.shivang.crm.modules.deal.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.deal.dto.DealStageCreateRequest;
import com.shivang.crm.modules.deal.dto.DealStageResponse;
import com.shivang.crm.modules.deal.dto.DealStageUpdateRequest;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.ForecastCategory;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.mapper.DealStageMapper;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.deal.repository.DealStageRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DealStageService {

    private final DealStageRepository dealStageRepository;
    private final DealRepository dealRepository;
    private final DealStageMapper dealStageMapper;

    /**
     * Create a new deal stage
     */
    public DealStageResponse createDealStage(UUID tenantId, UUID userId, DealStageCreateRequest request) {
        log.info("Creating deal stage for tenant: {}", tenantId);

        // Check for duplicate stage name
        dealStageRepository.findByTenantIdAndName(tenantId, request.getName())
                .ifPresent(existing -> {
                    throw new BusinessException("DUPLICATE", "A stage with this name already exists for this tenant");
                });

        DealStage stage = dealStageMapper.toEntity(request);
        stage.setTenantId(tenantId);
        normalizeLifecycleDefaults(stage);

        if (Boolean.TRUE.equals(stage.getIsDefault())
        && stage.getRecordCategory() != RecordCategory.OPEN) {
    throw new BusinessException(
        "INVALID_DEFAULT_STAGE",
        "Default stage must be an OPEN stage"
    );
}
 if (Boolean.TRUE.equals(stage.getIsDefault())) {
    ensureSingleDefaultStage(tenantId, null);
}

        DealStage savedStage = dealStageRepository.save(stage);
        log.info("Deal stage created with ID: {}", savedStage.getId());

        return dealStageMapper.toResponse(savedStage);
    }

    /**
     * Get deal stage by ID
     */
    @Transactional(readOnly = true)
    public DealStageResponse getDealStageById(UUID id, UUID tenantId) {
        log.info("Fetching deal stage: {} for tenant: {}", id, tenantId);

        DealStage stage = dealStageRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Deal stage not found"));

        return dealStageMapper.toResponse(stage);
    }

    /**
     * List all deal stages for a tenant
     */
    @Transactional(readOnly = true)
    public List<DealStageResponse> listDealStages(UUID tenantId) {
        log.info("Listing deal stages for tenant: {}", tenantId);

        List<DealStage> stages = dealStageRepository.findByTenantIdOrderByDisplayOrder(tenantId);
        return dealStageMapper.toResponseList(stages);
    }

    /**
     * Update deal stage
     */
    public DealStageResponse updateDealStage(UUID id, UUID tenantId, UUID userId, DealStageUpdateRequest request) {
        log.info("Updating deal stage: {} for tenant: {}", id, tenantId);

        DealStage stage = dealStageRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Deal stage not found"));

        // Check for duplicate name if name is being changed
        if (request.getName() != null && !request.getName().equals(stage.getName())) {
            dealStageRepository.findByTenantIdAndName(tenantId, request.getName())
                    .ifPresent(existing -> {
                        throw new BusinessException("DUPLICATE", "A stage with this name already exists");
                    });
        }

        dealStageMapper.updateEntity(request, stage);
        normalizeLifecycleDefaults(stage);
        if (Boolean.TRUE.equals(stage.getIsDefault())
        && stage.getRecordCategory() != RecordCategory.OPEN) {

    throw new BusinessException(
        "INVALID_DEFAULT_STAGE",
        "Default stage must be an OPEN stage"
    );
}

            if (Boolean.TRUE.equals(stage.getIsDefault())) {
    ensureSingleDefaultStage(tenantId, stage.getId());
}

        DealStage updatedStage = dealStageRepository.save(stage);

        log.info("Deal stage updated: {}", id);
        return dealStageMapper.toResponse(updatedStage);
    }

    /**
     * Delete deal stage (soft delete can be implemented later)
     */
    public void deleteDealStage(UUID id, UUID tenantId, UUID userId) {
        log.info("Deleting deal stage: {} for tenant: {}", id, tenantId);

        DealStage stage = dealStageRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Deal stage not found"));

        if (Boolean.TRUE.equals(stage.getIsDefault())) {
    throw new BusinessException(
        "DEFAULT_STAGE_DELETE",
        "Default stage cannot be deleted"
    );
}

        // Check if any deals are using this stage
        Integer dealCount = dealRepository.countByStageId(tenantId, id);
        if (dealCount > 0) {
            throw new BusinessException("IN_USE", "Cannot delete stage that has deals associated with it");
        }

        dealStageRepository.delete(stage);
        log.info("Deal stage deleted: {}", id);
    }

    /**
     * Get default stage for a tenant
     */
    @Transactional(readOnly = true)
    public DealStageResponse getDefaultStage(UUID tenantId) {
        log.info("Fetching default deal stage for tenant: {}", tenantId);

        DealStage stage = dealStageRepository.findByTenantIdAndIsDefault(tenantId, true)
                .orElseThrow(() -> new RuntimeException("No default stage found"));

        return dealStageMapper.toResponse(stage);
    }

    private void ensureSingleDefaultStage(UUID tenantId, UUID currentStageId) {

    dealStageRepository.findByTenantIdAndIsDefault(tenantId, true)
        .ifPresent(existing -> {

            if (currentStageId != null &&
                existing.getId().equals(currentStageId)) {
                return;
            }

            existing.setIsDefault(false);
            dealStageRepository.save(existing);
        });
}

    private void  normalizeLifecycleDefaults(DealStage stage) {
        if (stage.getRecordCategory() == null) {
    stage.setRecordCategory(RecordCategory.OPEN);
}

        switch (stage.getRecordCategory()) {
            case CLOSED_WON -> {
                stage.setIsClosed(true);
                stage.setDefaultProbability(100);
                stage.setDefaultForecastCategory(ForecastCategory.CLOSED);
            }
            case CLOSED_LOST -> {
                stage.setIsClosed(true);
                stage.setDefaultProbability(0);
                stage.setDefaultForecastCategory(ForecastCategory.CLOSED);
            }
            case OPEN -> {
                stage.setIsClosed(false);
                if (stage.getDefaultProbability() == null) {
                    stage.setDefaultProbability(0);
                }
                if (stage.getDefaultForecastCategory() == null || ForecastCategory.CLOSED.equals(stage.getDefaultForecastCategory())) {
                    stage.setDefaultForecastCategory(ForecastCategory.PIPELINE);
                }
            }
        }

        Integer probability = stage.getDefaultProbability();

if (probability == null || probability < 0 || probability > 100) {
    throw new BusinessException(
        "INVALID_PROBABILITY",
        "Default probability must be between 0 and 100"
    );
}
    }
}
