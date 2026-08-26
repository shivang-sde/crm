package com.shivang.crm.modules.analytics.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shivang.crm.modules.analytics.AnalyticsScope;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalyticsSummaryResponse {

    private AnalyticsScope scope;
    private Instant from;
    private Instant to;
    private long leads;
    private long contacts;
    private long deals;
    private long tasks;
    private long calls;
    private long meetings;
}
