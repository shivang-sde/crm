# Module: Records Platform (Generic Record Types)

| | |
|---|---|
| **Doc ID** | CRM-TD-M12 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\records` |
| **Evidence basis** | Repo inspection at commit `0fb9361`; untracked additions: `RiskSignalQueryRepository` (belongs to feedback module), `RecordWebhookIngress*` (tracked as A). Feature lineage WF-42…WF-53 tracked in commits (`5ac1b1c feat(records): implement end-to-end generic tenant-scoped Records platform`). |

## 1. Purpose & scope

Tenant-definable data records beyond the hardcoded CRM entities: custom record types with field schemas, display configs, generic record CRUD, mapping profiles (external payloads → records), **inbound record webhooks** with idempotency + delivery audit, and workflow `RECORD.RECEIVED` triggers.

**Excluded:** relations between custom records (no FK linking between record types), aggregate analytics (basic lists only).

## 2. API surface

| Controller | Base | Endpoints |
|---|---|---|
| `RecordTypeController` | `/api/v1/record-types` | POST; GET; GET/PATCH/DELETE /{id} |
| `RecordFieldController` | `/api/v1/record-types/{recordTypeId}/fields` | POST; GET; PATCH/DELETE /{fieldId} |
| `RecordDisplayConfigController` | `/api/v1/record-types/{recordTypeId}/display-config` | GET, PUT, DELETE |
| `CrmRecordController` | `/api/v1/records` | POST; GET; GET/PUT/PATCH/DELETE /{id} |
| `RecordMappingProfileController` | `/api/v1/record-mapping-profiles` | POST; GET; GET/PATCH/DELETE /{id}; POST /{id}/preview |
| `RecordWebhookController` | `/api/v1/record-webhooks` | POST; GET; GET/PATCH/DELETE /{id}; POST /{id}/rotate-secret |
| `RecordWebhookDeliveryController` | `/api/v1/record-webhooks` | GET /{webhookId}/deliveries, GET …/{deliveryId} |
| `RecordWebhookIngressController` (PUBLIC) | `/api/v1/records/webhooks` | POST /{webhookKey} |

## 3. Classes

- Services: `RecordTypeService`, `RecordFieldService`, `RecordDisplayConfigService`, `CrmRecordService` (+records sorting/pagination enhancements commit `9e53004`), `RecordMappingProfileService`, `RecordMappingService`, `RecordValidationService`, `RecordWebhookService`, `RecordWebhookIngestionService` (carries WF-51/WF-52 idempotency + delivery logic), `RecordWebhookIdempotencyService`, `RecordWebhookDeliveryQueryService`.
- Entities: `RecordType`, `RecordField`, `RecordDisplayConfig`, `RecordMappingProfile`, `CrmRecord`, `RecordWebhook`, `RecordWebhookDelivery`; enums `RecordFieldType`, `MappingProfileMode`, `MappingTransform`, `WebhookAuthMode`.
- Workflow integration: `RecordWorkflowEntityContextProvider`, `RecordWorkflowEntityContextTest`, `RecordRelatedRecordResolver` (workflow side).

## 4. Database

- `V51` record_types / record_fields / records; `V52` record_display_configs; `V53` record_mapping_profiles; `V54` record_webhooks; `V55` webhook security (secrets); `V56` record_webhook_deliveries; `V57` record field reference; `V58` delivery operational fields; `V59` record_type updated_by; `V60` generic updated_by columns. `V64` seeds records RBAC permissions (role provisioning trigger updated).

## 5. Auth, permissions, tenant isolation

- Module permission `records` seeded (V64) and wired into `DefaultRoleConfig ADMIN/MANAGER/MANAGER/EMPLOYEE` module lists consistently with `typescript` commit `3a3c4e8`.
- Tenant scoping: all services resolve via `TenantContext` and repositories use `tenantId` columns; public webhook resolves tenant via `webhookKey`.
- Record-webhook secret rotation supported (`POST /{id}/rotate-secret`).

## 6. Events

- `RECORD.RECEIVED` published from ingestion (wf52); delivery success/failure recorded retention in `record_webhook_deliveries` (operational fields V58).

## 7. Tests

`modules\records\`: `controller\RecordWebhookIngressTest`; `service\{CrmRecordServiceTest, RecordDisplayConfigServiceTest, RecordFieldServiceTest, RecordMappingProfileServiceTest, RecordMappingServiceTest, RecordMappingTransformationTest, RecordTypeServiceTest, RecordValidationServiceTest, RecordWebhookDeliveryOperationalTest, RecordWebhookIngestionWF52Test, RecordWebhookServiceTest}`; workflow side `WorkflowRecordReceivedTriggerAcceptanceTest`.
**Gaps:** no migration-contract test class for record tables like other modules have; no E2E covering record webhook → record → workflow trigger → action chain.

## 8. Dependencies & integration

- Storage-agnostic (JSON payloads), independent of other domain tables; used by workflow module through adapters — the only generic outbound projection of data besides feedback analytics.
- Workflow trigger registry + `record` UI at `(tenant)\records\{recordId}` settings pages (`(tenant)\settings\records\{recordTypeId}`, `record-webhooks`, `record-mappings`).

## 9. Limitations & risks

- Mapping transformations are code-defined (`MappingTransform` set); arbitrary scripting is intentionally unsupported.
- `RecordWebhookIdempotencyService` deduplicates on payload identity for inbound re-delivery; exact header replay window not documented.
