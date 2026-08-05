package com.shivang.crm.modules.catalog.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.catalog.dto.OfferingCreateRequest;
import com.shivang.crm.modules.catalog.dto.OfferingResponse;
import com.shivang.crm.modules.catalog.dto.OfferingUpdateRequest;
import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.catalog.enums.BillingInterval;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;
import com.shivang.crm.modules.catalog.mapper.OfferingMapper;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.catalog.repository.OfferingSpecifications;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OfferingService {

    private final OfferingRepository offeringRepository;
    private final OfferingMapper offeringMapper;
    private final UserRepository userRepository;
    private final ActivityService activityService;

    public OfferingResponse createOffering(UUID tenantId, UUID userId, OfferingCreateRequest request) {
        log.info("Creating offering for tenant {}", tenantId);

        validateRequest(request);
        normalizeRequest(request);

        if (offeringRepository.existsByTenantIdAndCodeIgnoreCaseAndDeletedFalse(tenantId, request.getCode())) {
            throw new BusinessException("DUPLICATE_OFFERING_CODE", "An offering with the same code already exists");
        }

        Offering offering = offeringMapper.toEntity(request);
        offering.setTenantId(tenantId);
        offering.setCreatedBy(userId);
        offering.setUpdatedBy(userId);
        offering.setOwnerId(request.getOwnerUserId() != null ? request.getOwnerUserId() : userId);
        offering.setActive(Boolean.TRUE.equals(request.getActive()));
        offering.setRenewable(Boolean.TRUE.equals(request.getRenewable()));

        validateOwner(tenantId, offering.getOwnerId());

        Offering saved = offeringRepository.save(offering);
        activityService.logActivity(tenantId, saved.getId(), "OFFERING", "OFFERING_CREATED",
                "Offering created", userId, Map.of("code", saved.getCode()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OfferingResponse getOfferingById(UUID id, UUID tenantId) {
        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Offering not found"));
        return toResponse(offering);
    }

    @Transactional(readOnly = true)
    public Page<OfferingResponse> listOfferings(UUID tenantId, String search, OfferingType offeringType,
            BillingType billingType, Boolean active, UUID ownerUserId, int page, int size) {
        Specification<Offering> spec = OfferingSpecifications.buildSpecification(
                tenantId, search, offeringType, billingType, active, ownerUserId);
        Pageable pageable = PageRequest.of(page, size);
        return offeringRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public OfferingResponse updateOffering(UUID id, UUID tenantId, UUID userId, OfferingUpdateRequest request) {
        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Offering not found"));

        if (request.getCode() != null && !request.getCode().trim().equalsIgnoreCase(offering.getCode())) {
            normalizeCode(request);
            if (offeringRepository.existsByTenantIdAndCodeIgnoreCaseAndIdNotAndDeletedFalse(tenantId, request.getCode(), id)) {
                throw new BusinessException("DUPLICATE_OFFERING_CODE", "An offering with the same code already exists");
            }
        }

        validateUpdateRequest(request, offering);
        normalizeUpdateRequest(request);
        offeringMapper.updateEntity(request, offering);

        if (request.getOwnerUserId() != null) {
            validateOwner(tenantId, request.getOwnerUserId());
            offering.setOwnerId(request.getOwnerUserId());
        }
        if (request.getCode() != null) {
            offering.setCode(request.getCode().trim().toUpperCase());
        }
        if (request.getCurrencyCode() != null) {
            offering.setCurrencyCode(request.getCurrencyCode().trim().toUpperCase());
        }
        if (request.getActive() != null) {
            offering.setActive(request.getActive());
        }
        if (request.getRenewable() != null) {
            offering.setRenewable(request.getRenewable());
        }
        offering.setUpdatedBy(userId);

        Offering updated = offeringRepository.save(offering);
        activityService.logActivity(tenantId, updated.getId(), "OFFERING", "OFFERING_UPDATED",
                "Offering updated", userId, Map.of("code", updated.getCode()));
        return toResponse(updated);
    }

    public void deactivateOffering(UUID id, UUID tenantId, UUID userId) {
        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Offering not found"));
        offering.setActive(false);
        offering.setUpdatedBy(userId);
        offeringRepository.save(offering);
        activityService.logActivity(tenantId, offering.getId(), "OFFERING", "OFFERING_DEACTIVATED",
                "Offering deactivated", userId, Map.of("code", offering.getCode()));
    }

    public void activateOffering(UUID id, UUID tenantId, UUID userId) {
        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Offering not found"));
        offering.setActive(true);
        offering.setUpdatedBy(userId);
        offeringRepository.save(offering);
        activityService.logActivity(tenantId, offering.getId(), "OFFERING", "OFFERING_ACTIVATED",
                "Offering activated", userId, Map.of("code", offering.getCode()));
    }

    public void deleteOffering(UUID id, UUID tenantId, UUID userId) {
        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Offering not found"));
        offering.softDelete(userId);
        offering.setUpdatedBy(userId);
        offeringRepository.save(offering);
        activityService.logActivity(tenantId, offering.getId(), "OFFERING", "OFFERING_DELETED",
                "Offering deleted", userId, Map.of("code", offering.getCode()));
    }

    private void validateRequest(OfferingCreateRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("INVALID_OFFERING", "Offering name is required");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new BusinessException("INVALID_OFFERING", "Offering code is required");
        }
        if (request.getOfferingType() == null) {
            throw new BusinessException("INVALID_OFFERING", "Offering type is required");
        }
        if (request.getBillingType() == null) {
            throw new BusinessException("INVALID_OFFERING", "Billing type is required");
        }
        if (request.getDefaultPrice() != null && request.getDefaultPrice().signum() < 0) {
            throw new BusinessException("INVALID_OFFERING", "Default price must be zero or positive");
        }
        if (request.getDefaultTermDays() != null && request.getDefaultTermDays() <= 0) {
            throw new BusinessException("INVALID_OFFERING", "Default term days must be positive when provided");
        }
        if (request.getCurrencyCode() != null && !request.getCurrencyCode().isBlank() && request.getCurrencyCode().trim().length() != 3) {
            throw new BusinessException("INVALID_OFFERING", "Currency code must be three characters when provided");
        }
        if (request.getBillingType() == BillingType.RECURRING && request.getBillingInterval() == null) {
            throw new BusinessException("INVALID_OFFERING", "Billing interval is required for recurring offerings");
        }
        if (request.getBillingType() != BillingType.RECURRING && request.getBillingInterval() != null) {
            throw new BusinessException("INVALID_OFFERING", "Billing interval must be null for non-recurring offerings");
        }
    }

    private void validateUpdateRequest(OfferingUpdateRequest request, Offering existing) {
        if (request.getName() != null && request.getName().isBlank()) {
            throw new BusinessException("INVALID_OFFERING", "Offering name is required");
        }
        if (request.getCode() != null && request.getCode().isBlank()) {
            throw new BusinessException("INVALID_OFFERING", "Offering code is required");
        }
        if (request.getOfferingType() != null && request.getOfferingType() == null) {
            throw new BusinessException("INVALID_OFFERING", "Offering type is required");
        }
        if (request.getBillingType() != null && request.getBillingType() == null) {
            throw new BusinessException("INVALID_OFFERING", "Billing type is required");
        }
        if (request.getDefaultPrice() != null && request.getDefaultPrice().signum() < 0) {
            throw new BusinessException("INVALID_OFFERING", "Default price must be zero or positive");
        }
        if (request.getDefaultTermDays() != null && request.getDefaultTermDays() <= 0) {
            throw new BusinessException("INVALID_OFFERING", "Default term days must be positive when provided");
        }
        if (request.getCurrencyCode() != null && !request.getCurrencyCode().isBlank() && request.getCurrencyCode().trim().length() != 3) {
            throw new BusinessException("INVALID_OFFERING", "Currency code must be three characters when provided");
        }
        BillingType resolvedBillingType = request.getBillingType() != null ? request.getBillingType() : existing.getBillingType();
        BillingInterval resolvedInterval = request.getBillingInterval() != null ? request.getBillingInterval() : existing.getBillingInterval();
        if (resolvedBillingType == BillingType.RECURRING && resolvedInterval == null) {
            throw new BusinessException("INVALID_OFFERING", "Billing interval is required for recurring offerings");
        }
        if (resolvedBillingType != BillingType.RECURRING && resolvedInterval != null) {
            throw new BusinessException("INVALID_OFFERING", "Billing interval must be null for non-recurring offerings");
        }
    }

    private void normalizeRequest(OfferingCreateRequest request) {
        if (request.getCode() != null) {
            request.setCode(request.getCode().trim().toUpperCase());
        }
        if (request.getCurrencyCode() != null) {
            request.setCurrencyCode(request.getCurrencyCode().trim().toUpperCase());
        }
        if (request.getBillingType() != BillingType.RECURRING) {
            request.setBillingInterval(null);
        }
    }

    private void normalizeUpdateRequest(OfferingUpdateRequest request) {
        if (request.getCode() != null) {
            request.setCode(request.getCode().trim().toUpperCase());
        }
        if (request.getCurrencyCode() != null) {
            request.setCurrencyCode(request.getCurrencyCode().trim().toUpperCase());
        }
        if (request.getBillingType() != null && request.getBillingType() != BillingType.RECURRING) {
            request.setBillingInterval(null);
        }
    }

    private void normalizeCode(OfferingUpdateRequest request) {
        if (request.getCode() != null) {
            request.setCode(request.getCode().trim().toUpperCase());
        }
    }

    private void validateOwner(UUID tenantId, UUID ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        if (userRepository.findByIdAndTenantIdAndDeletedFalse(ownerUserId, tenantId).isEmpty()) {
            throw new BusinessException("INVALID_OWNER", "Owner must belong to the same tenant");
        }
    }

    private OfferingResponse toResponse(Offering offering) {
        OfferingResponse response = offeringMapper.toResponse(offering);
        response.setOwnerName(offering.getOwnerId() != null ? userRepository.findById(offering.getOwnerId())
                .map(user -> user.getDisplayName())
                .orElse(null) : null);
        response.setTenantId(offering.getTenantId());
        return response;
    }
}
