# Architecture

| | |
|---|---|
| **Doc ID** | CRM-TD-02 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 |
| **Evidence basis** | Commit `0fb9361` + uncommitted working tree; all paths verified by inspection. |

## 1. High-level architecture

```
Browser (Next.js SSR/RSC pages + TanStack Query SPA parts)
   │  Bearer JWT (access) + HTTP-only refresh cookie
   ▼
nginx (nginx\default.conf)  ──► frontend:3000 (SSR/RSC)
   │                           └─ also talks to backend directly for API calls
   ▼
backend:8080 (Spring Boot, stateless)
   ├─ Filter chain: CommercialIntegrationAuthFilter → JwtAuthenticationFilter
   │                → TenantResolutionFilter → RbacFilter  (SecurityConfig.java)
   ├─ 29 domain modules (modules\*) + shared\ infra
   ├─ PostgreSQL (Flyway-managed, shared schema, tenant_id columns) :5433 dev
   ├─ RabbitMQ (outbox relay → topic exchange crm.events → crm.workflow.events) 
   ├─ Redis (permissions cache, blacklist, throttle, leases)
   └─ Micrometer + Prometheus metrics (pom.xml)
```

## 2. Backend modularization

- **Spring Modulith** declared per module (`AuthModule`, `AnalyticsModule`, `LeadConfig` etc.); `V2__add_spring_modulith_tables.sql` persists events for Modulith verification.
- **Shared kernel** (`com\shivang\crm\shared\`): `base\` (BaseEntity, TenantOwnedEntity), `event\` (canonical events + outbox + Rabbit config), `service\` (PermissionService role helpers, CustomerRelationshipValidator, EntityResolverService, EntityPhoneResolver), `exception\` GlobalExceptionHandler, `resilience\` (RedisAvailabilityGuard, InfrastructureMetrics), `config\` (Redis/Rabbit, RabbitPublisherReturnConfig), `dto\enums\validation\`.
- Module dependency direction: domain modules → shared; workflow module depends on canonical events and per-entity adapter interfaces implemented **in workflow** and registered from entity modules' services (`WorkflowEntityContextProviderRegistry` etc.). No cross-entity controller calls except admin/dashboard aggregators (SalesDashboardService, Customer360Service, analytics, demo data) which call other modules' services directly — see Known Limitations for circularity notes.

## 3. Transactional outbox event flow (canonical)

```
Domain transaction: entityChanged(...) → CanonicalCrmEventPublisher
     → CanonicalCrmEventOutboxService.enqueue (same tx, row in crm_event_outbox, status PENDING)
@Scheduled OutboxEventPublisher (app.outbox.poll-delay-ms; 30s dev / 5s prod)
     → OutboxEventClaimService claims batch (V65 adds terminal DEAD state)
     → publish to topic exchange crm.events, routing key crm.{aggregateType}.{eventType}
     → publisher-confirms; setMandatory(true) + ReturnedMessageTracker (RabbitPublisherReturnConfig)
       unroutable → UnroutableMessageException → row marked DEAD
WorkflowEventConsumer (@RabbitListener crm.workflow.events, MANUAL ack, prefetch 50)
     → WorkflowExecutionRuntimeService starts/passes executions
     → exhausts stateless retries (max 3, exp backoff) → WorkflowMessageRecoverer → DLQ
     → operator re-drive via modules\operations (explicit, audited)
CausalEventContext prevents workflow-triggered re-publications from recursing across chains.
```
Classes: `shared\event\{CanonicalCrmEvent,Publisher,OutboxService,OutboxEvent,ClaimService,Publisher}.java`,
`shared\config\RabbitPublisherReturnConfig.java`, `shared\event\ReturnedMessageTracker.java`,
`modules\workflow\config\{WorkflowRabbitConfig,WorkflowMessageRecoverer}.java`, `modules\operations\service\*`.

## 4. Runtime scheduling & concurrency (summary)

All scheduled work is DB-claim based (leases/claims in Postgres + Redis where applicable), designed for multi-instance deployment. The **complete verified job/interval table lives in `INTEGRATIONS_AND_ASYNC_PROCESSING.md` §5** (single source of truth) — it covers: outbox publisher (30s dev / 5s prod), workflow execution dispatcher + retry dispatcher (45s), execution recovery (60s), acquisition polling (5 min), entitlement lifecycle + renewal-task schedulers (daily), recurrence generation (60s), reminder processing (30s), ticket SLA breach scan (60s).

All intervals are configuration-tunable in `application.yaml` / `application-prod.yml` (`app.*` prefixes).

## 5. Security architecture (summary)

Filter chain + JWT claims + tenant context + DB-backed RBAC + record scopes. Full detail: `SECURITY_AND_TENANT_ISOLATION.md`.

## 6. Frontend architecture

- Route groups: `(auth)` unauthenticated, `(plateform)` (sic — platform: superadmin/reseller/tenants), `(tenant)` tenant dashboard modules, `(home)`, `forms`, `softphone`, `softphone-test`.
- Data access: `lib\api\*` axios clients + `lib\hooks\*` TanStack Query hooks; permissions via `lib\hooks\usePermissions.ts` mirroring backend module:action strings; route guards `components\shared\{ProtectedRoute,RouteGuard,NoAccessNotice}`.
- Workflow builder uses `@xyflow/react` canvas with node dialogs; executions inspector at `(tenant)\workflows\{id}\executions\[executionId]`.
- Voice stack (`lib\voice\`): runtime + `GenericSipAdapter` (sip.js), popup softphone window bridge, audio/hold diagnostics [uncommitted].
- Tenant isolation mirror: frontend picks `/api/v1/...` base from JWT tenant context; no tenant sweeping.

## 7. Deployment architecture (configuration-derived; **no recorded production deployment exists**)

- `backend\Dockerfile`: multi-stage Temurin 21 build → JRE runtime, non-root uid 1001, `/actuator/health` healthcheck, G1 + `MaxRAMPercentage=75`, prod profile.
- `backend\docker-compose.yaml`: infra only (PostgreSQL 17 :5433, RabbitMQ 3, Redis 7) for local dev.
- `docker-compose.prod.yml`: full production stack `crm-production` (postgres 18, redis 8 with requirepass, backend, frontend, nginx), localhost-only exposed ports (commit `0fb9361`), healthchecks, resource limits, json-file log rotation.
- `application-prod.yml`: env-injected datasource/Hikari/RabbitMQ(Rabbit confirms)/Redis, Flyway `validate-on-migrate: true`, CORS allow-list, tunable `app.*` scheduler settings, `ProductionSecuritySecretsValidator` (prod profile start-up guard).

## 8. Architectural constraints & invariants (observed, not formally decided)

1. Every tenant-scoped write goes through JWT-injected `TenantContext.tenantId`; entities extend `TenantOwnedEntity` (non-null tenant_id).
2. Canonical events are the only domain-change broadcast mechanism (no direct message sends).
3. Public ingress exists only in vetted controllers: `/api/v1/public/**` (acquisition/forms), `/api/v1/webhooks/connectors/**`, `/api/v1/records/webhooks/**`, `/api/v1/auth/{login,refresh,register,forgot-password}` (SecurityConfig).
4. Dead-letter and failed outbox rows are operator-recovered, never auto-requeue (modules\operations + tests).
5. Tests run against a real PostgreSQL (`application-test.yaml`, localhost:5433), keeping Flyway schema the single source of truth; migration contracts asserted by `*MigrationContractTest` classes.

Related: `KNOWN_LIMITATIONS_AND_TECHNICAL_RISKS.md` (circularity and coupling caveats), `MODULE_CATALOG.md` for per-module refs.
