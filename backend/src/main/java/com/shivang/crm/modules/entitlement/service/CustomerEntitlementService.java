package com.shivang.crm.modules.entitlement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealLineItem;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.entitlement.dto.CustomerEntitlementResponse;
import com.shivang.crm.modules.entitlement.dto.CustomerEntitlementUpdateRequest;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.mapper.CustomerEntitlementMapper;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementSpecifications;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomerEntitlementService {

    private final CustomerEntitlementRepository entitlementRepository;
    private final CustomerEntitlementMapper entitlementMapper;
    private final DealRepository dealRepository;
    private final DealLineItemRepository dealLineItemRepository;
    private final ActivityService activityService;

    public CustomerEntitlementResponse getById(UUID id, UUID tenantId) {
        CustomerEntitlement entitlement = entitlementRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Entitlement not found"));
        return toResponse(entitlement);
    }

    @Transactional(readOnly = true)
    public Page<CustomerEntitlementResponse> list(
            UUID tenantId,
            UUID accountId,
            UUID contactId,
            UUID offeringId,
            EntitlementStatus status,
            UUID ownerUserId,
            Boolean renewable,
            LocalDate endDateFrom,
            LocalDate endDateTo,
            String search,
            int page,
            int size) {
        Specification<CustomerEntitlement> spec = CustomerEntitlementSpecifications.buildSpecification(
                tenantId, accountId, contactId, offeringId, status, ownerUserId, renewable, endDateFrom, endDateTo, search);
        Pageable pageable = PageRequest.of(page, size);
        return entitlementRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public CustomerEntitlementResponse update(UUID id, UUID tenantId, UUID userId, CustomerEntitlementUpdateRequest request) {
        CustomerEntitlement entitlement = entitlementRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Entitlement not found"));

        entitlementMapper.updateEntity(request, entitlement);
        if (request.getOwnerUserId() != null) {
            entitlement.setOwnerId(request.getOwnerUserId());
        }
        entitlement.setUpdatedBy(userId);
        recalculateRenewalDueDate(entitlement);
        CustomerEntitlement saved = entitlementRepository.save(entitlement);
        activityService.logActivity(tenantId, saved.getId(), "ENTITLEMENT", "ENTITLEMENT_UPDATED",
                "Entitlement updated", userId, buildMetadata(saved));
        return toResponse(saved);
    }

    public void activate(UUID id, UUID tenantId, UUID userId) {
        CustomerEntitlement entitlement = entitlementRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Entitlement not found"));
        if (entitlement.getStatus() != EntitlementStatus.PENDING && entitlement.getStatus() != EntitlementStatus.SUSPENDED) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Only pending or suspended entitlements can be activated");
        }
        entitlement.setStatus(EntitlementStatus.ACTIVE);
        entitlement.setUpdatedBy(userId);
        entitlementRepository.save(entitlement);
        activityService.logActivity(tenantId, entitlement.getId(), "ENTITLEMENT", "ENTITLEMENT_ACTIVATED",
                "Entitlement activated", userId, buildMetadata(entitlement));
    }

    public void suspend(UUID id, UUID tenantId, UUID userId) {
        CustomerEntitlement entitlement = entitlementRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Entitlement not found"));
        if (entitlement.getStatus() != EntitlementStatus.ACTIVE) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Only active entitlements can be suspended");
        }
        entitlement.setStatus(EntitlementStatus.SUSPENDED);
        entitlement.setUpdatedBy(userId);
        entitlementRepository.save(entitlement);
        activityService.logActivity(tenantId, entitlement.getId(), "ENTITLEMENT", "ENTITLEMENT_SUSPENDED",
                "Entitlement suspended", userId, buildMetadata(entitlement));
    }

    public void terminate(UUID id, UUID tenantId, UUID userId) {
        CustomerEntitlement entitlement = entitlementRepository.findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Entitlement not found"));
        if (entitlement.getStatus() != EntitlementStatus.ACTIVE
                && entitlement.getStatus() != EntitlementStatus.PENDING
                && entitlement.getStatus() != EntitlementStatus.SUSPENDED) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Only active, pending, or suspended entitlements can be terminated");
        }
        entitlement.setStatus(EntitlementStatus.TERMINATED);
        entitlement.setUpdatedBy(userId);
        entitlementRepository.save(entitlement);
        activityService.logActivity(tenantId, entitlement.getId(), "ENTITLEMENT", "ENTITLEMENT_TERMINATED",
                "Entitlement terminated", userId, buildMetadata(entitlement));
    }

    public void provisionFromWonDeal(UUID tenantId, UUID dealId, UUID userId) {
        Deal deal = dealRepository.findByIdAndTenantId(dealId, tenantId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Deal not found"));
        if (!deal.isWon()) {
            log.debug("Skipping entitlement provisioning for non-won deal {}", dealId);
            return;
        }
        if (deal.getAccountId() == null && deal.getContactId() == null) {
            throw new BusinessException("ENTITLEMENT_NO_CUSTOMER", "A won deal must have an account or contact before provisioning entitlements");
        }

        List<DealLineItem> activeLineItems = dealLineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId);
        if (activeLineItems.isEmpty()) {
            log.info("No active deal line items for won deal {} - skipping entitlement provisioning", dealId);
            return;
        }

        for (DealLineItem lineItem : activeLineItems) {
            if (entitlementRepository.existsByTenantIdAndDealLineItemIdAndDeletedFalse(tenantId, lineItem.getId())) {
                continue;
            }
            CustomerEntitlement entitlement = buildEntitlement(deal, lineItem, tenantId, userId);
            CustomerEntitlement saved = entitlementRepository.save(entitlement);
            activityService.logActivity(tenantId, saved.getId(), "ENTITLEMENT", "ENTITLEMENT_CREATED",
                    "Entitlement created from won deal", userId, buildMetadata(saved));
        }
    }

    private CustomerEntitlement buildEntitlement(Deal deal, DealLineItem lineItem, UUID tenantId, UUID userId) {
        CustomerEntitlement entitlement = new CustomerEntitlement();
        entitlement.setTenantId(tenantId);
        entitlement.setOwnerId(deal.getOwnerId());
        entitlement.setCreatedBy(userId);
        entitlement.setUpdatedBy(userId);
        entitlement.setAccountId(deal.getAccountId());
        entitlement.setContactId(deal.getContactId());
        entitlement.setOfferingId(lineItem.getOfferingId());
        entitlement.setDealId(deal.getId());
        entitlement.setDealLineItemId(lineItem.getId());
        entitlement.setName(lineItem.getItemName());
        entitlement.setCode(lineItem.getItemCode());
        entitlement.setDescription(lineItem.getDescription());
        entitlement.setQuantity(lineItem.getQuantity());
        entitlement.setAgreedPrice(lineItem.getUnitPrice());
        entitlement.setCurrencyCode(null);
        entitlement.setRenewable(lineItem.getRenewable());
        entitlement.setAutoRenew(Boolean.FALSE);
        entitlement.setRenewalNoticeDays(lineItem.getRenewalNoticeDays());
        entitlement.setStartDate(lineItem.getServiceStartDate());
        entitlement.setEndDate(lineItem.getServiceEndDate());
        entitlement.setStatus(calculateInitialStatus(lineItem.getServiceStartDate(), lineItem.getServiceEndDate()));
        recalculateRenewalDueDate(entitlement);
        entitlement.setCustomData(lineItem.getCustomData() == null ? new HashMap<>() : new HashMap<>(lineItem.getCustomData()));
        return entitlement;
    }

    private EntitlementStatus calculateInitialStatus(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        if (startDate != null && startDate.isAfter(today)) {
            return EntitlementStatus.PENDING;
        }
        if (endDate != null && endDate.isBefore(today)) {
            return EntitlementStatus.EXPIRED;
        }
        return EntitlementStatus.ACTIVE;
    }

    private void recalculateRenewalDueDate(CustomerEntitlement entitlement) {
        if (Boolean.TRUE.equals(entitlement.getRenewable())
                && entitlement.getEndDate() != null
                && entitlement.getRenewalNoticeDays() != null) {
            entitlement.setRenewalDueDate(entitlement.getEndDate().minusDays(entitlement.getRenewalNoticeDays()));
        } else {
            entitlement.setRenewalDueDate(null);
        }
    }

    private CustomerEntitlementResponse toResponse(CustomerEntitlement entitlement) {
        return CustomerEntitlementResponse.builder()
                .id(entitlement.getId())
                .tenantId(entitlement.getTenantId())
                .accountId(entitlement.getAccountId())
                .contactId(entitlement.getContactId())
                .offeringId(entitlement.getOfferingId())
                .dealId(entitlement.getDealId())
                .dealLineItemId(entitlement.getDealLineItemId())
                .name(entitlement.getName())
                .code(entitlement.getCode())
                .description(entitlement.getDescription())
                .status(entitlement.getStatus())
                .startDate(entitlement.getStartDate())
                .endDate(entitlement.getEndDate())
                .quantity(entitlement.getQuantity())
                .agreedPrice(entitlement.getAgreedPrice())
                .currencyCode(entitlement.getCurrencyCode())
                .renewable(entitlement.getRenewable())
                .autoRenew(entitlement.getAutoRenew())
                .renewalNoticeDays(entitlement.getRenewalNoticeDays())
                .renewalDueDate(entitlement.getRenewalDueDate())
                .renewedFromEntitlementId(entitlement.getRenewedFromEntitlementId())
                .renewedToEntitlementId(entitlement.getRenewedToEntitlementId())
                .renewalDealId(entitlement.getRenewalDealId())
                .customData(entitlement.getCustomData())
                .ownerUserId(entitlement.getOwnerId())
                .createdAt(entitlement.getCreatedAt())
                .updatedAt(entitlement.getUpdatedAt())
                .build();
    }

    private Map<String, Object> buildMetadata(CustomerEntitlement entitlement) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entitlementId", entitlement.getId());
        metadata.put("offeringId", entitlement.getOfferingId());
        metadata.put("dealId", entitlement.getDealId());
        metadata.put("dealLineItemId", entitlement.getDealLineItemId());
        metadata.put("name", entitlement.getName());
        metadata.put("status", entitlement.getStatus());
        metadata.put("startDate", entitlement.getStartDate());
        metadata.put("endDate", entitlement.getEndDate());
        return metadata;
    }
}
