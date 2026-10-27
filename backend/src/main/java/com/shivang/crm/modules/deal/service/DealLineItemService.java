package com.shivang.crm.modules.deal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.mapper.DealLineItemMapper;
import com.shivang.crm.modules.deal.repository.DealLineItemRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
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
    /** L3.0-P6: sole producer of the DEAL_LINE_ITEM event family. */
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;

    /**
     * L3.0-P4-C / P5: commercial line items are part of the opportunity, so they
     * may only be edited while the Deal is still commercially open.
     *
     * <p>P4 shipped a transitional exception here: a closed Deal whose
     * provisioning was still pending could accept lines. It existed because a
     * Deal created directly in CLOSED_WON had no way to receive its lines, and
     * opening a won Deal up to be sold would have been worse.
     *
     * <p>P5 removed the exception by fixing the real problem. Deal creation now
     * accepts its initial line items, so a directly-created CLOSED_WON Deal is
     * born complete. There is therefore no legitimate reason left for a closed
     * Deal to be missing lines, and any such Deal is a defect to repair through
     * reconciliation rather than an opening to be exploited.
     *
     * <p>Returns the parent Deal so callers avoid a second lookup.
     */
    private Deal ensureDealAllowsLineItemMutation(UUID tenantId, UUID dealId) {
        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId)
                .orElseThrow(() -> new BusinessException("DEAL_NOT_FOUND", "Deal not found"));

        RecordCategory category = deal.getRecordCategory();
        if (category == null || category == RecordCategory.OPEN) {
            return deal;
        }

        throw new BusinessException("DEAL_CLOSED_LINE_ITEM_MUTATION",
                "Line items cannot be modified on a " + category + " deal");
    }

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
        // L3.0-P4-C: lifecycle boundary before any mutation.
        ensureDealAllowsLineItemMutation(tenantId, dealId);
        return persistLineItem(tenantId, dealId, userId, request);
    }

    /**
     * L3.0-P5: creates one line item, skipping the standalone entry point's
     * authorization and lifecycle gates.
     *
     * <p>Package-private and only for {@link DealService#createDeal}, which owns
     * an already-authorized {@code deal:write} scope for the same caller and is
     * building this Deal's own initial lines. Two gates are deliberately bypassed
     * because neither can apply to a line being born with its Deal:
     *
     * <ul>
     *   <li>the scope check, which {@code createDeal} has already performed;</li>
     *   <li>the closed-deal lifecycle guard, which a directly-created CLOSED_WON
     *       Deal would otherwise trip on its own first line.</li>
     * </ul>
     *
     * <p>Everything that actually decides whether the line is valid is still
     * applied here, so a nested line is validated exactly like a standalone one:
     * the Offering must exist, be live and belong to this tenant; quantity,
     * money, date range and renewal notice must be sane; and the currency is
     * snapshotted from the Offering rather than trusted from the request.
     */
    DealLineItemResponse persistLineItem(UUID tenantId, UUID dealId, UUID userId, DealLineItemCreateRequest request) {
        validateCreateRequest(request);

        Offering offering = offeringRepository.findByIdAndTenantIdAndDeletedFalse(request.getOfferingId(), tenantId)
                .orElseThrow(() -> new BusinessException("OFFERING_NOT_FOUND", "Offering not found"));
        if (!Boolean.TRUE.equals(offering.getActive())) {
            throw new BusinessException("OFFERING_INACTIVE", "Offering is not active");
        }
        requireDealCurrency(tenantId, dealId, offering.getCurrencyCode());

        DealLineItem lineItem = new DealLineItem();
        lineItem.setTenantId(tenantId);
        lineItem.setDealId(dealId);
        lineItem.setOfferingId(offering.getId());
        lineItem.setItemName(offering.getName());
        lineItem.setItemCode(offering.getCode());
        // L3.0-P4-F: currency is snapshotted from the Offering so the monetary
        // values on this line have explicit provenance. Offering.currencyCode is
        // nullable, and that nullability is preserved rather than defaulted to
        // an invented currency.
        lineItem.setCurrencyCode(offering.getCurrencyCode());
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
        publishLineItemCreated(tenantId, userId, saved);
        return dealLineItemMapper.toResponse(saved);
    }

    /**
     * L3.0-P6: the single authoritative producer of {@code DEAL_LINE_ITEM.CREATED}.
     *
     * <p>Placed inside {@link #persistLineItem} on purpose, so P5's nested creation
     * and ordinary line creation emit through exactly the same path. Because
     * {@code persistLineItem} runs inside the caller's transaction, the outbox row
     * shares the Deal's commit: a rolled-back creation leaves no event behind, and
     * an atomic CLOSED_WON creation writes Deal, lines and their events together.
     */
    private void publishLineItemCreated(UUID tenantId, UUID userId, DealLineItem lineItem) {
        Map<String, Object> metadata = baseMetadata(tenantId, userId);
        // The parent Deal and the Offering are what let a consumer place this line
        // in context without the event carrying the entities themselves.
        putIfPresent(metadata, "dealId", lineItem.getDealId());
        putIfPresent(metadata, "offeringId", lineItem.getOfferingId());
        metadata.put("quantity", lineItem.getQuantity());
        metadata.put("unitPrice", lineItem.getUnitPrice());
        metadata.put("lineTotal", lineItem.getLineTotal());
        putIfPresent(metadata, "currencyCode", lineItem.getCurrencyCode());

        canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.DEAL_LINE_ITEM_ENTITY_TYPE,
                CanonicalCrmEvent.CREATED_EVENT_TYPE,
                lineItem.getId(),
                metadata);
    }

    /**
     * Shared event metadata for the line-item family: the parent Deal and the
     * Offering it was sold from are the only identifiers a consumer needs to
     * resolve a line item, so nothing heavier is carried.
     */
    private Map<String, Object> baseMetadata(UUID tenantId, UUID userId) {
        boolean workflowCaused = com.shivang.crm.shared.event.CausalEventContext.get() != null;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("actorId", userId.toString());
        metadata.put("actorType", workflowCaused ? "SYSTEM" : "USER");
        metadata.put("source", workflowCaused ? "WORKFLOW" : "MANUAL");
        return metadata;
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value.toString());
        }
    }

    public DealLineItemResponse updateLineItem(UUID tenantId, UUID dealId, UUID lineItemId, UUID userId, DealLineItemUpdateRequest request) {
        // RBAC-7: parent deal must be within the caller's deal:write scope.
        ensureDealAccessible(tenantId, dealId, "write");
        // L3.0-P4-C: lifecycle boundary before any mutation.
        ensureDealAllowsLineItemMutation(tenantId, dealId);
        DealLineItem lineItem = dealLineItemRepository.findByIdAndTenantIdAndDealIdAndDeletedFalse(lineItemId, tenantId, dealId)
                .orElseThrow(() -> new BusinessException("DEAL_LINE_ITEM_NOT_FOUND", "Deal line item not found"));

        // L3.0-P6: snapshot before mutation so DEAL_LINE_ITEM.UPDATED is only
        // emitted when a field genuinely changed, matching the existing
        // LEAD.UPDATED convention.
        Map<String, Object> before = lineItemSnapshot(lineItem);

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
        publishLineItemUpdated(tenantId, dealId, userId, updated, before);
        return dealLineItemMapper.toResponse(updated);
    }

    /**
     * L3.0-P6: the single authoritative producer of
     * {@code DEAL_LINE_ITEM.UPDATED}. Suppressed when no field actually changed,
     * so a no-op request does not describe a mutation that never happened.
     */
    private void publishLineItemUpdated(UUID tenantId, UUID dealId, UUID userId,
            DealLineItem lineItem, Map<String, Object> before) {
        Map<String, Object> after = lineItemSnapshot(lineItem);
        List<String> changedFields = new ArrayList<>();
        before.forEach((field, oldValue) -> {
            if (!Objects.equals(oldValue, after.get(field))) {
                changedFields.add(field);
            }
        });
        if (changedFields.isEmpty()) {
            return;
        }

        Map<String, Object> metadata = baseMetadata(tenantId, userId);
        metadata.put("changedFields", changedFields);
        putIfPresent(metadata, "dealId", dealId);
        putIfPresent(metadata, "offeringId", lineItem.getOfferingId());
        putIfPresent(metadata, "lineTotal", lineItem.getLineTotal());
        putIfPresent(metadata, "currencyCode", lineItem.getCurrencyCode());

        canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.DEAL_LINE_ITEM_ENTITY_TYPE,
                CanonicalCrmEvent.UPDATED_EVENT_TYPE,
                lineItem.getId(),
                metadata);
    }

    /**
     * The mutable commercial fields of a line item. The currency snapshot is
     * included because it is set once from the Offering and never edited, so a
     * difference here would mean something actually changed it.
     *
     * <p>Monetary values are compared with trailing zeros stripped. Without that,
     * a no-op edit that only differs in representation (2 versus 2.0000) would
     * publish an UPDATED event describing a mutation that did not happen.
     */
    private Map<String, Object> lineItemSnapshot(DealLineItem lineItem) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("quantity", moneyValue(lineItem.getQuantity()));
        snapshot.put("unitPrice", moneyValue(lineItem.getUnitPrice()));
        snapshot.put("discountAmount", moneyValue(lineItem.getDiscountAmount()));
        snapshot.put("taxAmount", moneyValue(lineItem.getTaxAmount()));
        snapshot.put("description", lineItem.getDescription());
        snapshot.put("serviceStartDate", lineItem.getServiceStartDate());
        snapshot.put("serviceEndDate", lineItem.getServiceEndDate());
        snapshot.put("renewable", lineItem.getRenewable());
        snapshot.put("renewalNoticeDays", lineItem.getRenewalNoticeDays());
        snapshot.put("customData", lineItem.getCustomData());
        snapshot.put("currencyCode", lineItem.getCurrencyCode());
        return snapshot;
    }

    private static BigDecimal moneyValue(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    public void deleteLineItem(UUID tenantId, UUID dealId, UUID lineItemId, UUID userId) {
        // RBAC-7: parent deal must be within the caller's deal:write scope
        // (line items are components of the deal; the catalog defines no
        // separate line-item permission).
        ensureDealAccessible(tenantId, dealId, "write");
        // L3.0-P4-C: lifecycle boundary before any mutation. Soft-deleting a line
        // item on a provisioned won deal would orphan its entitlement.
        ensureDealAllowsLineItemMutation(tenantId, dealId);
        DealLineItem lineItem = dealLineItemRepository.findByIdAndTenantIdAndDealIdAndDeletedFalse(lineItemId, tenantId, dealId)
                .orElseThrow(() -> new BusinessException("DEAL_LINE_ITEM_NOT_FOUND", "Deal line item not found"));

        lineItem.softDelete(userId);
        lineItem.setUpdatedBy(userId);
        dealLineItemRepository.save(lineItem);
        recalculateDealAmount(tenantId, dealId, userId);
        logDealLineItemActivity(tenantId, dealId, lineItem, userId, "DEAL_LINE_ITEM_REMOVED", "Deal line item removed");
        // L3.0-P6: DEAL_LINE_ITEM.DELETED. Published after the soft delete is
        // persisted, inside the caller's transaction, so the outbox row commits
        // or rolls back with the mutation. entityId stays the line item id, and
        // the parent identifiers are carried in metadata so the deleted line is
        // still identifiable.
        Map<String, Object> metadata = baseMetadata(tenantId, userId);
        putIfPresent(metadata, "dealId", dealId);
        putIfPresent(metadata, "offeringId", lineItem.getOfferingId());
        metadata.put("deleted", Boolean.TRUE);
        canonicalCrmEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.DEAL_LINE_ITEM_ENTITY_TYPE,
                CanonicalCrmEvent.DELETED_EVENT_TYPE,
                lineItem.getId(),
                metadata);
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

        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId)
                .orElseThrow(() -> new BusinessException("DEAL_NOT_FOUND", "Deal not found"));

        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, currentUserId, deal.getOwnerId(), deal.getCreatedBy());
    }

    /**
     * The deal has one commercial currency, derived from its active lines.
     * NULL is a real value here: a line sold from an Offering with no currency
     * cannot be silently mixed with a priced currency.
     */
    private void requireDealCurrency(UUID tenantId, UUID dealId, String currencyCode) {
        List<String> existingCurrencies = dealLineItemRepository
                .findDistinctCurrencyCodesByTenantIdAndDealIdAndDeletedFalse(tenantId, dealId);
        if (existingCurrencies == null || existingCurrencies.isEmpty()) {
            return;
        }
        if (existingCurrencies.stream().anyMatch(existing -> !Objects.equals(existing, currencyCode))) {
            throw new BusinessException("DEAL_CURRENCY_MISMATCH",
                    "All line items on a deal must use the same currency; expected "
                            + existingCurrencies.get(0) + " but got " + currencyCode);
        }
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

        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId)
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
        Deal deal = dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantId)
                .orElseThrow(() -> new BusinessException("DEAL_NOT_FOUND", "Deal not found"));
        metadata.put("newDealAmount", deal.getAmount());
        activityService.logActivity(tenantId, dealId, "DEAL", activityType, description, userId, metadata);
    }
}
