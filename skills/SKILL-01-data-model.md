# SKILL-01: CRM Core Data Model & Entities

## PURPOSE
Defines the canonical data model for this CRM project. Every agent working on any module MUST align to these entity definitions, relationships, and ownership rules. Never deviate from this mental model.

---

## 1. THE GOLDEN RULE

**Account (Company) → Contact (Person) → Deal (Opportunity)**
**Lead = raw unqualified prospect that converts INTO the above**
**Owner = the user responsible for that record**

---

## 2. ENTITY DEFINITIONS

### LEAD (Pre-Sales)
- An **unqualified potential customer**. Not yet a real customer.
- Sources: form submission, CSV import, cold call list, API
- Lifecycle: `New → Contacted → Qualified → Converted` (or `Disqualified`)
- On conversion: creates → Account + Contact + Deal
- Key fields: `name, email, phone, company, source, status, owner_id, tenant_id`

### ACCOUNT (Company / Organization)
- A **company or organization** you do business with
- 1 Account → Many Contacts, Many Deals
- Key fields: `name, industry, website, address, revenue, owner_id, tenant_id`

### CONTACT (Person)
- A **person working at an Account**
- Many Contacts → 1 Account; Contact can link to many Deals
- Key fields: `first_name, last_name, email, phone, job_title, account_id, owner_id, tenant_id`

### DEAL / OPPORTUNITY (Money)
- A **potential or ongoing sale** — this is the revenue unit
- Belongs to an Account, linked to Contacts (decision makers)
- Key fields: `name, value, stage, probability, close_date, account_id, owner_id, tenant_id`
- Pipeline stages: `Prospecting → Qualification → Proposal → Negotiation → Closed Won / Closed Lost`

### ACTIVITY
- Calls, meetings, tasks, notes — linked to any entity
- Key fields: `type, subject, due_date, status, entity_type, entity_id, owner_id, tenant_id`

---

## 3. OWNERSHIP MODEL (owner_id)

Every record has an `owner_id`. This is **non-negotiable**.

| Entity   | Owner Meaning                          |
|----------|----------------------------------------|
| Lead     | Who is following up on this lead       |
| Account  | Who manages this company relationship  |
| Contact  | Who handles communication with person  |
| Deal     | Who is closing this sale               |
| Activity | Who is responsible for this task/call  |

**owner_id vs assigned_to:**
- `owner_id` = primary responsible person (permanent)
- `assigned_to` = current handler (can be temporary, optional)

---

## 4. MANDATORY TABLE FIELDS

Every single CRM entity table MUST have:
```
tenant_id    -- multi-tenancy isolation
owner_id     -- record ownership
created_by   -- audit trail
created_at   -- timestamp
updated_at   -- timestamp
is_deleted   -- soft delete flag
```

---

## 5. ER RELATIONSHIP SUMMARY

```
Tenant
 ├── Users ── Roles ── Permissions
 │     └── Teams
 │
 ├── Leads
 ├── Accounts ── Contacts
 │        └── Deals ── Pipeline Stages
 │
 ├── Activities (linked to Lead/Contact/Account/Deal)
 ├── Custom Fields → Values
 ├── Workflows → Triggers → Actions
 ├── Notifications
 ├── Webhooks / Integrations
 └── Audit Logs / Files
```

---

## 6. LEAD CONVERSION FLOW

```
Lead (raw)
  ↓ qualify & convert
Account (company is created or matched)
  ↓
Contact (person is created)
  ↓
Deal (opportunity is created)
```
After conversion, the Lead record is marked `converted = true` and is no longer active.

---

## 7. COMMON MISTAKES — NEVER DO THESE

- ❌ Treating Contact as a Company (use Account for companies)
- ❌ Skipping the Account entity
- ❌ Mixing Lead and Contact (they are different lifecycle stages)
- ❌ No `owner_id` on records
- ❌ No clear conversion flow from Lead
- ❌ Storing `tenant_id` from frontend (always resolve server-side)
