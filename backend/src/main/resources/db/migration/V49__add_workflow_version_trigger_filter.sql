-- WF-38: Lead Created trigger filter (RUN WHEN) — optional predicate on trigger metadata / entity
-- Stored as JSONB {logic: "AND"|"OR", conditions: [{field, operator, value}, ...]}
-- Null = no filter (existing workflows: every LEAD.CREATED matches)
ALTER TABLE workflow_versions
    ADD COLUMN trigger_filter JSONB;
