package com.shivang.crm.modules.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AN-15 A: conversion-EVENT reporting, distinct from the AN-10.1 created-window
 * metric. {@code convertedDuringPeriod} counts leads whose {@code convertedAt}
 * falls inside the selected [from, to) window and inside the caller's resolved
 * analytics scope.
 *
 * This is deliberately separate from {@code LeadMetrics.convertedLeads} (leads
 * created in the period that have since converted). The date semantics differ:
 * here the conversion event itself is the population basis, not lead creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversionPeriodSummary {

    /** Leads whose convertedAt is in [from, to). Conversion-event semantics. */
    private long convertedDuringPeriod;
}
