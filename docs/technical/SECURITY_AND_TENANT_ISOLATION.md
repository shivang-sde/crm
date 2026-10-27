# Security & Tenant Isolation

| | |
|---|---|
| **Doc ID** | CRM-TD-06 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 |
| **Evidence basis** | Direct inspection of `backend\src\main\java\com\shivang\crm\modules\{auth,rbac,commercial}\...` + migrations; no security-scan report exists (none found in repo). |

## 1. Runtime identity & token flow

- Access token: HS256 JWT (`JwtService`) — claims `sub` (userId), `tenant_id`, `level` (PLATFORM|TENANT), `role_id`, `role`. **No permission claims** (permissions always DB-resolved; refreshable rotation via `refresh_tokens` table + HTTP-only cookie scoped to `/api/v1/auth`).
- Logout → refresh-token revocation + access-token blacklist in Redis (`TokenBlacklistService`, TTL = remaining life; Redis outage mode configurable: default BYPASS / strict DENY).
- Login throttle: `LoginThrottleService`, Redis Lua `INCR`+`PEXPIRE` keyed on SHA-256(identifier)+SHA-256(IP), 5 failures → 15-min lock (`app.security.login.throttle.*`), fail-open bounded in-process fallback (10k keys) + metrics.

## 2. Filter chain (order matters)

Defined once in `auth\config\SecurityConfig.java`, ordered:

1. `CommercialIntegrationAuthFilter` — `X-API-Key` (alt `X-Commercial-Token`) for `/api/v1/integrations/commercial/**`, injects `ROLE_INTEGRATION` into `TenantContext`.
2. `JwtAuthenticationFilter` — signature + expiry verified without Redis; populates security context.
3. `TenantResolutionFilter` — copies JWT claims into `TenantContext` (skips token-injection path when `ROLE_INTEGRATION` set; skips `/auth/login` & `/auth/refresh`); clears context in `finally`.
4. `RbacFilter` — URL-derived module:action permission check for protected paths; excludes public path sets (`EXCLUDED_PATHS`, `CALLING_CONFIG_EXCLUDED_PREFIXES`, `OPERATOR_TOOLS_EXCLUDED_PREFIXES` incl. `/api/v1/operations` where method annotations are authoritative).

CSRF off (stateless tokens); CORS allow-list via `CorsProperties` (prod `application-prod.yml` stricter origin list); JSON 401/403 handlers.

## 3. RBAC model

- Tables: `roles`, `permissions`, `role_permissions`, `user_roles` (V3/V4). Permission = `module:action[:scope]`.
- Evaluation: `@PreAuthorize("@rbac.has(authentication,'module','action')")` via `RbacPermissionEvaluator` → `PermissionEvaluatorService.hasPermission(userId, tenantId, module, action)`; `@Cacheable("userPermissions")` + per-role/per-user post-commit eviction (`PermissionCacheEvictor`).
- Model: **fail-closed** — permission not in catalog ⇒ deny. `RecordScopeGuard` yields OWN/TEAM/ALL. SUPERADMIN = platform bypass via single `isSuperadmin` (centralized).
- Default role shapes (`rbac\config\DefaultRoleConfig.java`): ADMIN all; MANAGER lacks delete and `records`/`workflow`/`admin`; EMPLOYEE read/write OWN, never `assign`, limited to customer domain modules.
- New-module permission seeds ship **with** tenant-provisioning trigger updates (V64 records, V66 operations, V67/V68 entitlement, V70/V71 tickets, V74 feedback) so newly provisioned tenants inherit them; seeded idempotently.

## 4. Tenant isolation (the actual mechanism)

1. **Claims → context:** `TenantContext` is request-scoped; all services take it or use repository `tenantId` filters; cross-tenant fetches hit NOT_FOUND/forbidden per contract.
2. **Entity contract:** `TenantOwnedEntity` base (non-null `tenant_id`, owner/creator); soft-delete via `deleted`.
3. **Repository:** verified pattern `findByIdAndTenantId...`, `...AndDeletedFalse`, `Specifications` scoped filtering.
4. **DB-level safety net:** tenant role provisioning Postgres trigger (V5, fixed V50) + permission seeds executed inside triggers (`V40, V64, V66-V71, V74`).
5. **Service-shared guards:** `shared\service\CustomerRelationshipValidator` (tenant-blind FK checks), `EntityResolverService` (tenant-scoped cross-entity resolution; tested by `EntityResolverServiceTenantIsolationTest`), `RecordScopeGuard` for OWN/TEAM/ALL row scoping, `AnalyticsScopeResolver` for report scope (drill-down never widens — `INVALID_ANALYTICS_SCOPE`).
6. **Record-level nesting** [uncommitted]: nested account/contact/activities/notes handlers run parent-access guards (`assert*Accessible`) before child data access — tested by `NestedAccountContactControllerWiringTest` and `NestedAccountContactAuthorizationTest`.
7. **Async path:** attribute `tenantId` travels inside canonical events; workflow/claim services verify tenant before node execution (`WorkflowEventConsumerTest`, `EntitlementSchedulerTenantIsolationTest`).
8. **Public ingress resolution:** acquisition/forms public endpoints resolve tenant via per-tenant `publicKey`; webhook ingress via provider key mapping / record-webhook key; all HMAC/secret verified (records V55 + rotate endpoint; connectors `WebhookVerificationService`).

## 5. Encryption & secrets

- AES-256 key (`integration.security.encryption-key`, exactly 32 bytes) encrypts connector and outbound-connection credentials (`DefaultCredentialEncryptionService`). **Commercial integration API keys are NOT encrypted with this key**: they are stored as hashes and the raw key is shown only once at issuance/rotation (`CommercialIntegrationAdminController.java`, comment "Key is stored as hash; raw never persisted").
- JWT secret HS256 base64 (≥32 bytes) enforced at prod start by `ProductionSecuritySecretsValidator` (rejects defaults, never logs material).
- Redis requires auth in prod (config in `.env.production` — **not copied here**); RabbitMQ publisher confirms mandatory.

## 6. Outbound & webhook hardening

- `OutboundHttpSecurityPolicy` restricts destinations per connection; `HeaderSanitizer` + `ConnectorAuditSanitizer` scrub sensitive values from stored audit rows.
- `WebhookVerificationService` (connector HMAC) + record-webhook secrets (V55, rotation supported) verify inbound payloads; call webhook mapping uses `JsonPathValueExtractor` with allow-listed scopes.

## 7. Tests referencing security

- `auth\controller\SensitiveEndpointAuthorizationTest`, `auth\security\{LoginThrottleServiceTest, TokenBlacklistServiceTest}`, `config\ProductionSecuritySecretsValidatorTest`
- `rbac\config\{DefaultRoleConfigEmployeeScopeTest, EntitlementAssignSeedTest}`
- `deal\service\DealStageAuthorizationTest`, `entitlement\service\EntitlementAssignmentAuthorizationTest`, shared `CustomerRelationshipValidatorTest`, `EntityResolverServiceTenantIsolationTest`, activity `ActivityAccessGuardTest` + `ActivityControllerAuthorizationTest`, ticket `TicketAuthorizationTest`, `TicketAssignPermissionMigrationTest` (persistent seed), feedback `RiskSignalPersistenceIntegrationTest` (tenant/scope SQL isolation claims), records/webhook ingress test set, operations `OperatorMessageRecoverySafetyTest`.
- **Gaps:** no automated cross-tenant HTTP smoke test suite; no dependency/vulnerability scan config (`OWASP` dependency-check plugin absent); frontend permission checks untested beyond `usePermissions` usage inspection.

## 8. Known risks (records for risk register; no changes made)

- Sensitive files at repo root / backend (paths only, contents not reviewed): `.env.production`, `prod_backup.dump`, `backend\e2e-commercial-key.txt`, `backend\e2e-commercial-key-b.txt`, `backend\rbac10-tokens.json`, `backend\wf58_token.txt`, `backend\tokens_test.txt`, `docs\pashucare credential.pdf`. Not git-tracked (verified `git ls-files`, except `.env.example` which is empty). **Recommend human review: delete/rotate policy + .gitignore hardening.**
- `RbacFilter` URL-derivation exclusion lists (`OPERATOR_TOOLS_EXCLUDED_PREFIXES`) double as the authorization mechanism for `/api/v1/operations/**` via annotations — correctness depends on annotation discipline (tested by `OperatorMessageRecoverySafetyTest`-adjacent tests only).
- Login throttle & blacklist BYPASS during Redis outage are deliberate availability choices — implicit risk accepted (documented here, not in a formal ADR).
