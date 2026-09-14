-- ============================================================================
-- WF-55: Record Related CRM Records
-- Explicit one-hop relationships from Record to CRM entities via RecordField reference
-- ============================================================================

-- Add reference entity type for fields that represent a CRM relationship (one-hop, explicit)
ALTER TABLE record_fields
    ADD COLUMN reference_entity_type VARCHAR(30);

-- Allow REFERENCE type (generic) - reference_entity_type indicates target CRM type
-- Drop old check and recreate with REFERENCE included
ALTER TABLE record_fields DROP CONSTRAINT chk_record_fields_type;
ALTER TABLE record_fields ADD CONSTRAINT chk_record_fields_type CHECK (field_type IN (
    'TEXT','LONG_TEXT','INTEGER','DECIMAL','BOOLEAN','DATE','DATETIME','ENUM','URL','PHONE','EMAIL','JSON','REFERENCE'
));

ALTER TABLE record_fields ADD CONSTRAINT chk_record_fields_reference CHECK (
    (field_type = 'REFERENCE' AND reference_entity_type IN ('LEAD','CONTACT','ACCOUNT','DEAL','TASK','MEETING','CALL'))
    OR (field_type <> 'REFERENCE' AND reference_entity_type IS NULL)
);

CREATE INDEX idx_record_fields_reference ON record_fields(reference_entity_type) WHERE reference_entity_type IS NOT NULL;
