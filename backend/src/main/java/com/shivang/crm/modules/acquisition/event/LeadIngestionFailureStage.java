package com.shivang.crm.modules.acquisition.event;

public enum LeadIngestionFailureStage {
    MAPPING,
    VALIDATION,
    DEDUPLICATION,
    LEAD_CREATION,
    UNKNOWN
}
