package com.shivang.crm.modules.analytics.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.analytics.AnalyticsContext;
import com.shivang.crm.modules.analytics.AnalyticsDateRange;
import com.shivang.crm.modules.analytics.dto.AnalyticsSummaryResponse;
import com.shivang.crm.modules.analytics.dto.AnalyticsTrendResponse;
import com.shivang.crm.modules.analytics.dto.AccountsByOwnerRow;
import com.shivang.crm.modules.analytics.dto.ActivityRatesSummary;
import com.shivang.crm.modules.analytics.dto.CallDurationSummary;
import com.shivang.crm.modules.analytics.dto.CallStatusSummary;
import com.shivang.crm.modules.analytics.dto.ContactsPerAccountRow;
import com.shivang.crm.modules.analytics.dto.ConversionOwnerRow;
import com.shivang.crm.modules.analytics.dto.ConversionPeriodSummary;
import com.shivang.crm.modules.analytics.dto.CurrentStageAgeSummary;
import com.shivang.crm.modules.analytics.dto.DealAgingRow;
import com.shivang.crm.modules.analytics.dto.ForecastCategoryRow;
import com.shivang.crm.modules.analytics.dto.LeadSourcePerformanceRow;
import com.shivang.crm.modules.analytics.dto.PipelineAccountRow;
import com.shivang.crm.modules.analytics.dto.PipelineOwnerRow;
import com.shivang.crm.modules.analytics.dto.PipelineStageRow;
import com.shivang.crm.modules.analytics.export.CsvWriter;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * Serializes the scoped analytics summaries/trends to CSV. Reuses the exact
 * same {@link AnalyticsService} and scope resolver as the dashboards, so an
 * export can never expose more data than the authenticated user may view.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsExportService {

    private final AnalyticsService analyticsService;

    public String summaryCsv(AnalyticsContext context, AnalyticsDateRange range) {
        AnalyticsSummaryResponse s = analyticsService.getSummary(context, range);

        StringBuilder sb = new StringBuilder();
        sb.append(CsvWriter.row("scope", "from", "to"));
        sb.append(CsvWriter.row(s.getScope(), s.getFrom(), s.getTo()));

        sb.append(CsvWriter.row("metric", "value"));

        sb.append(CsvWriter.row("leads", s.getLeads()));
        sb.append(CsvWriter.row("contacts", s.getContacts()));
        sb.append(CsvWriter.row("deals", s.getDeals()));
        sb.append(CsvWriter.row("tasks", s.getTasks()));
        sb.append(CsvWriter.row("calls", s.getCalls()));
        sb.append(CsvWriter.row("meetings", s.getMeetings()));

        sb.append(CsvWriter.row("newLeads", s.getLeadMetrics().getNewLeads()));
        sb.append(CsvWriter.row("convertedLeads", s.getLeadMetrics().getConvertedLeads()));
        sb.append(CsvWriter.row("conversionRate", s.getLeadMetrics().getConversionRate()));

        sb.append(CsvWriter.row("openDeals", s.getDealMetrics().getOpenDeals()));
        sb.append(CsvWriter.row("wonDeals", s.getDealMetrics().getWonDeals()));
        sb.append(CsvWriter.row("lostDeals", s.getDealMetrics().getLostDeals()));
        sb.append(CsvWriter.row("pipelineValue", s.getDealMetrics().getPipelineValue()));
        sb.append(CsvWriter.row("wonValue", s.getDealMetrics().getWonValue()));
        sb.append(CsvWriter.row("winRate", s.getDealMetrics().getWinRate()));

        sb.append(CsvWriter.row("openTasks", s.getActivityMetrics().getOpenTasks()));
        sb.append(CsvWriter.row("completedTasks", s.getActivityMetrics().getCompletedTasks()));
        sb.append(CsvWriter.row("overdueTasks", s.getActivityMetrics().getOverdueTasks()));

        return sb.toString();
    }

    public String trendsCsv(AnalyticsContext context, AnalyticsDateRange range) {
        List<AnalyticsTrendResponse> trends = analyticsService.getTrends(context, range);

        StringBuilder sb = new StringBuilder();
        sb.append(CsvWriter.row("bucket", "leads", "contacts", "deals", "tasks"));
        for (AnalyticsTrendResponse t : trends) {
            sb.append(CsvWriter.row(t.getBucket(), t.getLeads(), t.getContacts(), t.getDeals(), t.getTasks()));
        }
        return sb.toString();
    }

    /**
     * AN-13: single dispatch point for grouped CSV exports. Reuses the exact
     * scoped {@link AnalyticsService} methods and {@link CsvWriter}, so an
     * export carries the same authorization/scope and escaping guarantees as
     * the JSON endpoints.
     */
    public String groupedCsv(String dataset, AnalyticsContext context, AnalyticsDateRange range) {
        return switch (dataset) {
            case "pipeline-stage" -> pipelineStageCsv(context, range);
            case "pipeline-owner" -> pipelineOwnerCsv(context, range);
            case "pipeline-account" -> pipelineAccountCsv(context, range);
            case "conversion-owner" -> conversionOwnerCsv(context, range);
            case "deals-aging" -> dealAgingCsv(context, range);
            case "calls-status" -> callStatusCsv(context, range);
            case "conversion-period" -> conversionPeriodCsv(context, range);
            case "forecast-category" -> forecastCategoryCsv(context, range);
            case "current-stage-age" -> currentStageAgeCsv(context, range);
            case "activity-rates" -> activityRatesCsv(context, range);
            case "calls-duration" -> callDurationCsv(context, range);
            case "lead-source" -> leadSourceCsv(context, range);
            case "contacts-account" -> contactsPerAccountCsv(context, range);
            case "accounts-owner" -> accountsOwnerCsv(context, range);
            default -> throw new BusinessException("INVALID_DATASET",
                    "Unknown grouped dataset: " + dataset);
        };
    }

    private String pipelineStageCsv(AnalyticsContext context, AnalyticsDateRange range) {
        List<PipelineStageRow> rows = analyticsService.getPipelineByStage(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("stageId", "stageName", "openCount", "wonCount", "lostCount",
                "pipelineValue", "wonValue", "totalCount"));
        for (PipelineStageRow r : rows) {
            sb.append(CsvWriter.row(r.getStageId(), r.getStageName(), r.getOpenCount(), r.getWonCount(),
                    r.getLostCount(), r.getPipelineValue(), r.getWonValue(), r.getTotalCount()));
        }
        return sb.toString();
    }

    private String pipelineOwnerCsv(AnalyticsContext context, AnalyticsDateRange range) {
        List<PipelineOwnerRow> rows = analyticsService.getPipelineByOwner(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("ownerUserId", "ownerDisplayName", "openCount", "wonCount", "lostCount",
                "pipelineValue", "wonValue", "totalCount"));
        for (PipelineOwnerRow r : rows) {
            sb.append(CsvWriter.row(r.getOwnerUserId(), r.getOwnerDisplayName(), r.getOpenCount(), r.getWonCount(),
                    r.getLostCount(), r.getPipelineValue(), r.getWonValue(), r.getTotalCount()));
        }
        return sb.toString();
    }

    private String pipelineAccountCsv(AnalyticsContext context, AnalyticsDateRange range) {
        List<PipelineAccountRow> rows = analyticsService.getPipelineByAccount(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("accountId", "accountName", "openCount", "wonCount", "lostCount",
                "pipelineValue", "wonValue", "totalCount"));
        for (PipelineAccountRow r : rows) {
            sb.append(CsvWriter.row(r.getAccountId(), r.getAccountName(), r.getOpenCount(), r.getWonCount(),
                    r.getLostCount(), r.getPipelineValue(), r.getWonValue(), r.getTotalCount()));
        }
        return sb.toString();
    }

    private String conversionOwnerCsv(AnalyticsContext context, AnalyticsDateRange range) {
        List<ConversionOwnerRow> rows = analyticsService.getConversionByOwner(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("ownerUserId", "ownerDisplayName", "newLeadCount", "convertedLeadCount", "conversionRate"));
        for (ConversionOwnerRow r : rows) {
            sb.append(CsvWriter.row(r.getOwnerUserId(), r.getOwnerDisplayName(), r.getNewLeadCount(),
                    r.getConvertedLeadCount(), r.getConversionRate()));
        }
        return sb.toString();
    }

    private String dealAgingCsv(AnalyticsContext context, AnalyticsDateRange range) {
        List<DealAgingRow> rows = analyticsService.getDealAging(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("bucket", "count", "pipelineValue"));
        for (DealAgingRow r : rows) {
            sb.append(CsvWriter.row(r.getBucket(), r.getCount(), r.getPipelineValue()));
        }
        return sb.toString();
    }

    private String callStatusCsv(AnalyticsContext context, AnalyticsDateRange range) {
        CallStatusSummary s = analyticsService.getCallStatus(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("planned", "held", "notHeld", "cancelled", "heldRate"));
        sb.append(CsvWriter.row(s.getPlanned(), s.getHeld(), s.getNotHeld(), s.getCancelled(), s.getHeldRate()));
        return sb.toString();
    }

    private String conversionPeriodCsv(AnalyticsContext context, AnalyticsDateRange range) {
        ConversionPeriodSummary s = analyticsService.getConversionDuringPeriod(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("convertedDuringPeriod"));
        sb.append(CsvWriter.row(s.getConvertedDuringPeriod()));
        return sb.toString();
    }

    private String forecastCategoryCsv(AnalyticsContext context, AnalyticsDateRange range) {
        List<ForecastCategoryRow> rows = analyticsService.getPipelineByForecastCategory(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("category", "dealCount", "pipelineValue", "wonValue"));
        for (ForecastCategoryRow r : rows) {
            sb.append(CsvWriter.row(r.getCategory(), r.getDealCount(), r.getPipelineValue(), r.getWonValue()));
        }
        return sb.toString();
    }

    private String currentStageAgeCsv(AnalyticsContext context, AnalyticsDateRange range) {
        CurrentStageAgeSummary s = analyticsService.getCurrentStageAge(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("avgDealAgeDays", "avgCurrentStageAgeDays",
                "openDealsWithStageEnteredAt", "openDealsWithoutStageEnteredAt"));
        sb.append(CsvWriter.row(s.getAvgDealAgeDays(), s.getAvgCurrentStageAgeDays(),
                s.getOpenDealsWithStageEnteredAt(), s.getOpenDealsWithoutStageEnteredAt()));
        return sb.toString();
    }

    private String activityRatesCsv(AnalyticsContext context, AnalyticsDateRange range) {
        ActivityRatesSummary s = analyticsService.getActivityRates(context, range);
        ActivityRatesSummary.MeetingStatusSummary ms = s.getMeetingStatus();
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("metric", "value"));
        sb.append(CsvWriter.row("taskCompletionRate", s.getTaskCompletionRate()));
        sb.append(CsvWriter.row("taskOverdueRate", s.getTaskOverdueRate()));
        sb.append(CsvWriter.row("meetingPlanned", ms.getPlanned()));
        sb.append(CsvWriter.row("meetingHeld", ms.getHeld()));
        sb.append(CsvWriter.row("meetingNotHeld", ms.getNotHeld()));
        sb.append(CsvWriter.row("meetingCancelled", ms.getCancelled()));
        sb.append(CsvWriter.row("meetingHeldRate", ms.getHeldRate()));
        return sb.toString();
    }

    private String callDurationCsv(AnalyticsContext context, AnalyticsDateRange range) {
        CallDurationSummary s = analyticsService.getCallDuration(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("callsTotal", "callsWithDuration", "callsWithoutDuration",
                "totalCallMinutes", "averageCallDurationMinutes"));
        sb.append(CsvWriter.row(s.getCallsTotal(), s.getCallsWithDuration(), s.getCallsWithoutDuration(),
                s.getTotalCallMinutes(), s.getAverageCallDurationMinutes()));
        return sb.toString();
    }

    private String leadSourceCsv(AnalyticsContext context, AnalyticsDateRange range) {
        List<LeadSourcePerformanceRow> rows = analyticsService.getLeadSourcePerformance(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("sourceId", "source", "leadCount", "convertedCount", "conversionRate"));
        for (LeadSourcePerformanceRow r : rows) {
            sb.append(CsvWriter.row(r.getSourceId(), r.getSource(), r.getLeadCount(),
                    r.getConvertedCount(), r.getConversionRate()));
        }
        return sb.toString();
    }

    private String contactsPerAccountCsv(AnalyticsContext context, AnalyticsDateRange range) {
        List<ContactsPerAccountRow> rows = analyticsService.getContactsPerAccount(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("accountId", "accountName", "contactCount"));
        for (ContactsPerAccountRow r : rows) {
            sb.append(CsvWriter.row(r.getAccountId(), r.getAccountName(), r.getContactCount()));
        }
        return sb.toString();
    }

    private String accountsOwnerCsv(AnalyticsContext context, AnalyticsDateRange range) {
        List<AccountsByOwnerRow> rows = analyticsService.getAccountsByOwner(context, range);
        StringBuilder sb = headerRow(context, range);
        sb.append(CsvWriter.row("ownerUserId", "ownerDisplayName", "accountCount", "activeCount"));
        for (AccountsByOwnerRow r : rows) {
            sb.append(CsvWriter.row(r.getOwnerUserId(), r.getOwnerDisplayName(),
                    r.getAccountCount(), r.getActiveCount()));
        }
        return sb.toString();
    }

    private StringBuilder headerRow(AnalyticsContext context, AnalyticsDateRange range) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvWriter.row("scope", "from", "to"));
        sb.append(CsvWriter.row(context.scope(), range.from(), range.to()));
        return sb;
    }

    public String fileName(String kind, AnalyticsContext context, AnalyticsDateRange range) {
        String from = range.from().toString().replace(":", "-");
        String to = range.to().toString().replace(":", "-");
        return "analytics-%s-%s-%s.csv".formatted(kind, from, to);
    }
}