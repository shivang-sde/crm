# SKILL-04: Authentication & JWT Architecture

## PURPOSE
Defines the auth system for this CRM. Every agent working on auth, tokens, login flows, or security MUST follow this pattern exactly. Deviating from this creates security vulnerabilities.

---

## 1. AUTH STACK

- **Primary**: JWT + Spring Security
- **Scalable option**: Keycloak (for SSO, OAuth2, multi-tenant auth at scale)
- **Current phase**: JWT + Spring Security

---

## 2. TWO-TOKEN ARCHITECTURE (MANDATORY)

### Access Token
- **Lifetime**: 10–30 minutes
- **Storage**: In-memory only (JavaScript variable / React state)
- **Use**: Sent in every API call as `Authorization: Bearer <token>`
- **Contains**: `user_id`, `tenant_id`, `role_id` (nothing else)

### Refresh Token
- **Lifetime**: 7–30 days
- **Storage**: HTTP-only cookie (never accessible to JavaScript)
- **Use**: Used ONLY to get a new access token
- **Must be**: Rotated on every use

---

## 3. JWT PAYLOAD STRUCTURE

```json
{
  "sub": "user_id",
  "tenant_id": "tenant_uuid",
  "role_id": "role_uuid",
  "iat": 1700000000,
  "exp": 1700001800
}
```

**⚠️ DO NOT store permissions or full role data in JWT.**
Permissions are fetched server-side (cached in Redis) using `role_id`.

---

## 4. SECURITY RULES

- ✅ Store access token in memory only (not localStorage, not sessionStorage)
- ✅ Store refresh token in HTTP-only cookie
- ✅ Enable token rotation (new refresh token on every refresh)
- ✅ Implement logout invalidation (Redis blacklist or token version counter)
- ✅ Validate `tenant_id` from JWT on every request
- ❌ Never store access token in localStorage (XSS vulnerable)
- ❌ Never put sensitive permissions data in JWT payload
- ❌ Never trust `tenant_id` from request body

---

## 5. AUTH FLOW

### Login
```
POST /api/v1/auth/login
  ↓
1. Validate credentials
2. Resolve tenant (from subdomain or email domain)
3. Generate Access Token (short-lived)
4. Generate Refresh Token (long-lived)
5. Set Refresh Token in HTTP-only cookie
6. Return Access Token in response body
```

### API Request
```
Authorization: Bearer <access_token>
  ↓
1. Validate JWT signature
2. Check expiry
3. Extract user_id + tenant_id + role_id
4. Set TenantContext (server-side)
5. Load permissions from Redis cache
6. Proceed to handler
```

### Token Refresh
```
POST /api/v1/auth/refresh
Cookie: refresh_token=<token>
  ↓
1. Validate refresh token (not blacklisted, not expired)
2. Issue new access token
3. Rotate refresh token (new one set in cookie, old invalidated)
4. Return new access token
```

### Logout
```
POST /api/v1/auth/logout
  ↓
1. Invalidate refresh token (add to Redis blacklist or increment version)
2. Clear HTTP-only cookie
3. Client clears in-memory access token
```

---

## 6. SPRING SECURITY FILTER CHAIN ORDER

```
Request → JwtAuthFilter → TenantResolutionFilter → RbacFilter → Controller
```

Each filter:
1. **JwtAuthFilter**: Validates token, extracts claims
2. **TenantResolutionFilter**: Sets TenantContext from JWT or subdomain
3. **RbacFilter**: Checks module-level permissions

---

## 7. MULTI-TENANT AUTH CONSIDERATIONS

- Each tenant can have its own password policy (stored in `tenant_settings`)
- Keycloak realms map 1:1 to tenants when scaling
- SSO is available at enterprise tier via OAuth2/OIDC

---

## 8. COMMON MISTAKES — NEVER DO THESE

- ❌ Storing tokens in localStorage (XSS risk)
- ❌ Not rotating refresh tokens (replay attack risk)
- ❌ Putting permissions list in JWT (bloated + stale data)
- ❌ Using a single long-lived access token (no expiry)
- ❌ Skipping logout invalidation (tokens remain valid after logout)
- ❌ Not validating tenant_id match between JWT and requested resource
