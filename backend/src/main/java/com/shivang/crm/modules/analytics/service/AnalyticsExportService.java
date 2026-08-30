package com.shivang.crm.modules.analytics.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.analytics.AnalyticsContext;
import com.shivang.crm.modules.analytics.AnalyticsDateRange;
import com.shivang.crm.modules.analytics.dto.AnalyticsSummaryResponse;
import com.shivang.crm.modules.analytics.dto.AnalyticsTrendResponse;
import com.shivang.crm.modules.analytics.export.CsvWriter;

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

    public String fileName(String kind, AnalyticsContext context, AnalyticsDateRange range) {
        String from = range.from().toString().replace(":", "-");
        String to = range.to().toString().replace(":", "-");
        return "analytics-%s-%s-%s.csv".formatted(kind, from, to);
    }
}