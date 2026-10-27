# Commercial Integration V1 — External Quotation/Invoice → CRMexternalType

> **Version:** V1 · **Status:** Production · **Base path:** `/api/v1/integrations/commercial`
> **Isolation:** This is an optional adapter. All external IDs live only in `integration_external_records` (`V61`/`V62`/`V63`). No external fields are added to `leads`, `accounts`, `contacts`, `deals`. Remove `com.shivang.crm.modules.commercial` + migrations to disable without touching core.

---

## 1. Core Business Contract

```
External Quotation/Invoice Platform
        │
        │ Customer Sync
        ▼
CRM Account + Contact
        │
        │ Document Sync
        ▼
CRM Account + Contact + External Document Mapping
```

* **Direction:** `ONE-WAY` — `External Platform → CRM`. The CRM never pushes to the external platform.
* **External platform is source of truth for:** its customers, quotations, invoices, and quotation/invoice PDFs.
* **CRM is source of truth for:** CRM-native records (Account, Contact, Deal, pipeline, ownership). The integration only creates/updates the small Account/Contact surface it owns (see §4).

---

## 2. Deal Independence

Quotation/invoice sync does **NOT**:

* create Deals
* update Deals
* assign Deals
* change Deal stages, amounts, probability, line items, or ownership

```
External Customer → CRM Account + Contact
External Quotation → CRM Account + Contact
External Invoice   → CRM Account + Contact
```

A `CRM Deal` is an independent sales opportunity managed only by CRM workflows. **Do not send `dealId`.** Do not create a Deal when sending a quotation/invoice. Document sync stores only a mapping `tenant + externalType + externalId → accountId + contactId + pdfUrl`.

---

## 3. Feature Flag

```
app.commercial-integration.enabled = true|false
env: COMMERCIAL_INTEGRATION_ENABLED=true
```

Capability `COMMERCIAL_INTEGRATION`. Default `false`.

When `false`, every sync/GET endpoint returns:

```json
{
  "success": false,
  "error": { "code": "FEATURE_DISABLED", "message": "Commercial integration is disabled for this deployment" }
}
```

HTTP `403`. Normal CRM functionality continues.

---

## 4. Authentication Contract

### 4.1 Primary header (use this)

```http
X-API-Key: <64-hex-key>
```

* The key is **per-tenant**. The CRM resolves the tenant **solely** from the API key hash (`SHA-256`, prefix lookup). Any `tenantId` you send in JSON is ignored.
* Treat the key as a **server-side secret**: never expose in browser JS, never commit to git, never put inside a `pdfUrl`. Store in your server env/secrets manager.

**Alternates accepted (for convenience):**
```http
X-Commercial-Token: <same 64-hex>
Authorization: Bearer <64-hex>   // no dots — JWTs contain "." and are NOT accepted here
```

Use `X-API-Key` unless you have a reason not to.

### 4.2 How a key is provisioned

A CRM tenant admin rotates/generates it (JWT auth required):

```http
POST /api/v1/admin/commercial/api-keys/rotate
Authorization: Bearer <tenant-admin-jwt>

=> 200 { "success": true, "data": { "apiKey": "abcd...64hex", "prefix": "abcd1234", "message": "Store this key securely..." } }
```

Platform admin variant: `POST /api/v1/admin/commercial/tenants/{tenantId}/api-keys/rotate`.

You will be given one key by the CRM owner. Save it once — the CRM stores only the hash.

### 4.3 HTTP responses

Missing/invalid key (any commercial endpoint):

```json
{ "success": false, "status": 401, "error": "UNAUTHORIZED", "message": "Missing X-API-Key header", "path": "/api/v1/integrations/commercial/..." }
{ "success": false, "status": 401, "error": "UNAUTHORIZED", "message": "Invalid integration API key", "path": "..." }
```

Feature disabled (after auth): `403` with `code: "FEATURE_DISABLED"` (see §3). The filter layer is before JWT; do not try to bypass with a CRM user JWT — commercial endpoints require the API key (except `GET /documents/...` which also accepts a CRM user JWT for **CRM UI** viewing — not for the external platform).

---

## 5. Customer Sync API

### 5.1 Endpoint

```http
POST /api/v1/integrations/commercial/customers/sync
Host: <CRM_BASE_URL>
Content-Type: application/json
X-API-Key: <COMMERCIAL_API_KEY>
```

### 5.2 Request body (actual DTO `CustomerSyncRequest`)

```json
{
  "externalCustomerId": "CUST-001",
  "customerName": "Acme Industries",
  "companyName": "Acme Industries Pvt Ltd",
  "email": "accounts@acme.com",
  "phone": "+919876543210",
  "gstin": "09ABCDE1234F1Z5"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `externalCustomerId` | string | **yes** | `@NotBlank` |
| `customerName` | string | no* | *At least one of `customerName` / `companyName` must be non-blank |
| `companyName` | string | no* | see above |
| `email` | string | no | if present and non-blank must contain `@` |
| `phone` | string | no | free-form, stored as-is |
| `gstin` | string | no | stored for correlation, participates in hash |

Extra JSON fields are ignored. Send only these fields.

### 5.3 Response (actual DTO `CustomerSyncResponse` wrapped in `ApiResponse`)

```json
{
  "success": true,
  "data": {
    "success": true,
    "accountId": "11111111-1111-1111-1111-111111111111",
    "contactId": "22222222-2222-2222-2222-222222222222",
    "created": true
  }
}
```

* `created: true` — this `tenant + CUSTOMER + externalCustomerId` mapping did not exist; a new `Account+Contact` was created.
* `created: false` — mapping already existed: either **NO-OP** (identical hash) or **UPDATE** (owned fields refreshed). No new Account/Contact is created.
* `accountId` / `contactId` are CRM `UUID`s. Store them if useful — never JPA entities.

### 5.4 CRM ownership boundary

Integration owns only:

* `accounts.name` ← `companyName ?? customerName`
* `accounts.email`, `accounts.phone`
* `contacts.firstName/lastName` ← split `customerName`, `contacts.email`, `contacts.phone`

It does **not** own `ownerId`, `deal stage`, `probability`, `forecast`, pipeline. Updates are limited to the fields above; concurrent/duplicate updates deduplicate safely.

### 5.5 Error responses

| HTTP | `error.code` | Meaning |
|------|--------------|---------|
| `401` | `UNAUTHORIZED` | missing/invalid API key |
| `403` | `FEATURE_DISABLED` | flag off |
| `400` | `VALIDATION_ERROR` | `externalCustomerId` blank, both names blank, email without `@` |
| `400` | `NO_USER_FOR_TENANT` | tenant has no user to act as `createdBy` (CRM admin must create a user first) |

---

## 6. Customer Identity

`externalCustomerId` is the external platform's **permanent** identifier.

Example `CUST-001` must continue representing the **same** external customer even if `customerName` / `companyName` / `phone` / `email` / `gstin` change. The CRM hashes `externalCustomerId|companyName|customerName|email(lower)|phone|gstin` for change detection; a name/phone change updates the mapped `Account/Contact` but does **not** require a new ID.

**Do not** create a new `externalCustomerId` because information changed — send an update for the same ID.

> **Isolation note:** `findSafeExistingMatch` is intentionally disabled. Two different `externalCustomerId` values never collapse via shared phone/email. Each external ID gets its own `Account+Contact` even if phone/email collides — this prevents burst `10 indistinguishable customers → 1` collapse.

---

## 7. Document Sync API

### 7.1 Endpoint

```http
POST /api/v1/integrations/commercial/documents/sync
Host: <CRM_BASE_URL>
Content-Type: application/json
X-API-Key: <COMMERCIAL_API_KEY>
```

### 7.2 Supported types

```text
QUOTATION
INVOICE
```

Case-insensitive. Anything else → `400 VALIDATION_ERROR`.

### 7.3 Request body (actual DTO `DocumentSyncRequest`)

```json
{
  "externalType": "QUOTATION",
  "externalId": "QUO-100",
  "externalUpdatedAt": "2026-09-17T10:00:00Z",
  "customer": { "externalCustomerId": "CUST-001" },
  "documentNumber": "Q-100",
  "documentDate": "2026-09-17",
  "currency": "INR",
  "subtotal": 100000,
  "tax": 18000,
  "total": 118000,
  "status": "DRAFT",
  "pdfUrl": "https://invoice.example.com/pdf/QUO-100-v1.pdf"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `externalType` | string | **yes** | `QUOTATION` or `INVOICE` |
| `externalId` | string | **yes** | external document ID |
| `externalUpdatedAt` | instant `ISO-8601` | no | included in hash; changes force an update even if amounts equal |
| `customer` | object | **yes** | must contain `externalCustomerId` |
| `customer.externalCustomerId` | string | **yes** | must have been synced via `POST /customers/sync` first |
| `documentNumber` | string | no | metadata, not mapped to Deal |
| `documentDate` | date `YYYY-MM-DD` | no | metadata |
| `currency` | string | no | e.g. `INR` |
| `subtotal` | number | no | |
| `tax` | number | no | |
| `total` | number | no | if present must be `>= 0` |
| `status` | string | no | e.g. `DRAFT`/`SENT`/`PAID` |
| `pdfUrl` | string | no | **validated** (see §11), stored with mapping |

`QUO-...` / `INV-...` are conventions, not enforced — any non-blank `externalId` is accepted.

### 7.4 Response (actual DTO `DocumentSyncResponse`)

```json
{
  "success": true,
  "data": {
    "success": true,
    "accountId": "11111111-1111-1111-1111-111111111111",
    "contactId": "22222222-2222-2222-2222-222222222222",
    "created": true,
    "pdfUrl": "https://invoice.example.com/pdf/QUO-100-v1.pdf"
  }
}
```

`created: true` only on first creation of `tenant + externalType + externalId`. No `dealId` is returned (Deal independence). `pdfUrl` echoes the stored value (may be `null` if not sent).

### 7.5 Semantics

* Resolves `customer.externalCustomerId → Account A + Contact C` (tenant-isolated). If not found → `400 CUSTOMER_NOT_SYNCED`.
* Creates/updates mapping `tenant + externalType + externalId → A + C + pdfUrl + hash`. **No Deal is touched.**
* Hash = `SHA-256(externalType|externalId|externalUpdatedAt|customerId|documentNumber|documentDate|currency|subtotal|tax|total|status|pdfUrl)`. Same hash → **NO-OP** (no DB write). Different hash → **UPDATE** (`last_synced_hash`, `external_updated_at`, `pdfUrl` refreshed). Ten concurrent creates for same key serialize via per-key lock + `REQUIRES_NEW` + `flush()` + `DataIntegrityViolationException → winner reload`; no duplicate mapping, no orphan leak (orphan Account/Contact cleaned on race).

---

## 8. Document Identity

`externalType + externalId` identifies the external document **within the tenant**.

```
QUOTATION + QUO-100
INVOICE   + INV-100   → different documents (different type)
QUOTATION + 100
INVOICE   + 100       → also different (constraint is tenant+type+id)
QUOTATION + 100 (Tenant A) vs QUOTATION + 100 (Tenant B) → isolated
```

The uniqueness constraint is `uq_external_record_tenant_type_id` on `(tenant_id, external_type, external_id)`. Same `externalId` can exist independently for `QUOTATION` and `INVOICE`.

---

## 9. Document Customer Ownership (Immutability)

Once

```
QUOTATION + QUO-100 → CUST-001
```

has been created, it **cannot** silently move to `CUST-002`.

```
POST /documents/sync { externalType:"QUOTATION", externalId:"QUO-100", customer:{externalCustomerId:"CUST-002"} }
```

returns:

```http
HTTP 409 Conflict
{
  "success": false,
  "error": { "code": "COMMERCIAL_DOCUMENT_CUSTOMER_CONFLICT", "message": "Document QUOTATION/QUO-100 already mapped to another customer" }
}
```

Same check applies to concurrent locked creation and to the fast-path update. **If you see 409, do not generate `QUO-100-1` to bypass it** — investigate the source-system relationship; the external document is attached to the wrong customer upstream.

---

## 10. Customer-First Requirement

Required order:

```
1. POST /customers/sync  externalCustomerId = CUST-001
2. POST /documents/sync  customer.externalCustomerId = CUST-001
```

If step 1 has not been done for the referenced customer:

```http
HTTP 400
{ "success": false, "error": { "code": "CUSTOMER_NOT_SYNCED", "message": "Customer CUST-001 must be synced before document" } }
```

Fix by syncing the customer first, then retry the document with the **same** `externalType/externalId`.

---

## 11. Idempotency

* Keep `externalCustomerId` stable for the lifetime of that customer.
* Keep `externalType + externalId` stable for the lifetime of that document.
* For retries, **re-send the same IDs**. Do NOT generate `QUO-100-1`, `QUO-100-2`, `CUST-001-v2`, etc.

| Scenario | What CRM does | Response |
|----------|---------------|----------|
| **First creation** (`NEW`) | creates mapping, sets `last_synced_hash` | `200` `created:true` `accountId/contactId` |
| **Unchanged retry** (identical hash) | **NO-OP**, no write | `200` `created:false` same `accountId/contactId` (`pdfUrl` echoed) |
| **Changed payload** (hash differs, same owner) | updates `hash/pdfUrl/externalUpdatedAt` | `200` `created:false` same `accountId/contactId` (updated `pdfUrl` if changed) |

`externalUpdatedAt` participates in the hash — sending a new timestamp alone counts as a change.

---

## 12. Document Updates — Before/After

**Initial:**

```json
{
  "externalType": "QUOTATION",
  "externalId": "QUO-100",
  "externalUpdatedAt": "2026-09-17T10:00:00Z",
  "customer": { "externalCustomerId": "CUST-001" },
  "documentNumber": "Q-100",
  "documentDate": "2026-09-17",
  "currency": "INR",
  "subtotal": 100000,
  "tax": 18000,
  "total": 118000,
  "status": "DRAFT",
  "pdfUrl": "https://invoice.example.com/pdf/QUO-100-v1.pdf"
}
```

**Updated (same quotation, amounts/status/pdf changed):**

```json
{
  "externalType": "QUOTATION",
  "externalId": "QUO-100",
  "externalUpdatedAt": "2026-09-17T11:00:00Z",
  "customer": { "externalCustomerId": "CUST-001" },
  "documentNumber": "Q-100",
  "documentDate": "2026-09-17",
  "currency": "INR",
  "subtotal": 120000,
  "tax": 21600,
  "total": 141600,
  "status": "SENT",
  "pdfUrl": "https://invoice.example.com/pdf/QUO-100-v2.pdf"
}
```

> This is an **update** to the same quotation. `externalType`/`externalId`/`customer.externalCustomerId` do not change. The CRM updates the mapping in place; no second mapping and no Deal is created.

---

## 13. PDF URL Contract

The external platform sends:

```json
"pdfUrl": "https://invoice.example.com/pdf/QUO-100-v1.pdf"
```

### CRM behavior

1. validates the URL (see below)
2. stores it in `integration_external_records.pdf_url` (`VARCHAR(2048)`) with the document mapping
3. echoes it in `POST /documents/sync` response (`data.pdfUrl`)
4. exposes it via `GET /documents/{externalType}/{externalId}` (`data.pdfUrl`)
5. CRM UI can render it as a link/button so the CRM user can open/view/download the external PDF

The CRM **does NOT**:

* download the PDF
* upload/proxy/host it
* generate it
* fetch it during sync (no outbound HTTP at sync time)

### Validation

* optional — omitted or blank is allowed (`null` stored)
* if present: `1–2048` chars, must be a valid URL with protocol `http` or `https` (`new URL(...)` check). `ftp://`, `file://`, relative paths → `400 VALIDATION_ERROR` (`pdfUrl must be http or https` / `must be a valid URL` / `too long`).
* whitespace is trimmed before storage and before hashing.

---

## 14. PDF URL Responsibility

| Responsibility | External Platform | CRM |
|----------------|-------------------|-----|
| Generate PDF | **Yes** | No |
| Host PDF | **Yes** | No |
| Mint/maintain URL | **Yes** | No |
| Ensure CRM user can access | **Yes** (share/signed URL) | No |
| Update URL when regenerated | **Yes** (resync with new `pdfUrl`) | No |
| PDF authN/Z | **Yes** (own session/signed URL) | No |
| Store URL | No | **Yes** |
| Associate with correct document | No | **Yes** (mapping) |
| Return/display URL | No | **Yes** |
| Fetch URL | No | **No** |

---

## 15. PDF URL Update

```
QUO-100  pdfUrl = https://.../QUO-100-v1.pdf   →   QUO-100  pdfUrl = https://.../QUO-100-v2.pdf
```

Continue using `externalId = QUO-100`. The new URL is part of the updated document payload (and part of the hash). Do NOT create `QUO-100-v2` as a document ID because the PDF changed.

---

## 16. PDF URL Accessibility

The URL must be usable by the intended CRM user at the time they click it.

* **Public URL** — simplest.
* **Signed / expiring URL** (e.g. S3 presigned) — the external app should **resync** a refreshed URL before expiry; otherwise the CRM link will 403/expired.
* **Authenticated URL** — if your PDF host requires your own auth session, ensure the CRM user has a way to obtain it (e.g. a short-lived signed URL minted at sync time). The CRM will not add its API key to the PDF request.

Do NOT place the CRM `X-API-Key` inside the `pdfUrl`.

---

## 17. Document GET API

```http
GET /api/v1/integrations/commercial/documents/{externalType}/{externalId}
Host: <CRM_BASE_URL>
X-API-Key: <COMMERCIAL_API_KEY>   // or CRM user JWT for in-app viewing
```

* **Who can call:** external platform with tenant API key (**recommended**), or a logged-in CRM user (`Authorization: Bearer <jwt>`) for UI viewing. The filter allows `GET` + valid JWT without API key; other commercial endpoints require the API key.
* **Path params:** `externalType` (`QUOTATION`/`INVOICE`, case-insensitive via `toUpperCase()`), `externalId` (exact).
* **Tenant:** resolved from API key (external) or from JWT (CRM user). Cross-tenant reads return `404`/`no mapping`.
* **Success (document exists):**

```json
{
  "success": true,
  "data": {
    "success": true,
    "accountId": "11111111-1111-1111-1111-111111111111",
    "contactId": "22222222-2222-2222-2222-222222222222",
    "pdfUrl": "https://invoice.example.com/pdf/QUO-100-v2.pdf",
    "created": false
  }
}
```

`pdfUrl` may be `null`. `created` is always `false` for GET.

* **Not found:**

```http
HTTP 404
{ "success": false, "error": { "code": "NOT_FOUND", "message": "IntegrationExternalRecord not found: QUOTATION/QUO-999" } }
```

* **Feature disabled:** `403 FEATURE_DISABLED`. **Unauthenticated:** `401` (missing/invalid key/JWT).

> `CUSTOMER` rows are not intended to be fetched via this endpoint (it only returns `QUOTATION`/`INVOICE` mappings).

---

## 18. Error Contract

All errors use `ApiResponse.error(code, message)` envelope: `{ "success": false, "error": { "code": "...", "message": "..." } }` (commercial auth filter's `401` uses `{ "success": false, "status": 401, "error": "UNAUTHORIZED", "message": "...", "path": "..." }`).

| HTTP | Code | Cause | Action |
|------|------|-------|--------|
| `400` | `VALIDATION_ERROR` | `@NotBlank` / `@NotNull` missing, `externalType` not `QUOTATION`/`INVOICE`, `total < 0`, `pdfUrl` malformed/too long/not http(s) | Fix payload, retry with **same** IDs |
| `400` | `CUSTOMER_NOT_SYNCED` | `documents/sync` references `customer.externalCustomerId` with no `CUSTOMER` mapping in tenant | Sync customer first, then retry document |
| `400` | `NO_USER_FOR_TENANT` | tenant has no user to act as `createdBy` (new tenant) | CRM admin creates a user in tenant, retry |
| `401` | `UNAUTHORIZED` | missing `X-API-Key` / invalid key / JWT invalid | Check key, never strip header, do not retry blindly |
| `403` | `FEATURE_DISABLED` | `app.commercial-integration.enabled == false` | Enable flag (`COMMERCIAL_INTEGRATION_ENABLED=true`) or stop calling |
| `403` | `FORBIDDEN` / `ACCESS_DENIED` | JWT lacks `ADMIN`/`PLATFORM_ADMIN` for `/admin/commercial/...` | Use correct admin JWT |
| `404` | `NOT_FOUND` | `GET /documents/{type}/{id}` — no mapping for tenant | Verify `externalType`/`externalId` and tenant key |
| `409` | `COMMERCIAL_DOCUMENT_CUSTOMER_CONFLICT` | document already mapped to different customer | Do not create new ID; fix upstream relationship |
| `500` | `INTERNAL_ERROR` | unexpected | Retry same IDs with backoff; report if persistent |

`BusinessException` codes `FEATURE_DISABLED` and `COMMERCIAL_DOCUMENT_CUSTOMER_CONFLICT` are mapped to `403` / `409` respectively in `GlobalExceptionHandler`; others map to `400`.

---

## 19. Retry Policy

### Retryable (retry same IDs, exponential backoff, idempotent)

* network timeout / connection reset / DNS hiccup
* `5xx` / `INTERNAL_ERROR`
* ambiguous response (e.g. client timed out but server may have committed) — **retry with same IDs** and compare `accountId/contactId/pdfUrl`; the second call will be `NO-OP` or `UPDATE`, never a duplicate.

### Do not blindly retry

* `400 VALIDATION_ERROR` / `CUSTOMER_NOT_SYNCED` / `NO_USER_FOR_TENANT` — fix payload/ordering
* `401 UNAUTHORIZED` — fix key, do not loop
* `403 FEATURE_DISABLED` — stop calling until flag enabled
* `409 COMMERCIAL_DOCUMENT_CUSTOMER_CONFLICT` — investigate data; creating `QUO-100-NEW` hides the bug
* `404` on GET — verify IDs/tenant

---

## 20. Testing the Integration as a Real Third-Party Application

> **Rule:** Test **only** against the **public HTTP API**. Behave as if you have no CRM DB or source access. Use `curl`, Postman, Node.js, Python, or your own app. Do NOT use `AccountRepository`, `ContactRepository`, `IntegrationExternalRecordRepository`, `EntityManager`, or direct DB writes.

---

## 21. Test Environment

Ask the CRM owner for:

```text
CRM_BASE_URL         e.g. https://crm.example.com  (no trailing slash)
COMMERCIAL_API_KEY   64-hex tenant key (server-side secret)
TEST_CUSTOMER_ID     e.g. TEST-CUST-001            (pick a fresh prefix per run)
TEST_QUOTATION_ID    e.g. TEST-QUO-001
TEST_INVOICE_ID      e.g. TEST-INV-001
```

Never commit the real key. Configure as:

```bash
export CRM_BASE_URL="https://crm.example.com"
export COMMERCIAL_API_KEY="abcd...64hex"
```

or `CRM_BASE_URL=<...>` / `COMMERCIAL_API_KEY=<...>` in your app's env manager.

---

## 22. curl Tests (copy-pasteable)

All `curl` below use the **actual** field names. Replace angle brackets with your values. Each assumes `_feature flag == true`.

### 1. Customer creation

```bash
curl -s -X POST "$CRM_BASE_URL/api/v1/integrations/commercial/customers/sync" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $COMMERCIAL_API_KEY" \
  -d '{
    "externalCustomerId": "TEST-CUST-001",
    "customerName": "Test Customer",
    "companyName": "Test Customer Pvt Ltd",
    "email": "test@example.com",
    "phone": "+919876543210",
    "gstin": "09ABCDE1234F1Z5"
  }' | jq
# => 200 { success:true, data:{ accountId:"...", contactId:"...", created:true } }
```

### 2. Customer retry (idempotency)

```bash
# same payload as (1)
curl -s -X POST "$CRM_BASE_URL/api/v1/integrations/commercial/customers/sync" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $COMMERCIAL_API_KEY" \
  -d '{ "externalCustomerId":"TEST-CUST-001","customerName":"Test Customer","companyName":"Test Customer Pvt Ltd","email":"test@example.com","phone":"+919876543210","gstin":"09ABCDE1234F1Z5" }' | jq
# => 200 created:false same accountId/contactId
```

### 3. Customer update (same ID, one field changed)

```bash
curl -s -X POST "$CRM_BASE_URL/api/v1/integrations/commercial/customers/sync" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $COMMERCIAL_API_KEY" \
  -d '{
    "externalCustomerId": "TEST-CUST-001",
    "customerName": "Test Customer Updated",
    "companyName": "Test Customer Pvt Ltd",
    "email": "test-updated@example.com",
    "phone": "+919876543210",
    "gstin": "09ABCDE1234F1Z5"
  }' | jq
# => 200 created:false same accountId/contactId (owner fields refreshed)
```

### 4. Quotation creation (with pdfUrl)

```bash
curl -s -X POST "$CRM_BASE_URL/api/v1/integrations/commercial/documents/sync" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $COMMERCIAL_API_KEY" \
  -d '{
    "externalType": "QUOTATION",
    "externalId": "TEST-QUO-001",
    "externalUpdatedAt": "2026-09-17T10:00:00Z",
    "customer": { "externalCustomerId": "TEST-CUST-001" },
    "documentNumber": "Q-TEST-001",
    "documentDate": "2026-09-17",
    "currency": "INR",
    "subtotal": 100000,
    "tax": 18000,
    "total": 118000,
    "status": "DRAFT",
    "pdfUrl": "https://invoice.example.com/pdf/TEST-QUO-001-v1.pdf"
  }' | jq
# => 200 { accountId:"...", contactId:"...", created:true, pdfUrl:"https://...v1.pdf" }
```

### 5. Invoice creation (same customer, separate ID)

```bash
curl -s -X POST "$CRM_BASE_URL/api/v1/integrations/commercial/documents/sync" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $COMMERCIAL_API_KEY" \
  -d '{
    "externalType": "INVOICE",
    "externalId": "TEST-INV-001",
    "externalUpdatedAt": "2026-09-17T10:00:00Z",
    "customer": { "externalCustomerId": "TEST-CUST-001" },
    "documentNumber": "INV-TEST-001",
    "documentDate": "2026-09-17",
    "currency": "INR",
    "subtotal": 100000,
    "tax": 18000,
    "total": 118000,
    "status": "SENT",
    "pdfUrl": "https://invoice.example.com/pdf/TEST-INV-001-v1.pdf"
  }' | jq
# => 200 created:true same accountId/contactId as (1), separate mapping (QUOTATION 001 vs INVOICE 001)
```

### 6. Quotation retry (same payload as §4)

```bash
# re-send §4 verbatim
# => 200 created:false same accountId/contactId/pdfUrl (NO-OP)
```

### 7. Quotation update (same ID, total/status/pdfUrl/updatedAt changed)

```bash
curl -s -X POST "$CRM_BASE_URL/api/v1/integrations/commercial/documents/sync" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $COMMERCIAL_API_KEY" \
  -d '{
    "externalType": "QUOTATION",
    "externalId": "TEST-QUO-001",
    "externalUpdatedAt": "2026-09-17T11:00:00Z",
    "customer": { "externalCustomerId": "TEST-CUST-001" },
    "documentNumber": "Q-TEST-001",
    "documentDate": "2026-09-17",
    "currency": "INR",
    "subtotal": 120000,
    "tax": 21600,
    "total": 141600,
    "status": "SENT",
    "pdfUrl": "https://invoice.example.com/pdf/TEST-QUO-001-v2.pdf"
  }' | jq
# => 200 created:false same accountId/contactId, pdfUrl now v2
```

### 8. PDF URL retrieval (document GET)

```bash
curl -s "$CRM_BASE_URL/api/v1/integrations/commercial/documents/QUOTATION/TEST-QUO-001" \
  -H "X-API-Key: $COMMERCIAL_API_KEY" | jq
# => 200 { data:{ accountId, contactId, pdfUrl:"https://...v2.pdf", created:false } }

# CRM user variant (no API key):
# curl -s "$CRM_BASE_URL/api/v1/integrations/commercial/documents/QUOTATION/TEST-QUO-001" \
#   -H "Authorization: Bearer <crm-jwt>" | jq
```

---

## 23. Edge-Case Test Matrix (A–Z)

For each: **Purpose · Request · Expected HTTP · Expected body · Business result · Must NOT happen**

| ID | Purpose | Request | Expected HTTP | Expected body / Result | Must NOT happen |
|----|---------|---------|---------------|------------------------|-----------------|
| **A** | New customer | `POST /customers/sync { externalCustomerId:"A-001", customerName:"Alice", companyName:"Alice Ltd", email, phone, gstin }` | `200` | `created:true`, new `accountId/contactId` | 500, duplicate |
| **B** | Customer retry (idempotent) | Same as A verbatim | `200` | `created:false` **same** IDs | New Account/Contact |
| **C** | Customer update | Same `externalCustomerId` + changed `email`/`phone`/`companyName` | `200` | `created:false` same IDs, owned fields refreshed | New mapping |
| **D** | New quotation | `POST /documents/sync { QUOTATION+QUO-A, customer:A-001, pdfUrl v1 }` | `200` | `created:true`, `pdfUrl` echoed, `accountId/contactId` == A | Deal created |
| **E** | New invoice (same customer) | `POST /documents/sync { INVOICE+INV-A, customer:A-001, pdfUrl }` | `200` | `created:true`, same `accountId/contactId` as A, separate mapping | Quotation overwritten |
| **F** | Quotation retry | Same as D verbatim | `200` | `created:false` same IDs + pdfUrl (NO-OP) | Duplicate |
| **G** | Invoice retry | Same as E verbatim | `200` | `created:false` same IDs | Duplicate |
| **H** | Changed quotation | Same `QUOTATION+QUO-A` + `total/status/pdfUrl/externalUpdatedAt` changed, same customer | `200` | `created:false` same IDs, hash updated, `pdfUrl` new | New mapping |
| **I** | Timestamp-only update | Same as H but only `externalUpdatedAt` changed | `200` | `created:false` (hash includes timestamp, so UPDATE) | NO-OP incorrectly |
| **J** | Document before customer | `POST /documents/sync { customer:"NEVER-SYNCED" }` | `400` | `CUSTOMER_NOT_SYNCED` | Mapping created |
| **K** | Out-of-order documents | Invoice `INV-OUT` before quotation `QUO-OUT` (both existing customer) | `200` each | Both `created:true` independently | Ordering required |
| **L** | Invalid externalType | `{ externalType:"ORDER" }` | `400` | `VALIDATION_ERROR` | Mapping created |
| **M** | Missing required fields | Omit `externalCustomerId` / `externalType` / `externalId` / `customer.externalCustomerId` | `400` | `VALIDATION_ERROR` | 500 |
| **N** | Same ID across types | `QUOTATION+100` then `INVOICE+100` (same customer) | `200` each | Two distinct mappings `QUOTATION 100` and `INVOICE 100` | Collision / overwrite |
| **O** | Same document → different customer | Create `QUO-100 → CUST-001`, then `POST QUO-100 → CUST-002` | `409` | `COMMERCIAL_DOCUMENT_CUSTOMER_CONFLICT` | Silent reassociation |
| **P** | Cross-tenant access | Tenant A key syncs `CUST-P`, Tenant B key `GET` or `POST` that tenant's doc | `404` or `CUSTOMER_NOT_SYNCED` (B sees nothing) | Tenant B data invisible | Leakage |
| **Q** | Invalid API key | `X-API-Key: deadbeef...` | `401` | `UNAUTHORIZED` | 200 |
| **R** | Missing API key | No header | `401` | `UNAUTHORIZED` `Missing X-API-Key` | 200/500 |
| **S** | Feature disabled | Set `COMMERCIAL_INTEGRATION_ENABLED=false`, then any sync/GET | `403` | `FEATURE_DISABLED` | 200 |
| **T** | Concurrent customer sync | 10× simultaneous `POST /customers/sync` same `externalCustomerId` same payload | `200` all | 1 mapping, 1 Account, 1 Contact, all responses same IDs, 0×500 | Duplicates, 500 |
| **U** | Concurrent quotation sync | 10× simultaneous `POST /documents/sync QUOTATION+QUO-CONC` same customer | `200` all | 1 mapping, same IDs/pdfUrl, 0×500 | Duplicates |
| **V** | Retry after ambiguous failure | Send doc, drop client before reading response, retry same IDs | `200` | Second retry `created:false` (NO-OP or UPDATE) | `QUO-100-1` workaround |
| **W** | Invoice before quotation | Already covered by K — either order accepted | `200` | Independent | Rejected order |
| **X** | Document lifecycle | `DRAFT→SENT→PAID` via successive syncs same ID | `200` each | Status evolves, same mapping | New IDs per status |
| **Y** | Multiple distinct customers | `CUST-Y1` vs `CUST-Y2` (different phone/email overlapping) | `200` each | Two Accounts/Contacts, never merged | Collapse to 1 |
| **Z** | Burst synchronization | 10 distinct customers + 10 quotes + 10 invoices in parallel | `200` all | 10 customer mappings + 20 document mappings, correct `accountId` per customer | Cross-wiring |

---

## 24. Concurrency Testing (real HTTP)

Run **only HTTP**; do NOT query the DB from the test harness.

```js
// Node.js — 10 concurrent customer syncs (same ID)
await Promise.all(Array.from({length:10}, () =>
  fetch(`${CRM_BASE_URL}/api/v1/integrations/commercial/customers/sync`, {
    method:"POST",
    headers:{ "Content-Type":"application/json", "X-API-Key": COMMERCIAL_API_KEY },
    body: JSON.stringify({ externalCustomerId:"CONC-CUST-001", customerName:"Conc Test", companyName:"Conc Ltd", email:"conc@example.com", phone:"+919900000001" })
  }).then(r=>r.json())
));
// Assert: every response 200, success true, same accountId/contactId, created true once then false, 0×500
```

Same for quotations/invoices:

```js
// 10 concurrent quotation syncs (same QUO, same customer)
await Promise.all(Array.from({length:10}, () =>
  fetch(`${CRM_BASE_URL}/api/v1/integrations/commercial/documents/sync`, {
    method:"POST",
    headers:{ "Content-Type":"application/json", "X-API-Key": COMMERCIAL_API_KEY },
    body: JSON.stringify({ externalType:"QUOTATION", externalId:"CONC-QUO-001", customer:{externalCustomerId:"CONC-CUST-001"}, total:118000, pdfUrl:"https://cdn.example.com/CONC-QUO-001.pdf" })
  }).then(r=>r.json())
));
```

Expected: `1 external document mapping`, all responses resolve to same `accountId/contactId/pdfUrl`, `0 unexpected 500`. Service serializes per `tenant:type:id` via `ConcurrentHashMap` lock + `REQUIRES_NEW` + `flush()` + winner-reload.

---

## 25. Security Tests

### Invalid API key → 401

```bash
curl -s -X POST "$CRM_BASE_URL/api/v1/integrations/commercial/customers/sync" \
  -H "X-API-Key: 0000000000000000000000000000000000000000000000000000000000000000" \
  -H "Content-Type: application/json" \
  -d '{"externalCustomerId":"X","customerName":"X"}' | jq
# => 401 { success:false, status:401, error:"UNAUTHORIZED", message:"Invalid integration API key" }
```

### Missing API key → 401

```bash
curl -s -X POST "$CRM_BASE_URL/api/v1/integrations/commercial/customers/sync" \
  -H "Content-Type: application/json" \
  -d '{"externalCustomerId":"X","customerName":"X"}' | jq
# => 401 Missing X-API-Key header
```

### Cross-tenant attempt → rejected, no leak

Using **Tenant A's key**, try to reference Tenant B's `externalCustomerId` without syncing it in A, or `GET` Tenant B's `QUO`:

```bash
# As Tenant A, reference a customer that only exists in Tenant B
curl -s -X POST "$CRM_BASE_URL/api/v1/integrations/commercial/documents/sync" \
  -H "X-API-Key: $TENANT_A_KEY" \
  -H "Content-Type: application/json" \
  -d '{"externalType":"QUOTATION","externalId":"B-QUO-ONLY","customer":{"externalCustomerId":"B-CUST-ONLY"}}' | jq
# => 400 CUSTOMER_NOT_SYNCED (Tenant A's view — B's data invisible)

curl -s "$CRM_BASE_URL/api/v1/integrations/commercial/documents/QUOTATION/B-QUO-ONLY" \
  -H "X-API-Key: $TENANT_A_KEY" | jq
# => 404 NOT_FOUND
```

You do not need to know internal tenant UUIDs — the API key *is* the tenant.

---

## 26. Critical Business Invariants

| Invariant | Expected |
|-----------|----------|
| **Customer identity** | Different `externalCustomerId` never collapse into one mapping. `Y` validates. |
| **Document identity** | Same `externalType+externalId` never creates duplicate mappings. `F/G/U` validate. |
| **Document ownership** | Existing document cannot silently move between customers (`409`). `O` validates. |
| **Tenant isolation** | Tenant A cannot read/write Tenant B data. `P` validates. |
| **PDF** | Stored/retrieved `pdfUrl` corresponds to correct document; updates visible via GET. §22.8 validates. |
| **Deal** | Commercial Integration creates/modifies **0 Deals**. |

---

## 27. Handoff Checklist for External Developer

```
[ ] Configure CRM_BASE_URL (no trailing slash)
[ ] Configure COMMERCIAL_API_KEY securely (env/secret, not frontend, not git)
[ ] Implement Customer Sync (POST /customers/sync)
[ ] Persist externalCustomerId per customer (permanent)
[ ] Implement Quotation Sync (POST /documents/sync externalType=QUOTATION)
[ ] Implement Invoice Sync   (POST /documents/sync externalType=INVOICE)
[ ] Persist external quotation/invoice IDs (permanent)
[ ] Send externalUpdatedAt (ISO-8601) for change detection
[ ] Send pdfUrl (http/https, <=2048 chars)
[ ] Keep PDF URL accessible (refresh expiring/signed URLs via resync)
[ ] Implement retry using SAME external IDs (never suffix -1/-2)
[ ] Implement document updates (same ID, new amounts/status/pdfUrl)
[ ] Handle CUSTOMER_NOT_SYNCED (sync customer first)
[ ] Handle VALIDATION_ERROR (400) — fix payload
[ ] Handle 401 UNAUTHORIZED — fix key
[ ] Handle 403 FEATURE_DISABLED — stop/disable or enable flag
[ ] Handle 409 COMMERCIAL_DOCUMENT_CUSTOMER_CONFLICT — fix upstream data
[ ] Handle 5xx retry with backoff, same IDs
[ ] Never send tenantId as the source of tenant identity
[ ] Never expose CRM API key in frontend
[ ] Never create a new external ID for retries
[ ] Never create a new external ID just because PDF changed
[ ] Never assume CRM creates Deals
[ ] Never place CRM API key inside pdfUrl
```

---

## 28. Responsibility Matrix

| Responsibility | CRM | External Platform |
|---|---|---|
| Customer source data | Consume | **Own** |
| `externalCustomerId` | Map | **Own** |
| Account / Contact (CRM) | **Own** | Reference |
| Quotation data | Consume | **Own** |
| Invoice data | Consume | **Own** |
| PDF generation | No | **Yes** |
| PDF hosting | No | **Yes** |
| `pdfUrl` value | Store / reference | **Provide / maintain** |
| `pdfUrl` accessibility | N/A | **Ensure user-accessible** |
| Deal creation / mutation | **No** | No |
| Authentication (`X-API-Key`) | Validate | **Secure** |
| Retry | Idempotent receiver | **Retry same identity** |
| Tenant isolation | Enforce | Respect |

---

## 29. Removal

* Code: delete `com/shivang/crm/modules/commercial` + `CommercialIntegrationAuthFilter` registration in `SecurityConfig` (1 line) + optional `TenantResolutionFilter` integration guard + `GlobalExceptionHandler` 403/409 branches.
* DB: drop `integration_external_records`, `commercial_integration_api_keys` (or keep data). `V62` already removes obsolete `deal_id`; `V63` adds `pdf_url`.
* Config: remove `app.commercial-integration.enabled`.

No core `accounts/contacts/deals` columns are touched.

---

## 30. Version

`Commercial Integration V1` — `ONE-WAY External → CRM` — `Account+Contact + External Mapping` — no Deal automation — `pdfUrl` stored.

