package com.shivang.crm.modules.deal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.deal.dto.SalesDashboardResponse;
import com.shivang.crm.modules.deal.dto.SalesDashboardResponse.ClosingMetrics;
import com.shivang.crm.modules.deal.dto.SalesDashboardResponse.DealSummary;
import com.shivang.crm.modules.deal.dto.SalesDashboardResponse.LeadFunnel;
import com.shivang.crm.modules.deal.dto.SalesDashboardResponse.OwnerBreakdown;
import com.shivang.crm.modules.deal.dto.SalesDashboardResponse.StageBreakdown;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.ForecastCategory;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.deal.repository.DealSpecifications;
import com.shivang.crm.modules.deal.repository.DealStageRepository;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.lead.repository.LeadSpecifications;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesDashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    /**
     * Phase 8C-2C business rule: an OPEN deal with no update for this many
     * calendar days is stale. Deliberately a fixed constant, not configuration.
     */
    private static final int STALE_DEAL_DAYS = 30;

    private final DealRepository dealRepository;
    private final DealStageRepository dealStageRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;

    @Transactional(readOnly = true)
    public SalesDashboardResponse getSalesDashboard(UUID tenantId, UUID currentUserId) {
        List<Deal> visibleDeals = loadVisibleDeals(tenantId, currentUserId);
        LocalDate today = LocalDate.now();

        return SalesDashboardResponse.builder()
            .deals(buildDealSummary(visibleDeals, today))
            .stages(buildStageBreakdown(tenantId, visibleDeals, today))
            .owners(buildOwnerBreakdown(visibleDeals))
            .leadFunnel(buildLeadFunnel(tenantId, currentUserId))
            .closing(buildClosingMetrics(visibleDeals, today))
            .build();
    }

    /**
     * Applies exactly the same tenant + ALL/TEAM/OWN visibility rules as
     * DealService.listDeals by reusing DealSpecifications.visibleToUser.
     */
    private List<Deal> loadVisibleDeals(UUID tenantId, UUID currentUserId) {
        String accessScope = permissionEvaluatorService.getAccessScope(currentUserId, tenantId, "deal", "read");
        List<UUID> teamUserIds = "TEAM".equals(accessScope)
            ? userRepository.findTeamUserIdsByManagerAndTenant(tenantId, currentUserId)
            : List.of();

        Specification<Deal> spec = DealSpecifications.byTenantId(tenantId)
            .and(DealSpecifications.visibleToUser(accessScope, currentUserId, teamUserIds));

        return dealRepository.findAll(spec);
    }

    private DealSummary buildDealSummary(List<Deal> deals, LocalDate today) {
        long openCount = 0;
        long wonCount = 0;
        long lostCount = 0;
        BigDecimal openValue = ZERO;
        BigDecimal weightedValue = ZERO;
        BigDecimal wonValue = ZERO;
        BigDecimal lostValue = ZERO;
        Map<ForecastCategory, BigDecimal> forecastByCategory = new HashMap<>();
        long openAgeTotalDays = 0;
        long openAgeSamples = 0;
        long maxOpenAgeDays = 0;
        long stageAgeTotalDays = 0;
        long stageAgeSamples = 0;
        long staleDealCount = 0;
        BigDecimal staleDealValue = ZERO;
        BigDecimal staleDealWeightedValue = ZERO;

        for (Deal deal : deals) {
            RecordCategory category = deal.getRecordCategory();
            BigDecimal amount = deal.getAmount() == null ? ZERO : deal.getAmount();
            BigDecimal expectedRevenue = deal.getExpectedRevenue() == null ? ZERO : deal.getExpectedRevenue();

            if (category == RecordCategory.CLOSED_WON) {
                wonCount++;
                wonValue = wonValue.add(amount);
            } else if (category == RecordCategory.CLOSED_LOST) {
                lostCount++;
                lostValue = lostValue.add(amount);
            } else {
                openCount++;
                openValue = openValue.add(amount);
                weightedValue = weightedValue.add(expectedRevenue);

                if (deal.getCreatedAt() != null) {
                    long ageDays = Math.max(0, ChronoUnit.DAYS.between(
                        LocalDate.ofInstant(deal.getCreatedAt(), ZoneOffset.UTC), today));
                    openAgeTotalDays += ageDays;
                    openAgeSamples++;
                    maxOpenAgeDays = Math.max(maxOpenAgeDays, ageDays);
                }

                if (deal.getStageEnteredAt() != null) {
                    long stageAgeDays = Math.max(0, ChronoUnit.DAYS.between(
                        LocalDate.ofInstant(deal.getStageEnteredAt(), ZoneOffset.UTC), today));
                    stageAgeTotalDays += stageAgeDays;
                    stageAgeSamples++;

                    // Phase 8C-2C stale rule: OPEN && daysSince(stageEnteredAt) >= 30.
                    if (stageAgeDays >= STALE_DEAL_DAYS) {
                        staleDealCount++;
                        staleDealValue = staleDealValue.add(amount);
                        staleDealWeightedValue = staleDealWeightedValue.add(expectedRevenue);
                    }
                }

                forecastByCategory.merge(
                    deal.getForecastCategory() == null ? ForecastCategory.PIPELINE : deal.getForecastCategory(),
                    expectedRevenue,
                    BigDecimal::add);
            }
        }

        return DealSummary.builder()
            .totalCount(deals.size())
            .openCount(openCount)
            .wonCount(wonCount)
            .lostCount(lostCount)
            .openPipelineValue(scale(openValue))
            .weightedPipelineValue(scale(weightedValue))
            .wonValue(scale(wonValue))
            .lostValue(scale(lostValue))
            .averageOpenDealSize(openCount == 0 ? ZERO : scale(openValue.divide(BigDecimal.valueOf(openCount), 2, RoundingMode.HALF_UP)))
            .averageDaysInPipeline(openAgeSamples == 0
                ? null
                : BigDecimal.valueOf(openAgeTotalDays)
                    .divide(BigDecimal.valueOf(openAgeSamples), 1, RoundingMode.HALF_UP))
            .maxOpenDealAgeDays(openAgeSamples == 0 ? null : maxOpenAgeDays)
            .averageDaysInCurrentStage(stageAgeSamples == 0
                ? null
                : BigDecimal.valueOf(stageAgeTotalDays)
                    .divide(BigDecimal.valueOf(stageAgeSamples), 1, RoundingMode.HALF_UP))
            .staleDealCount(staleDealCount)
            .staleDealValue(scale(staleDealValue))
            .staleDealWeightedValue(scale(staleDealWeightedValue))
            .staleDealPercentage(openCount == 0
                ? null
                : BigDecimal.valueOf(staleDealCount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(openCount), 1, RoundingMode.HALF_UP))
            .forecastByCategory(forecastByCategory.isEmpty() ? Map.of() : forecastByCategory)
            .build();
    }

    private List<StageBreakdown> buildStageBreakdown(UUID tenantId, List<Deal> deals, LocalDate today) {
        Map<UUID, StageBreakdown> byStageId = new HashMap<>();
        for (DealStage stage : dealStageRepository.findByTenantIdOrderByDisplayOrder(tenantId)) {
            byStageId.put(stage.getId(), StageBreakdown.builder()
                .stageId(stage.getId())
                .stageName(stage.getName())
                .color(stage.getColor())
                .displayOrder(stage.getDisplayOrder())
                .recordCategory(stage.getRecordCategory())
                .count(0)
                .totalAmount(ZERO)
                .build());
        }

        // Current-stage ageing accumulators (open deals only)
        Map<UUID, Long> stageAgeTotals = new HashMap<>();
        Map<UUID, Long> stageAgeSamples = new HashMap<>();
        Map<UUID, Long> stageStaleCounts = new HashMap<>();

        for (Deal deal : deals) {
            if (deal.getStage() == null) {
                continue;
            }
            StageBreakdown breakdown = byStageId.get(deal.getStage().getId());
            if (breakdown != null) {
                breakdown.setCount(breakdown.getCount() + 1);
                breakdown.setTotalAmount(breakdown.getTotalAmount().add(deal.getAmount() == null ? BigDecimal.ZERO : deal.getAmount()));

                RecordCategory category = deal.getRecordCategory();
                boolean isOpen = category != RecordCategory.CLOSED_WON && category != RecordCategory.CLOSED_LOST;
                if (isOpen && deal.getStageEnteredAt() != null) {
                    long stageAgeDays = Math.max(0, ChronoUnit.DAYS.between(
                        LocalDate.ofInstant(deal.getStageEnteredAt(), ZoneOffset.UTC), today));
                    stageAgeTotals.merge(deal.getStage().getId(), stageAgeDays, Long::sum);
                    stageAgeSamples.merge(deal.getStage().getId(), 1L, Long::sum);
                    if (stageAgeDays >= STALE_DEAL_DAYS) {
                        stageStaleCounts.merge(deal.getStage().getId(), 1L, Long::sum);
                    }
                }
            }
        }

        List<StageBreakdown> stages = new ArrayList<>(byStageId.values());
        stages.sort((a, b) -> Integer.compare(
            a.getDisplayOrder() == null ? 0 : a.getDisplayOrder(),
            b.getDisplayOrder() == null ? 0 : b.getDisplayOrder()));
        stages.forEach(stage -> {
            stage.setTotalAmount(scale(stage.getTotalAmount()));
            Long total = stageAgeTotals.get(stage.getStageId());
            Long samples = stageAgeSamples.get(stage.getStageId());
            stage.setAverageDaysInStage(
                total == null || samples == null || samples == 0
                    ? null
                    : BigDecimal.valueOf(total).divide(BigDecimal.valueOf(samples), 1, RoundingMode.HALF_UP));
            stage.setStaleCount(stageStaleCounts.getOrDefault(stage.getStageId(), 0L));
        });
        return stages;
    }

    private List<OwnerBreakdown> buildOwnerBreakdown(List<Deal> deals) {
        Map<UUID, List<Deal>> byOwner = new HashMap<>();
        UUID unassignedKey = new UUID(0L, 0L);

        for (Deal deal : deals) {
            UUID key = deal.getOwnerId() == null ? unassignedKey : deal.getOwnerId();
            byOwner.computeIfAbsent(key, k -> new ArrayList<>()).add(deal);
        }

        List<OwnerBreakdown> owners = new ArrayList<>();
        for (Map.Entry<UUID, List<Deal>> entry : byOwner.entrySet()) {
            long openCount = 0;
            long wonCount = 0;
            long lostCount = 0;
            BigDecimal openValue = ZERO;
            BigDecimal wonValue = ZERO;

            for (Deal deal : entry.getValue()) {
                RecordCategory category = deal.getRecordCategory();
                BigDecimal amount = deal.getAmount() == null ? BigDecimal.ZERO : deal.getAmount();
                if (category == RecordCategory.CLOSED_WON) {
                    wonCount++;
                    wonValue = wonValue.add(amount);
                } else if (category == RecordCategory.CLOSED_LOST) {
                    lostCount++;
                } else {
                    openCount++;
                    openValue = openValue.add(amount);
                }
            }

            boolean unassigned = entry.getKey().equals(unassignedKey);
            owners.add(OwnerBreakdown.builder()
                .ownerUserId(unassigned ? null : entry.getKey())
                .ownerName(unassigned ? "Unassigned" : resolveOwnerName(entry.getKey()))
                .openCount(openCount)
                .wonCount(wonCount)
                .lostCount(lostCount)
                .openValue(scale(openValue))
                .wonValue(scale(wonValue))
                .build());
        }

        owners.sort((a, b) -> Long.compare(b.getOpenCount(), a.getOpenCount()));
        return owners;
    }

    private String resolveOwnerName(UUID ownerId) {
        return userRepository.findById(ownerId).map(User::getDisplayName).orElse("Unknown user");
    }

    private LeadFunnel buildLeadFunnel(UUID tenantId, UUID currentUserId) {
        String accessScope = permissionEvaluatorService.getAccessScope(currentUserId, tenantId, "lead", "read");
        List<UUID> teamUserIds = "TEAM".equals(accessScope)
            ? userRepository.findTeamUserIdsByManagerAndTenant(tenantId, currentUserId)
            : List.of();

        Specification<com.shivang.crm.modules.lead.entity.Lead> totalSpec =
            LeadSpecifications.byTenantId(tenantId)
                .and(LeadSpecifications.visibleToUser(accessScope, currentUserId, teamUserIds));
        Specification<com.shivang.crm.modules.lead.entity.Lead> convertedSpec =
            totalSpec.and(LeadSpecifications.byIsConverted(true));

        long totalLeads = leadRepository.count(totalSpec);
        long convertedLeads = leadRepository.count(convertedSpec);
        long openLeads = totalLeads - convertedLeads;
        double conversionRate = totalLeads == 0
            ? 0.0
            : Math.round((convertedLeads * 1000.0 / totalLeads)) / 10.0;

        return LeadFunnel.builder()
            .totalLeads(totalLeads)
            .openLeads(openLeads)
            .convertedLeads(convertedLeads)
            .conversionRatePercent(conversionRate)
            .build();
    }

    private ClosingMetrics buildClosingMetrics(List<Deal> deals, LocalDate today) {
        long next30Count = 0;
        long overdueCount = 0;
        BigDecimal next30Value = ZERO;
        BigDecimal overdueValue = ZERO;
        BigDecimal wonValueLast30Days = ZERO;
        long salesCycleTotalDays = 0;
        long salesCycleSamples = 0;

        LocalDate cycleStart = today.minusDays(30);
        LocalDate cycleEnd = today.plusDays(30);

        for (Deal deal : deals) {
            RecordCategory category = deal.getRecordCategory();
            BigDecimal amount = deal.getAmount() == null ? ZERO : deal.getAmount();
            boolean isOpen = category != RecordCategory.CLOSED_WON && category != RecordCategory.CLOSED_LOST;

            if (isOpen && deal.getExpectedCloseDate() != null) {
                LocalDate closeDate = deal.getExpectedCloseDate();
                if (!closeDate.isBefore(today) && !closeDate.isAfter(cycleEnd)) {
                    next30Count++;
                    next30Value = next30Value.add(amount);
                } else if (closeDate.isBefore(today)) {
                    overdueCount++;
                    overdueValue = overdueValue.add(amount);
                }
            }

            if (category == RecordCategory.CLOSED_WON && deal.getClosedDate() != null) {
                if (!deal.getClosedDate().isBefore(cycleStart)) {
                    wonValueLast30Days = wonValueLast30Days.add(amount);
                }
                if (deal.getCreatedAt() != null) {
                    LocalDate createdDate = LocalDate.ofInstant(deal.getCreatedAt(), ZoneOffset.UTC);
                    long days = ChronoUnit.DAYS.between(createdDate, deal.getClosedDate());
                    if (days >= 0) {
                        salesCycleTotalDays += days;
                        salesCycleSamples++;
                    }
                }
            }
        }

        return ClosingMetrics.builder()
            .expectedCloseNext30DaysCount(next30Count)
            .expectedCloseNext30DaysValue(scale(next30Value))
            .overdueExpectedCloseCount(overdueCount)
            .overdueExpectedCloseValue(scale(overdueValue))
            .averageSalesCycleDays(salesCycleSamples == 0 ? null : salesCycleTotalDays / salesCycleSamples)
            .wonValueLast30Days(scale(wonValueLast30Days))
            .build();
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
