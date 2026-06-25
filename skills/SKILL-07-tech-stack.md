# SKILL-07: Tech Stack & Architecture Decisions

## PURPOSE
Defines the approved tech stack for this CRM project. Every agent generating code, configs, or infrastructure MUST use ONLY the approved stack. Do not suggest alternatives unless explicitly asked.

---

## 1. BACKEND (Core Engine)

**✅ Chosen: Java + Spring Boot**

| Component        | Technology             | Purpose                              |
|------------------|------------------------|--------------------------------------|
| Framework        | Spring Boot            | Core application framework           |
| Security         | Spring Security        | Auth, JWT, filter chain              |
| ORM              | Hibernate / JPA        | Database access                      |
| Validation       | Jakarta Bean Validation| Request validation                   |
| Caching          | Spring Cache + Redis   | RBAC cache, session, rate limiting   |
| Events (local)   | Spring Events          | In-process event bus                 |

---

## 2. FRONTEND (User Interface)

**✅ Chosen: React + Next.js**

| Component        | Technology       | Purpose                              |
|------------------|------------------|--------------------------------------|
| Framework        | Next.js + React  | SSR, routing, component model        |
| UI Library       | shadcn/ui        | Component primitives                 |
| Styling          | Tailwind CSS     | Utility-first CSS                    |
| Data Fetching    | TanStack Query   | Server state, caching, invalidation  |
| State Management | Zustand          | Lightweight client state             |
| Forms            | React Hook Form  | Form state + validation              |

---

## 3. DATABASE (Primary)

**✅ Chosen: PostgreSQL**

Reasons:
- Strong relational modeling (CRM is inherently relational-heavy)
- JSONB for flexible custom fields storage
- Supports multi-tenancy via `tenant_id` column pattern
- Excellent indexing capabilities

Key patterns:
- `tenant_id` on every table (shared DB strategy)
- JSONB for `custom_field_values` (hybrid approach)
- Proper indexes on: `tenant_id`, `owner_id`, `status`, `created_at`

---

## 4. CACHING

**✅ Chosen: Redis**

Used for:
- RBAC permissions cache (per user per tenant)
- JWT refresh token storage / blacklist
- Session management
- Rate limiting counters
- Frequently accessed CRM data (tenant settings, role hierarchy)
- Lightweight notification queues

Cache key patterns:
```
rbac:{tenantId}:{userId}:permissions     → RBAC data
tenant:{tenantId}:settings               → Tenant config
session:{userId}:teams                   → User team memberships
```

---

## 5. MESSAGING / EVENT SYSTEM

**✅ Phase 1: RabbitMQ** (current)
**✅ Phase 2: Apache Kafka** (scale)

Used for:
- Workflow engine events (LEAD_CREATED, DEAL_UPDATED, etc.)
- Notification delivery
- Activity log processing
- Integration webhook triggers
- Audit log streaming

**Never skip the event system** — synchronous processing will not scale.

---

## 6. SEARCH

**✅ Chosen: Elasticsearch**

Used for:
- Global search across leads, contacts, accounts, deals, notes
- Autocomplete / typeahead
- Advanced filtering
- Full-text search on notes and activities

Sync strategy: Publish events → consumer syncs to Elasticsearch after DB write.

---

## 7. FILE STORAGE

**✅ Chosen: Amazon S3**

Used for:
- Record attachments (contracts, invoices, documents)
- CSV import files
- Profile images
- Report exports

Access: Generate pre-signed URLs (never expose S3 paths directly).

---

## 8. AUTHENTICATION (Scalable Path)

| Phase     | Technology           | When                        |
|-----------|----------------------|-----------------------------|
| Phase 1   | JWT + Spring Security| Current — standard JWT flow |
| Phase 2   | Keycloak             | SSO, OAuth2, multi-tenant   |

Keycloak features (when needed):
- Multi-tenant realm support
- SSO for enterprise customers
- OAuth2 for third-party integrations
- Social login

---

## 9. DEVOPS & DEPLOYMENT

| Component   | Technology          |
|-------------|---------------------|
| Container   | Docker              |
| Orchestration | Kubernetes (later) |
| Cloud       | AWS / Azure / GCP   |
| CI/CD       | GitHub Actions      |
| Monitoring  | Prometheus + Grafana|
| Logging     | ELK Stack           |

---

## 10. INTEGRATION LAYER

| Type              | Technology        |
|-------------------|-------------------|
| REST APIs         | Spring Boot REST  |
| Webhooks          | Outgoing via HTTP |
| CPaaS (calls/SMS) | Twilio            |
| WhatsApp          | Twilio WhatsApp   |
| Email             | SES / SendGrid    |
| GraphQL (optional)| later phase       |

---

## 11. SCALING ROADMAP

### Phase 1 — Monolith (Current)
```
Spring Boot Monolith → PostgreSQL → Redis → RabbitMQ
```

### Phase 2 — Extract Heavy Services
```
Extract: Workflow Service, Notification Service, Integration Service
```

### Phase 3 — Full Microservices
```
Add: Kafka, Elasticsearch, Keycloak, Kubernetes
```

---

## 12. NEVER DO THESE

- ❌ Use MySQL (missing JSONB, weaker for CRM modeling)
- ❌ Store files in the DB (use S3)
- ❌ Implement workflow logic in the API thread (use event queue)
- ❌ localStorage for JWT tokens (use HTTP-only cookies for refresh)
- ❌ Hardcode cloud region or bucket names
- ❌ Use a different stack without explicit project decision
