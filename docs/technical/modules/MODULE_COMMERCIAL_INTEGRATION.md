# Module: Commercial (External System Integration)

| | |
|---|---|
| **Doc ID** | CRM-TD-M06 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\commercial` |
| **Evidence basis** | Repo inspection + existing contract doc `backend\docs\COMMERCIAL_INTEGRATION_API.md` (prevailing authority for external behavior). Uncommitted files: `CommercialDocumentQueryService`, `CommercialApiKeyService/Repository` exist at commit time per git status; treat endpoints as verified code, deployment unverified. |

## 1. Purpose & scope

Bi-directional sync with the company's separate Commercial system: customer master sync (CRM owns account/contact identity; commercial pushes), document sync (quotation/invoice objects keyed externally), record-link queries for UI embedding. **Excluded:** finance posting, payment processing; deal linkage removed (V62 — partnership is deal-independent by design).

## 2. API surface

| Controller | Base | Endpoints | Auth |
|---|---|---|---|
| `CommercialIntegrationController` | `/api/v1/integrations/commercial` | POST /customers/sync, POST /documents/sync, GET /documents/{externalType}/{externalId} | `X-API-Key` (alt `X-Commercial-Token`) **or** CRM JWT |
| `CommercialIntegrationAdminController` | `/api/v1/admin/commercial` | GET /api-keys, POST /api-keys/rotate (ADMIN|PLATFORM_ADMIN|SUPER_ADMIN), POST /tenants/{tenantId}/api-keys/rotate (PLATFORM_ADMIN) | RBAC |
| `CommercialDocumentController` | `/api/v1/commercial-documents` | GET (CRM-side listing) | RBAC |

## 3. Classes & behavior

- `CommercialSyncService` — customer upsert idempotency (`externalType`+`externalId` uniqueness via `IntegrationExternalRecord`), contacts, and document sync; `CommercialDocumentQueryService` for lookups.
- `CommercialApiKeyService` — key issuance rotation; plain key never re-returned (rotated only).
- `CommercialIntegrationAuthFilter` (filters within auth filter chain, before JWT — see SecurityConfig comment) — validates `X-API-Key`, injects `ROLE_INTEGRATION` into `TenantContext` so downstream filters skip tenant re-resolution.
- `CommercialIntegrationFeatureService` — feature flag (CommercialIntegrationProperties); disabled → 404-like responses per contract doc.

## 4. Database

- `commercial_integration_api_keys`, `integration_external_records` (V61); `pdf_url` handling (V63); deal link removal (V62).

## 5. Contract doc summary (3 lines)

1. Customer sync: idempotent account+contact upsert keyed by external ids; CRM solidly owns identity; document keys (QUOTATION/INVOICE) immutable customer ownership and "customer-first" requirement.
2. Auth model: API-key header provisioned/rotated via admin API; 401/400/404 error contract; retry policy: idempotent retries allowed, blind failure retries discouraged.
3. Ops: curl test matrix A–Z incl. concurrency + cross-tenant-leak security probes; invariants + responsibility matrix; versioning note.

## 6. Tests

**No `modules\commercial` test classes exist** in `backend\src\test\java`. Coverage is indirect (doc test matrix only) + manual traces: `backend\e2e-commercial-test.js`, `backend\e2e-commercial-results.json` (local E2E run artifacts, uncommitted). This is the largest single-module test gap in the backend (see Technical Risks).

## 7. Security considerations

 - API-key material generated server-side; **stored as hash — raw key shown only once and never persisted** (`CommercialIntegrationAdminController.java:46–47` comment). AES-256 in-repo storage applies to connector/outbound credentials via `CredentialEncryptionService` (integration module), not to these API keys; validator requires correct 32-byte length of `integration.security.encryption-key` at prod start-up.
- Rotation is audit-visible only through key metadata (no audit log row created here — gap).
- Cross-tenant isolation proven only through documented test matrix, not automated regression tests.

## 8. Dependencies & integration points

- auth filter chain (position 1), account/contact services (upsert), RBAC (admin endpoints). No RabbitMQ events consumed/published by this module currently.

## 9. Limitations & risks

- No dedicated automated test suite for the external-facing API (customer/document sync endpoints) — highest-risk unverified surface.
- Key rotation UI exists in frontend (`components\commercial\`, `lib\api\commercial-integration.ts`) but operator auditability of rotations is not recorded (recommend adding to operations audit in future).
