# Data Model & Migrations

| | |
|---|---|
| **Doc ID** | CRM-TD-05 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 |
| **Evidence basis** | Migration counts verified 2026-10-26 in the **working tree** (`backend\src\main\resources\db\migration\`: 74 files, `V1…V74` + one stray copy file). **Committed baseline (`origin/main` = `0fb9361`) contains only 64 migrations**; V65–V74 are staged but uncommitted. Full per-table assertions live in `*MigrationContractTest` classes. Stray file: `V20__add_audit_columns_to_connector_webhook_events copy.sql` backup. A predecessor ER doc `docs\db-entity-relationship.java` was deleted in the working tree — this doc now carries schema documentation; a generated ERD remains a follow-up. |

## 1. Approach

- **Single shared PostgreSQL schema; multi-tenancy through a `tenant_id` column** on all tenant-owned tables (no per-tenant schemas, no Hibernate multi-tenant plugin). Entities extend `TenantOwnedEntity` (non-null `tenant_id`, `owner_id`, `created_by`, `deleted` soft delete).
- Flyway is the single schema authority (`validate-on-migrate: true` in prod); production safety net trigger for role provisioning: `V5__create_tenant_role_trigger.sql` (corrected in V50).
- Audit columns standardized: `created_at/updated_at/created_by/updated_by` (V60) — generic updates.

## 2. Migration index (grouped)

| Group | Migrations | Tables/objects |
|---|---|---|
| Auth/tenancy | V1, V2, V3, V4, V5, V27, V50 | users, refresh_tokens, tenants, modulith tables, roles/permissions/role_permissions/user_roles, reseller_tenants, invitations, role-provisioning trigger |
| Leads/notes/history | V6 | leads, lead_statuses, lead_sources, lead_custom_fields, lead_activities, entity_notes, entity_history |
| Accounts/contacts/activities | V7 | accounts, contacts, account_custom_fields, contact_custom_fields, activities |
| Calls/telephony | V11, V13, V14, V15, V16, V17, V19, V20, V21 | tasks, calls, meetings, call provider/opening/connect/layout tables, webhook mappings/audit, credential scope |
| Deals | V10, V26, V42, V69 | deals, deal_stages, deal_custom_fields, deal_line_items, stage_entered_at, line currency_code |
| Offerings/entitlements | V25, V28, V67, V68 | offerings, customer_entitlements, access-scope fix, assign seed |
| Tasks extr. (recurrence) | V24 | recurrence_schedules |
| Reminders/notifications | V22, V23 | reminders, user_notifications |
| Demo data | V29 | tenant_demo_installations, demo_data_records |
| Ingestion | V30, V31, V40, V45, V46 | lead_ingestion_configs/field_mappings/events, **crm_event_outbox**, permission seeds, hardening |
| Workflow | V32–V41, V49 | workflows, versions, nodes, edges, executions, node executions, identity, reliability metadata, idempotency, provenance, lineage, trigger filter |
| Integration outbound | V36, V43, V44, V48 | outbound_http_connections(+) |
| Quoted historical | V51–V60 | records platform tables (record_types/fields/display_configs/mapping_profiles/webhooks/deliveries) incl. V55 webhook security |
| Forms | V47 | forms, form_fields |
| Records RBAC | V64 | records permission seeds |
| Operations | V65, V66 | crm_event_outbox DEAD state, operator_message_actions + seeds |
| Support | V70–V73 | tickets, ticket_messages, SLA fields + permission triggers |
| Feedback | V74 | feedback + permission seeds |

(Authoritative file list: `backend\src\main\resources\db\migration\` — 74 files, verified.)

## 3. Key relationships (logical)

- One tenant → many everything. `accounts 1—N contacts` (validated via `CustomerRelationshipValidator.requireLiveAccount`), `account 1—N deals`, `deal 1—N deal_line_items`, `offering 1—N deal_line_items` (currency snapshot line-level), `deal (CLOSED_WON) 1—N customer_entitlements` (renewal chain via `renewedFrom/To`, `renewalDealId`), `ticket 1—N ticket_messages`, `feedback 0—1 ticket` (complaint ticket link), `call_provider_links → calls/call_opening_events`.
- `activities`/`entity_notes`/`entity_history` are polymorphic (`entity_type` + `entity_id`, no FKs) — access computed via parent guards.
- Outbox: `crm_event_outbox` consumed by `WorkflowExecution*` execution tables (records), joined via execution lineage/idempotency tables.

## 4. Data-quality & hygiene findings (no changes made)

- Stray file: `V20__add_audit_columns_to_connector_webhook_events copy.sql` — migrate copy left in migration folder; harmless to Flyway ordering but should be moved out of the migration set (doc-only recommendation).
- `docs\db-entity-relationship.java` deleted in working tree — ERD artifact missing; this doc supersedes it structurally but a generated ERD (e.g., schemaSpy/dotgraph) would strengthen the data design record.
- Soft-delete policy is uniform (`deleted` flag) — activity table is the exception (no tenant columns; see accounts module doc).
- No foreign keys on polymorphic tables — referential integrity relies on service-layer validators + tests (documented design; note for DB admin awareness).

## 5. Backup/ops

- Dev dump: `prod_backup.dump` (root, 456KB, 12-09-2026) — **contains possibly current tenant data; treat as sensitive (see Technical Risks).** Prod backup/restore procedure is not documented (ops doc gap).
