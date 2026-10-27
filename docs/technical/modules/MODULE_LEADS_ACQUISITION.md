# Module: Leads, Acquisition (Ingestion), Forms

| | |
|---|---|
| **Doc ID** | CRM-TD-M03 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\lead`, `modules\acquisition`, `modules\form` |
| **Evidence basis** | Repo inspection at commit `0fb9361`. Note: acquisition is the target of the `docs\prompts\workflow\prompt-audit.md` release-readiness audit and FE_BE_WF19–21 E2E audit reports (see Testing doc). |

## 1. Purpose & scope

Lead management (statuses, sources, conversion, assignment), configurable lead ingestion from external sources (webhook, direct, public form, CSV import, polling), tenant-addressable public ingress via `publicKey`, and a tenant-scoped form builder. **Excluded:** marketing-campaign attribution object, email/SMS sending (none), enrichment providers (none).

## 2. API surface

| Controller | Base | Endpoints |
|---|---|---|
| `LeadController` | `/api/v1/leads` | POST; GET, GET /{id}; PUT /{id}, /{id}/assign, /{id}/status; POST /{id}/convert; DELETE /{id} |
| `LeadEntityController` | `/api/v1/leads/{leadId}` | GET /histories, /notes; POST /notes; DELETE /notes/{noteId} |
| `LeadActivityController` | `/api/v1/leads/{leadId}` | GET /activities |
| `LeadStatusController` / `LeadSourceController` / `LeadCustomFieldController` | `/api/v1/lead-statuses`, `/api/v1/lead-sources`, `/api/v1/lead-custom-fields` | GET, POST; PUT/DELETE /{id} |
| `LeadIngestionConfigController` | `/api/v1/acquisition/configs` | CRUD |
| `LeadIngestionEventController` | `.../configs/{configId}/events` | GET list, GET /{eventId}, POST /{eventId}/reprocess |
| `LeadIngestionMappingController` | `.../configs/{configId}` | source-fields, target-fields, mappings CRUD, preview, validate-preview |
| `LeadIngestionImportController` | `.../configs/{configId}/import` | POST preview, POST (multipart) |
| `LeadIngestionPollingController` | `.../configs/{configId}/polling` | POST /test, /trigger; GET /status |
| `LeadIngestionPublicController`, `LeadIngestionDirectPublicController`, `LeadIngestionFormPublicController` | `/api/v1/public/{acquisition|direct|forms}/{publicKey}` | GET def, POST submit (**public unauthenticated ingress**) |
| `FormController` | `/api/v1/forms` | GET, POST; GET/PUT/DELETE /{formId}; POST /{formId}/publish, /unpublish, /duplicate |

## 3. Classes & services

- Lead: `LeadService`, `LeadStatusService`, `LeadSourceService`, `LeadCustomFieldService`; shared note/history engine `EntityNoteService`, `EntityHistoryService` (note engine consumed by account/contact too).
- Acquisition pipeline services (one per stage, all prefixed `LeadIngestion*`): `LeadIngestionConfigService`, `LeadIngestionIngressService`, `LeadIngestionValidationService`, `LeadIngestionMappingService`, `LeadIngestionProcessingService`, `LeadIngestionFormService`, `LeadIngestionImportService`, `LeadIngestionPollingService`, `LeadIngestionFieldDiscoveryService`, `LeadIngestionEventQueryService`, `LeadIngestionFailureService`, `LeadIngestionSystemActorService`, `LeadIngestionTargetFieldService`; transformations via `LeadIngestionTransformType`; scheduler `PollingScheduler`.
- Forms: `FormService` with `Form`, `FormField`.
- Workflow hooks: `LeadWorkflowEntityContextProvider`, `LeadWorkflowEntityUpdateAdapter`, `LeadWorkflowOwnerAssignmentAdapter`.

## 4. Database

- `leads, lead_statuses, lead_sources, lead_custom_fields, lead_activities, entity_notes, entity_history` (V6), plus `lead_creation_origins`; **ingestion**: `lead_ingestion_configs`, `lead_ingestion_field_mappings`, `lead_ingestion_events` (V30; hardening V45, V46; permission seeds V40); **forms**: `forms`, `form_fields` (V47).
- Outbox `crm_event_outbox` (V31) — event write-through for created leads.

## 5. Auth, permissions, tenant isolation

- Tenant endpoints guarded (`lead`/`acquisition`/`records`-style module:action permissions; RBAC seeds V40).
- Public ingress is keyed by per-tenant `publicKey` and resolves tenant via key (`LeadIngestionFormPublicController`); is on the SecurityConfig public whitelist.
- All ingestion lookups go through `findByIdAndTenantIdAndDeletedFalse` (services, V45/V46 hardening).

## 6. Events & scheduled jobs

- `LEAD.CREATED` (both manual and ingestion paths) → outbox → workflow triggers (`LeadCreatedOriginWorkflowTest` distinguishes origin).
- `LEAD.UPDATED / OWNER_CHANGED / STATUS_CHANGED / CONVERTED`.
- `PollingScheduler` — `@Scheduled` every `app.polling.interval-ms` (default 300000 ms) drives `LeadIngestionPollingService`.

## 7. Tests

- No dedicated module tests in the committed tree. Coverage exists indirectly: `docs\prompts\workflow\prompt-audit.md` audit scope, FE_BE_WF19–21 E2E reports (forms route fix, LEAD.UPDATED publisher proof), `frontend\tests\smoke\lead.spec.ts`, `acquisition.spec.ts`, plus `LeadCreatedOriginWorkflowTest` (workflow module).
- **Gap:** no backend unit tests for ingestion mapping/transform logic and CSV import — highest-value untested logic in codebase (see Technical Risks).

## 8. Dependencies

lead ← acquisition (ingestion writes leads); lead ← workflow (triggers); form ← acquisition; shared: `CustomerRelationshipValidator` (on convert), `EntityResolverService`.

## 9. Limitations & risks

- Reprocess (`/{eventId}/reprocess`) is operator-initiated only; no automatic requeue scheduling.
- Polling transport depends on outbound reachability of configured source URLs — subject to `OutboundHttpSecurityPolicy` only when routed through it (verify per use case; not unit-proven).
- CSV import row-error strategy documented in API responses (`LeadIngestion*DTOs`); bulk-rollback semantics not audited here.
