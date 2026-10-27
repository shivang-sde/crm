# Module: Auth, Identity, RBAC, Tenant, User

| | |
|---|---|
| **Doc ID** | CRM-TD-M01 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\auth`, `modules\rbac`, `modules\tenant`, `modules\user` |
| **Evidence basis** | Repo inspection at commit `0fb9361` (files noted as modified/uncommitted in the working tree where applicable). |

## 1. Purpose & scope

Identity (login/JWT/refresh/throttle/blacklist), platform tenant provisioning, DB-backed RBAC with record scopes, tenant CRUD, user management. **Excluded:** tenant-side user profile pages belong to auth (`/auth/me`), not the user module; invitations table exists (V3) but no invitation controller was found — **status: partially implemented / unverified use**.

## 2. API surface (verified)

| Controller | Base | Endpoints |
|---|---|---|
| `AuthController` | `/api/v1/auth` | POST register, login, refresh, logout, change-password, forgot-password; GET me (reset-password listed in public paths but its route is commented out) |
| `TenantProvisioningController` | `/api/v1/tenants` | POST /provision (platform role only; checked in `TenantProvisioningServiceImpl`) |
| `TenantController` | `/api/v1/tenants` | GET, GET /{tenantId}, PUT /{tenantId} (`tenant` read/write via `@rbac.has`) |
| `UserManagementController` | `/api/v1/users` | GET, GET /managers, GET /{userId}, POST, POST /{userId}/activate, /deactivate, PUT /{userId}, DELETE /{userId} |
| `RoleManagementController` | `/api/v1/roles` | GET, GET /{roleId}, GET /{roleId}/permissions, GET /permissions, POST, POST /{roleId}/permissions, PUT /{roleId}, PUT /{roleId}/permissions/{permissionId}, PUT …/scope, DELETE variants |

Validation: DTO request objects with Bean Validation; password rules enforced in `AuthServiceImpl`. Contracts not separately documented beyond code.

## 3. Classes & infrastructure

- Filters (order enforced in `auth\config\SecurityConfig.java`): `CommercialIntegrationAuthFilter` → `JwtAuthenticationFilter` → `TenantResolutionFilter` → `RbacFilter` (all in `auth\security\`).
- `JwtService` — HS256, Base64 secret; claims: sub(userId), tenant_id, level (PLATFORM/TENANT), role_id, role; **no permission claims by design**.
- `TenantContext` (@RequestScope): tenantId, userId, roleId, role, userLevel, userEmail; helpers `isPlatformUser()/isTenantUser()/requireTenantId()`.
- `TokenBlacklistService` — Redis `blacklist:<token>` TTL = remaining life; Redis-outage mode configurable: default BYPASS / strict DENY (`rabbit`-style naming: `app.security.token-blacklist.redis-failure-mode`).
- `LoginThrottleService` — Redis Lua INCR/PEXPIRE on SHA-256(identifier)+SHA-256(IP); 5 fails → 15-min lock (`app.security.login.throttle.*`); fail-open to bounded in-process map (10k keys) with `RedisAvailabilityGuard`.
- `RbacPermissionEvaluator` + `RbacAuthorization` (`modules\rbac\security\`) bridge `@PreAuthorize("hasPermission(...)")` / `@rbac.has(...)` to `PermissionEvaluatorService.hasPermission(userId, tenantId, module, action)`; `@Cacheable("userPermissions")` + post-commit `PermissionCacheEvictor`.
- `RecordScopeGuard` (`rbac\service\`) resolves per-permission access scope OWN/TEAM/ALL; SUPERADMIN centralized bypass.
- `DefaultRoleConfig` (`rbac\config\`): ADMIN (all modules & actions), MANAGER (read/write/assign/export, no delete), EMPLOYEE (read/write at OWN, never assign). Permission catalog seeded/extended by migrations V3, V4, V27, V64, V67, V68, V70, V71, V74, including tenant-provisioning trigger updates so new tenants inherit seeds (e.g. `V5__create_tenant_role_trigger.sql`, fixed in V50; `V66__operator_message_recovery.sql` seeds `admin:operations_read/write`).

## 4. Database

- `users`, `refresh_tokens`, `tenants` (V1). `roles`, `permissions`, `role_permissions`, `user_roles`, `reseller_tenants`, `invitations` (V3).
- Refresh-token rotation persisted in DB; refresh delivered as HTTP-only cookie scoped to `/api/v1/auth` (AuthServiceImpl).

## 5. Security & tenant isolation

- Tenant isolation at identity layer: claims → `TenantContext`; repositories query by tenantId; user-visibility scoped (see `UserManagementService`).
- Public/unauthenticated: `/api/v1/auth/{login,refresh,register,forgot-password}` only (SecurityConfig permit list).
- `ProductionSecuritySecretsValidator` (prod profile, `@PostConstruct`): rejects default/weak `jwt.secret` (≥32B) & `integration.security.encryption-key` (32B AES). Never logs secret material.
- Known hardening state: `docs\hardening-fixes.md.txt` memo (informational; fixes tracked in `KNOWN_LIMITATIONS_AND_TECHNICAL_RISKS.md`).

## 6. Tests

- `auth\config\ProductionSecuritySecretsValidatorTest`, `auth\controller\SensitiveEndpointAuthorizationTest`, `auth\security\LoginThrottleServiceTest`, `auth\security\TokenBlacklistServiceTest`, `rbac\config\DefaultRoleConfigEmployeeScopeTest`, `rbac\config\EntitlementAssignSeedTest` (all in `backend\src\test\java`).
- **Verification gaps:** no end-to-end auth-flow test (login→refresh→logout against real Postgres) in this tree; `tenant` and `user` modules have **no tests**. Test execution status: see `TESTING_AND_VERIFICATION_BASELINE.md`.

## 7. Dependencies

- auth → rbac (roles for login), user module depends on rbac/auth entities; rbac → Redis + migrations; tenant provisioning → rbac seeds.

## 8. Known limitations / risks

- Token blacklist BYPASS default is a deliberate availability trade-off — revoked tokens live until natural expiry if Redis is down (documented in code, config only).
- `invitation` flow unimplemented (table only).
- `POST /api/v1/auth/reset-password` referenced in permit list while route commented out in `AuthController` — public surface smaller than permit list (harmless but should be reconciled).
