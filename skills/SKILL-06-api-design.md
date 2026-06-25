# SKILL-06: API Design Standards

## PURPOSE
Defines API structure, naming conventions, request/response formats, and design rules for this CRM. Every agent writing controllers, endpoints, or API specs MUST follow these standards for consistency.

---

## 1. API STRUCTURE (Module Folders)

```
api/
├── auth/           → login, refresh, logout
├── tenant/         → onboarding, settings
├── users/          → user management
├── leads/          → lead CRUD + conversion
├── contacts/       → contact management
├── accounts/       → account management
├── deals/          → deal + pipeline
├── activities/     → calls, tasks, meetings
├── workflows/      → workflow builder
├── notifications/  → notification center
├── integrations/   → webhooks, external APIs
├── reports/        → dashboards, analytics
└── common/         → health, metadata, enums
```

---

## 2. URL CONVENTIONS

Base path: `/api/v1/`

Standard CRUD pattern:
```
GET    /api/v1/{module}           → list (paginated)
POST   /api/v1/{module}           → create
GET    /api/v1/{module}/{id}      → get one
PUT    /api/v1/{module}/{id}      → full update
PATCH  /api/v1/{module}/{id}      → partial update
DELETE /api/v1/{module}/{id}      → soft delete
```

Examples:
```
GET    /api/v1/leads
POST   /api/v1/leads
GET    /api/v1/leads/{id}
PUT    /api/v1/leads/{id}
DELETE /api/v1/leads/{id}
POST   /api/v1/leads/{id}/convert    → lead conversion action
GET    /api/v1/deals/{id}/activities → sub-resource
```

---

## 3. STATELESS REQUESTS

Every request must be self-contained. NEVER assume session state. Each request carries:
- `Authorization: Bearer <access_token>` header
- Tenant context resolved server-side from JWT
- User identity resolved server-side from JWT

**Frontend NEVER sends `tenant_id` as a parameter.**

---

## 4. STANDARD RESPONSE FORMAT

### ✅ Success Response
```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "page": 1,
    "size": 20,
    "total": 120,
    "totalPages": 6
  }
}
```

For single-item responses, `meta` is omitted:
```json
{
  "success": true,
  "data": { "id": "...", "name": "Infosys Deal", ... }
}
```

### ❌ Error Response
```json
{
  "success": false,
  "error": {
    "code": "ACCESS_DENIED",
    "message": "You do not have permission to view this deal",
    "field": null
  }
}
```

For validation errors:
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "fields": {
      "email": "Invalid email format",
      "phone": "Phone number is required"
    }
  }
}
```

---

## 5. STANDARD ERROR CODES

| Code                  | HTTP Status | Meaning                             |
|-----------------------|-------------|-------------------------------------|
| VALIDATION_ERROR      | 400         | Request body failed validation      |
| UNAUTHORIZED          | 401         | No valid token                      |
| ACCESS_DENIED         | 403         | Valid token but no permission       |
| NOT_FOUND             | 404         | Resource doesn't exist for tenant   |
| CONFLICT              | 409         | Duplicate resource                  |
| RATE_LIMITED          | 429         | Too many requests                   |
| INTERNAL_ERROR        | 500         | Unexpected server error             |

---

## 6. PAGINATION, FILTERING & SORTING

### Request Parameters
```
GET /api/v1/leads?page=1&size=20&sort=created_at&order=desc&status=new&owner_id=xyz
```

Standard params:
- `page` (default: 1)
- `size` (default: 20, max: 100)
- `sort` (field name)
- `order` (asc | desc)
- Entity-specific filters: `status`, `owner_id`, `stage`, `assigned_to`, etc.

### Response Meta
Always include pagination metadata:
```json
"meta": {
  "page": 1,
  "size": 20,
  "total": 120,
  "totalPages": 6,
  "hasNext": true,
  "hasPrev": false
}
```

---

## 7. BULK OPERATIONS

```
POST /api/v1/leads/bulk-import       → CSV/JSON import
POST /api/v1/leads/bulk-update       → update multiple records
POST /api/v1/leads/bulk-delete       → soft delete multiple
POST /api/v1/leads/bulk-assign       → assign owner to multiple
```

---

## 8. SEARCH ENDPOINT

```
GET /api/v1/search?q=infosys&modules=leads,contacts,accounts
```

Returns results across modules for global search.

---

## 9. VERSIONING STRATEGY

- Current: `v1`
- When breaking changes needed → introduce `v2` (keep `v1` running for 6 months minimum)
- Non-breaking changes (new fields, new optional params) → no version bump needed

---

## 10. REQUEST VALIDATION RULES

- All IDs: UUID format
- Email: valid email format + lowercase normalized
- Phone: E.164 format preferred
- Dates: ISO 8601 (`2024-01-15T10:30:00Z`)
- Currency values: integer in smallest unit (paise for INR) OR decimal with currency code
- Enums: uppercase strings matching defined values

---

## 11. NEVER DO THESE

- ❌ Accept `tenant_id` from request body
- ❌ Use inconsistent response format (some endpoints returning raw objects)
- ❌ Skip pagination (unbounded list queries crash the DB)
- ❌ Use verbs in URLs (`/getLeads`, `/createDeal`)
- ❌ Return 200 with an error body (always use proper HTTP status)
- ❌ Expose internal IDs, stack traces, or DB errors in responses
- ❌ Versioned internal logic (business logic lives in services, not controllers)
