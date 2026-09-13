package com.shivang.crm.modules.lead.entity;

/**
 * Immutable creation-origin for LEAD.CREATED event metadata.
 * Kept strictly separate from business LeadSource (lead_sources FK).
 * Persisted only on event metadata (triggerContext), never on Lead row.
 */
public enum LeadCreationOrigin {
    MANUAL,
    LEAD_INGESTION,
    IMPORT;

    /**
     * Human label for workflow builder picker.
     */
    public String label() {
        return switch (this) {
            case MANUAL -> "Manual";
            case LEAD_INGESTION -> "Lead Ingestion";
            case IMPORT -> "Import";
        };
    }
}
