package com.shivang.crm.modules.feedback.service;

import java.util.List;

import com.shivang.crm.modules.feedback.dto.RiskSignalResponse;

public record RiskSignalPage(
        List<RiskSignalResponse> signals,
        int page,
        int size,
        long totalElements) {

    public int totalPages() {
        return size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
