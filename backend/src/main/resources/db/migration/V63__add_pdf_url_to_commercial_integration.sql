-- ============================================================================
-- V63__add_pdf_url_to_commercial_integration.sql
-- Commercial Integration V1: add pdfUrl for external Quotation/Invoice PDFs
-- Stores external PDF URL in integration mapping, isolated from core CRM
-- ============================================================================

ALTER TABLE integration_external_records ADD COLUMN IF NOT EXISTS pdf_url VARCHAR(2048);

-- No index needed; pdfUrl is retrieved via tenant+type+id lookup
