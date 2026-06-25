# SKILL-08: Custom Fields & Customization Engine

## PURPOSE
Defines how tenant-specific customization is implemented. This is **critical for SaaS viability** — without it the CRM cannot be sold. Every agent working on entity schemas, field rendering, or tenant configuration MUST follow this.

---

## 1. WHAT NEEDS TO BE CUSTOMIZABLE (Per Tenant)

- Custom fields on any module (lead, contact, account, deal, activity)
- Custom pipeline stages (for deals)
- Custom lead statuses
- Custom activity types
- Module layout configuration (which fields appear, in what order)
- Custom views and filters

---

## 2. CUSTOM FIELDS DB DESIGN

### Metadata Table (what fields exist)
```sql
custom_fields
  id            UUID
  tenant_id     UUID        -- per tenant
  module        VARCHAR     -- lead | contact | account | deal | activity
  field_name    VARCHAR     -- internal key (snake_case)
  label         VARCHAR     -- display label
  field_type    VARCHAR     -- text | number | date | boolean | select | multi_select | url | email
  is_required   BOOLEAN     -- validation flag
  options       JSONB       -- for select/multi_select: ["Option A", "Option B"]
  display_order INTEGER     -- render order
  is_active     BOOLEAN
```

### Values Table (actual data)
```sql
custom_field_values
  id            UUID
  entity_id     UUID        -- the lead/deal/contact ID
  entity_type   VARCHAR     -- lead | deal | contact | account
  field_id      UUID        -- references custom_fields.id
  value         TEXT        -- stored as text, cast on read
  tenant_id     UUID        -- always present
```

### Hybrid Alternative (for performance)
Store custom field values as JSONB directly on the entity table:
```sql
-- On leads table
custom_data JSONB DEFAULT '{}'

-- Example stored value
{ "source_campaign": "Q1 Google Ads", "budget_range": "10L-50L", "industry_segment": "BFSI" }
```

**Recommendation**: Use JSONB on entity table for MVP; migrate to values table if querying custom fields becomes a bottleneck.

---

## 3. CUSTOM PIPELINES

```sql
pipelines
  id, tenant_id, name, module (deal | lead), is_default, is_active

pipeline_stages
  id, pipeline_id, tenant_id, name, display_order, probability, is_won, is_lost
```

A deal always belongs to a pipeline. Stage changes are tracked via the workflow engine.

---

## 4. LAYOUT CONFIGURATION

```sql
layouts
  id, tenant_id, module, role_id (nullable = applies to all), config_json

-- config_json example:
{
  "sections": [
    {
      "title": "Basic Info",
      "fields": ["name", "email", "phone", "owner_id"]
    },
    {
      "title": "Custom Details",
      "fields": ["cf_budget_range", "cf_source_campaign"]
    }
  ]
}
```

---

## 5. HOW CUSTOM FIELDS FLOW IN THE API

### Fetching a Lead with Custom Fields
```json
{
  "id": "lead-uuid",
  "name": "Amit Kumar",
  "email": "amit@abc.com",
  "stage": "Qualified",
  "customFields": {
    "budget_range": "10L-50L",
    "source_campaign": "Q1 Google Ads"
  }
}
```

### Creating/Updating with Custom Fields
```json
POST /api/v1/leads
{
  "name": "Amit Kumar",
  "email": "amit@abc.com",
  "customFields": {
    "budget_range": "10L-50L"
  }
}
```

Backend validates custom field values against `custom_fields` definitions (required, type, options).

---

## 6. VALIDATION OF CUSTOM FIELDS

On save, for each custom field value:
1. Look up `custom_fields` by `field_id` and `tenant_id`
2. Check `is_required` — error if empty
3. Check `field_type` — validate format (date, number, email, URL)
4. Check `options` if `field_type = select` — value must be in list

---

## 7. CUSTOM STATUSES (Leads, Activities)

```sql
module_statuses
  id, tenant_id, module, name, color, display_order, is_terminal, is_default
```

`is_terminal`: marks closed/final states (e.g., "Converted", "Disqualified")
`is_default`: which status new records start with

---

## 8. NEVER DO THESE

- ❌ Hardcoding field lists in code (cannot be extended by tenants)
- ❌ Separate tables per tenant for custom fields (schema explosion)
- ❌ Storing typed values without type metadata (unvalidatable)
- ❌ Ignoring display_order (fields render randomly)
- ❌ Skipping tenant_id on custom_fields (cross-tenant field bleed)
