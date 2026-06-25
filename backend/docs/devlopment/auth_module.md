1. Database Schema & Multi-Tenancy (SKILL-02)
Created the Flyway migration (V1__create_auth_tables.sql) containing the tenants, users, and refresh_tokens tables.
Added tenant_id with appropriate indexes and foreign keys, strictly enforcing tenant isolation.
Created JPA Entities (User, Tenant, RefreshToken) and extended a generic BaseEntity with id, createdAt, updatedAt for automatic auditing.
Queries in UserRepository strictly filter by tenantId whenever appropriate.
2. Authentication & JWT Architecture (SKILL-04)
JwtService: Uses jjwt to generate the short-lived access token, strictly embedding ONLY user_id, tenant_id, and role_id as claims (no permissions).
Two-Token System:
The AuthService handles returning the accessToken in the JSON response payload.
The refreshToken is generated securely (SHA-256 hashed before saving to DB) and strictly returned in an HttpOnly, SameSite=Strict cookie (ResponseCookie).
Token Rotation: On hitting /api/v1/auth/refresh, the old refresh token is revoked and a new access+refresh token pair is issued. It also includes theft detection (if a revoked token is used, all user sessions are immediately invalidated).
Logout (Redis Blacklist): Added TokenBlacklistService using RedisTemplate. The logout endpoint invalidates the refresh token in PostgreSQL and adds the unexpired access token to the Redis blacklist with a TTL exactly matching the remaining token lifetime.
3. API Standards & Error Handling (SKILL-06)
Created base wrappers like ApiResponse ({success, data, meta}) and standardized error structures ({success: false, error: {code, message}}).
Built a global @RestControllerAdvice (GlobalExceptionHandler) to translate custom exceptions (UnauthorizedException, ResourceNotFoundException, BusinessException) and security exceptions into standardized API responses and HTTP status codes (e.g., 401, 403, 404, 400).
DTOs were properly structured with jakarta.validation annotations. Used MapStruct to handle the User to UserInfo DTO mapping without exposing internal properties.
4. Security Filter Chain Configuration (SKILL-04 & SKILL-07)
JwtAuthenticationFilter: Validates incoming JWTs, checks the Redis blacklist, and sets the Spring SecurityContext.
TenantResolutionFilter: Executes immediately after the JWT filter, extracts the tenant_id claim, and safely manages it within a ThreadLocal TenantContext (guaranteeing it's cleared after the request finishes to avoid leaks).
Configured Spring Security 7/Boot 4 stateless sessions with correct route rules (/login, /refresh permitted) and integrated BCrypt.
All project dependencies (JJWT, MapStruct + Lombok annotation processor) were properly wired in pom.xml and the module compiles successfully.