package com.shivang.crm.modules.deal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.catalog.repository.OfferingRepository;
import com.shivang.crm.modules.deal.dto.DealLineItemCreateRequest;
import com.shivang.crm.modules.deal.dto.DealLineItemResponse;
import com.shivang.crm.modules.deal.dto.DealLineItemUpdateRequest;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealLineItem;
import com.shivang.crm.modules.deal.mapper.DealLineItemMapper;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DealLineItemService {

    private static final int QUANTITY_SCALE = 4;
    private static final int MONEY_SCALE = 2;

    private final DealLineItemRepository dealLineItemRepository;
    private final DealRepository dealRepository;
    private final OfferingRepository offeringRepository;
    private final DealLineItemMapper dealLineItemMapper;
    private final ActivityService activityService;
    private final com.shivang.crm.modules.rbac.service.RecordScopeGuard recordScopeGuard;

    @Transactional(readOnly = true)
    public List<DealLineItemResponse> listLineItems(UUID tenantId, UUID dealId) {
        ensureDealExists(tenantId, dealId);
        return dealLineItemMapper.toResponseList(
                dealLineItemRepository.findByTenantIdAndDealIdAndDeletedFalseOrderByCreatedAtAsc(tenantId, dealId));
    }

    @Transactional(readOnly = true)
    public DealLineItemResponse getLineItem(UUID tenantId, UUID dealId, UUID lineItemId) {
        ensureDealExists(tenantId, dealId);
        DealLineItem lineItem = dealLineItemRepository.findByIdAndTenantIdAndDealIdAndDeletedFalse(lineItemId, tenantId, dealId)
                .orElseThrow(() -> new BusinessException("DEAL_LINE_ITEM_NOT_FOUND", "Deal line item not found"));
        return dealLineItemMapper.toResponse(lineItem);
    }

    public DealLineItemResponse createLineItem(UUID tenantId, UUID dealId, UUID userId, DealLineItemCreateRequest request) {
        // RBAC-7: parent deal must be within the caller's deal:write scope.
        ensureDealAccessible(tenantId, dealId, "write");
        validateCreateRequest(request);

        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(request.getOfferingId(), tenantId)
                .orElseThrow(() -> new BusinessException("OFFERING_NOT_FOUND", "Offering not found"));
        if (!Boolean.TRUE.equals(offering.getActive())) {
            throw new BusinessException("OFFERING_INACTIVE", "Offering is not active");
        }

        DealLineItem lineItem = new DealLineItem();
        lineItem.setTenantId(tenantId);
        lineItem.setDealId(dealId);
        lineItem.setOfferingId(offering.getId());
        lineItem.setItemName(offering.getName());
        lineItem.setItemCode(offering.getCode());
        lineItem.setDescription(request.getDescription() != null ? request.getDescription() : offering.getDescription());
        lineItem.setQuantity(normalizeQuantity(request.getQuantity()));
        lineItem.setUnitPrice(normalizeMoney(request.getUnitPrice() != null ? request.getUnitPrice() : offering.getDefaultPrice()));
        lineItem.setDiscountAmount(normalizeMoney(request.getDiscountAmount()));
        lineItem.setTaxAmount(normalizeMoney(request.getTaxAmount()));
        lineItem.setServiceStartDate(request.getServiceStartDate());
        lineItem.setServiceEndDate(resolveServiceEndDate(request.getServiceStartDate(), request.getServiceEndDate(), offering));
        lineItem.setRenewable(request.getRenewable() != null ? request.getRenewable() : offering.getRenewable());
        lineItem.setRenewalNoticeDays(request.getRenewalNoticeDays());
        lineItem.setCustomData(request.getCustomData());
        lineItem.setUpdatedBy(userId);
        lineItem.setDeleted(false);

        validateLineItem(lineItem);
        DealLineItem saved = dealLineItemRepository.save(lineItem);
        recalculateDealAmount(tenantId, dealId, userId);
        logDealLineItemActivity(tenantId, dealId, saved, userId, "DEAL_LINE_ITEM_ADDED", "Deal line item added");
        return dealLineItemMapper.toResponse(saved);
    }

    public DealLineItemResponse updateLineItem(UUID tenantId, UUID dealId, UUID lineItemId, UUID userId, DealLineItemUpdateRequest request) {
        // RBAC-7: parent deal must be within the caller's deal:write scope.
        ensureDealAccessible(tenantId, dealId, "write");
        DealLineItem lineItem = dealLineItemRepository.findByIdAndTenantIdAndDealIdAndDeletedFalse(lineItemId, tenantId, dealId)
                .orElseThrow(() -> new BusinessException("DEAL_LINE_ITEM_NOT_FOUND", "Deal line item not found"));

        if (request.getQuantity() != null) {
            lineItem.setQuantity(normalizeQuantity(request.getQuantity()));
        }
        if (request.getUnitPrice() != null) {
            lineItem.setUnitPrice(normalizeMoney(request.getUnitPrice()));
        }
        if (request.getDiscountAmount() != null) {
            lineItem.setDiscountAmount(normalizeMoney(request.getDiscountAmount()));
        }
        if (request.getTaxAmount() != null) {
            lineItem.setTaxAmount(normalizeMoney(request.getTaxAmount()));
        }
        if (request.getDescription() != null) {
            lineItem.setDescription(request.getDescription());
        }
        if (request.getServiceStartDate() != null) {
            lineItem.setServiceStartDate(request.getServiceStartDate());
        }
        if (request.getServiceEndDate() != null) {
            lineItem.setServiceEndDate(request.getServiceEndDate());
        }
        if (request.getRenewable() != null) {
            lineItem.setRenewable(request.getRenewable());
        }
        if (request.getRenewalNoticeDays() != null) {
            lineItem.setRenewalNoticeDays(request.getRenewalNoticeDays());
        }
        if (request.getCustomData() != null) {
            lineItem.setCustomData(request.getCustomData());
        }

        lineItem.setUpdatedBy(userId);
        validateLineItem(lineItem);
        DealLineItem updated = dealLineItemRepository.save(lineItem);
        recalculateDealAmount(tenantId, dealId, userId);
        logDealLineItemActivity(tenantId, dealId, updated, userId, "DEAL_LINE_ITEM_UPDATED", "Deal line item updated");
        return dealLineItemMapper.toResponse(updated);
    }

    public void deleteLineItem(UUID tenantId, UUID dealId, UUID lineItemId, UUID userId) {
        // RBAC-7: parent deal must be within the caller's deal:write scope
        // (line items are components of the deal; the catalog defines no
        // separate line-item permission).
        ensureDealAccessible(tenantId, dealId, "write");
        DealLineItem lineItem = dealLineItemRepository.findByIdAndTenantIdAndDealIdAndDeletedFalse(lineItemId, tenantId, dealId)
                .orElseThrow(() -> new BusinessException("DEAL_LINE_ITEM_NOT_FOUND", "Deal line item not found"));

        lineItem.softDelete(userId);
        lineItem.setUpdatedBy(userId);
        dealLineItemRepository.save(lineItem);
        recalculateDealAmount(tenantId, dealId, userId);
        logDealLineItemActivity(tenantId, dealId, lineItem, userId, "DEAL_LINE_ITEM_REMOVED", "Deal line item removed");
    }

    private void ensureDealExists(UUID tenantId, UUID dealId) {
        ensureDealAccessible(tenantId, dealId, "read");
    }

    /**
     * RBAC-7: line items inherit the parent deal's scope. The caller must
     * hold the parent deal permission at a scope that covers the record.
     */
    private void ensureDealAccessible(UUID tenantId, UUID dealId, String action) {
        UUID currentUserId = com.shivang.crm.util.UserUtil.currentUserId();
        String scope = recordScopeGuard.requireScope(tenantId, currentUserId, "deal", action);

        Deal deal = dealRepository.findByIdAndTenantId(dealId, tenantId)
                .orElseThrow(() -> new BusinessException("DEAL_NOT_FOUND", "Deal not found"));

        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, currentUserId, deal.getOwnerId(), deal.getCreatedBy());
    }

    private void validateCreateRequest(DealLineItemCreateRequest request) {
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "Quantity must be greater than zero");
        }
        if (request.getUnitPrice() != null && request.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_UNIT_PRICE", "Unit price cannot be negative");
        }
        if (request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_DISCOUNT", "Discount amount cannot be negative");
        }
        if (request.getTaxAmount() != null && request.getTaxAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_TAX", "Tax amount cannot be negative");
        }
    }

    private void validateLineItem(DealLineItem lineItem) {
        BigDecimal quantity = lineItem.getQuantity();
        BigDecimal unitPrice = lineItem.getUnitPrice();
        BigDecimal discountAmount = lineItem.getDiscountAmount() == null ? BigDecimal.ZERO : lineItem.getDiscountAmount();
        BigDecimal taxAmount = lineItem.getTaxAmount() == null ? BigDecimal.ZERO : lineItem.getTaxAmount();

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "Quantity must be greater than zero");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_UNIT_PRICE", "Unit price cannot be negative");
        }
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_DISCOUNT", "Discount amount cannot be negative");
        }
        if (taxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_TAX", "Tax amount cannot be negative");
        }

        BigDecimal subtotal = quantity.multiply(unitPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (discountAmount.compareTo(subtotal) > 0) {
            throw new BusinessException("INVALID_DISCOUNT", "Discount amount cannot exceed subtotal");
        }
        BigDecimal lineTotal = subtotal.subtract(discountAmount).add(taxAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (lineTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_LINE_TOTAL", "Line total cannot be negative");
        }
        lineItem.setLineTotal(lineTotal);

        if (lineItem.getServiceStartDate() != null && lineItem.getServiceEndDate() != null
                && lineItem.getServiceEndDate().isBefore(lineItem.getServiceStartDate())) {
            throw new BusinessException("INVALID_DATE_RANGE", "Service end date cannot be before start date");
        }
        if (Boolean.TRUE.equals(lineItem.getRenewable()) && lineItem.getRenewalNoticeDays() != null && lineItem.getRenewalNoticeDays() < 0) {
            throw new BusinessException("INVALID_RENEWAL_NOTICE_DAYS", "Renewal notice days cannot be negative");
        }
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        return quantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private LocalDate resolveServiceEndDate(LocalDate startDate, LocalDate endDate, Offering offering) {
        if (endDate != null) {
            return endDate;
        }
        if (startDate != null && offering.getDefaultTermDays() != null && offering.getDefaultTermDays() > 0) {
            return startDate.plusDays(offering.getDefaultTermDays());
        }
        return null;
    }

    private void recalculateDealAmount(UUID tenantId, UUID dealId, UUID userId) {
        BigDecimal sum = Optional.ofNullable(dealLineItemRepository.sumLineTotalsByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId))
                .orElse(BigDecimal.ZERO);

        Deal deal = dealRepository.findByIdAndTenantId(dealId, tenantId)
                .orElseThrow(() -> new BusinessException("DEAL_NOT_FOUND", "Deal not found"));

        deal.setAmount(sum.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        deal.setExpectedRevenue(calculateExpectedRevenue(sum, deal.getProbability()));
        deal.setUpdatedBy(userId);
        dealRepository.save(deal);
    }

    private BigDecimal calculateExpectedRevenue(BigDecimal amount, Integer probability) {
        if (amount == null || probability == null || probability == 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return amount.multiply(BigDecimal.valueOf(probability)).divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void logDealLineItemActivity(UUID tenantId, UUID dealId, DealLineItem lineItem, UUID userId, String activityType, String description) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("lineItemId", lineItem.getId());
        metadata.put("offeringId", lineItem.getOfferingId());
        metadata.put("itemName", lineItem.getItemName());
        metadata.put("quantity", lineItem.getQuantity());
        metadata.put("unitPrice", lineItem.getUnitPrice());
        metadata.put("lineTotal", lineItem.getLineTotal());
        Deal deal = dealRepository.findByIdAndTenantId(dealId, tenantId)
                .orElseThrow(() -> new BusinessException("DEAL_NOT_FOUND", "Deal not found"));
        metadata.put("newDealAmount", deal.getAmount());
        activityService.logActivity(tenantId, dealId, "DEAL", activityType, description, userId, metadata);
    }
}
