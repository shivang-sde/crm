package com.shivang.crm.modules.auth.security;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fail-closed RBAC enforcement for all authenticated /api/v1 requests.
 *
 * Every module is enforced: a request is only allowed when the resolved
 * module/action permission exists in the catalog AND is assigned to the
 * caller's role. Undefined permissions deny access (see
 * PermissionEvaluatorService). SUPERADMIN retains its platform bypass.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RbacFilter extends OncePerRequestFilter {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final TenantContext tenantContext;

    // Paths that are intentionally outside of user RBAC:
    // - auth endpoints are session/bootstrap operations available to any authenticated user
    // - actuator/swagger are infrastructure surfaces
    // - public acquisition ingestion and connector webhooks are machine-to-machine
    //   endpoints secured by provider signing secrets, not by user permissions
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/v1/auth", "/actuator", "/api/v1/public", "/api/v1/webhooks",
            "/swagger", "/v3/api-docs");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        log.debug("=== RBAC Filter Debug ===");
        log.debug("Request URI: {}", request.getRequestURI());
        log.debug("Request Method: {}", request.getMethod());

        // Skip CORS preflight; the security chain handles it.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip for public endpoints
        if (isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip for excluded paths
        if (isExcludedPath(request)) {
            log.debug("Skipping RBAC for excluded path: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String method = request.getMethod();
            String path = request.getRequestURI();

            UUID userId = extractAuthenticatedUserId();
            UUID tenantId = tenantContext.getTenantId();

            ModuleAction moduleAction = extractModuleAction(path, method);
            String module = moduleAction.module();
            String action = moduleAction.action();

            log.debug("Extracted Module: {}, Action: {}", module, action);

            // A user may always read their own role assignment. This keeps
            // session bootstrap working without re-introducing the blanket
            // admin:*read* bypass: any other role read requires admin:role_read.
            if (isSelfRoleRead(path, method, tenantContext.getRoleId())) {
                request.setAttribute("access_scope", "ALL");
                request.setAttribute("user_id", userId);
                request.setAttribute("tenant_id", tenantId);
                filterChain.doFilter(request, response);
                return;
            }

            // Fail-closed permission check
            boolean hasPermission = permissionEvaluatorService.hasPermission(
                    userId, tenantId, module, action);

            if (!hasPermission) {
                throw new AccessDeniedException(
                        "No permission for " + action + " on " + module);
            }

            // Get access scope and store in request for repository filtering
            String accessScope = permissionEvaluatorService.getAccessScope(
                    userId, tenantId, module, action);
            request.setAttribute("access_scope", accessScope);
            request.setAttribute("user_id", userId);
            request.setAttribute("tenant_id", tenantId);

            filterChain.doFilter(request, response);

        } catch (AccessDeniedException e) {
            deny(response, e.getMessage());
        } catch (Exception e) {
            // Any unexpected failure during authorization evaluation must deny
            // access rather than allow the request through.
            log.error("RBAC evaluation failed, denying request (fail-closed)", e);
            deny(response, "Authorization check failed");
        }
    }

    private UUID extractAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null) {
            throw new AccessDeniedException("Authentication required");
        }

        try {
            return UUID.fromString(authentication.getPrincipal().toString());
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Invalid authentication principal");
        }
    }

    /**
     * GET /api/v1/roles/{roleId} where {roleId} equals the caller's own role id.
     */
    private boolean isSelfRoleRead(String path, String method, UUID ownRoleId) {
        if (!"GET".equals(method) || ownRoleId == null) {
            return false;
        }
        String[] parts = path.split("/");
        return parts.length == 5
                && "roles".equals(parts[3])
                && ownRoleId.toString().equalsIgnoreCase(parts[4]);
    }

    private void deny(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"success\":false,\"error\":{\"code\":\"ACCESS_DENIED\",\"message\":\"%s\"}}",
                message));
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/login") ||
                path.startsWith("/api/v1/auth/register") ||
                path.startsWith("/api/v1/auth/refresh") ||
                path.startsWith("/api/v1/auth/forgot-password") ||
                path.startsWith("/api/v1/auth/reset-password") ||
                path.startsWith("/actuator/health");
    }

    private boolean isExcludedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    private ModuleAction extractModuleAction(String path, String method) {

        // Special handling for role endpoints
        if (path.contains("/api/v1/roles")) {
            String action;
            if (method.equals("GET")) {
                action = "role_read";
            } else {
                action = "role_manage";
            }
            return new ModuleAction("admin", action);
        }

        // Special handling for user management endpoints
        if (path.contains("/api/v1/users")) {
            if (tenantContext.getTenantId() != null) {
                return new ModuleAction("admin", "user_manage");
            }

            String action = switch (method) {
                case "GET" -> "read";
                case "POST", "PUT", "PATCH" -> "write";
                case "DELETE" -> "delete";
                default -> "read";
            };
            return new ModuleAction("user", action);
        }

        // Special handling for tenant endpoints
        if (path.contains("/api/v1/tenants")) {
            String action = switch (method) {
                case "GET" -> "read";
                case "POST", "PUT", "PATCH" -> "write";
                case "DELETE" -> "delete";
                default -> "read";
            };
            return new ModuleAction("tenant", action);
        }

        // Determine action based on HTTP method
        String action = switch (method.toUpperCase()) {
            case "GET" -> "read";
            case "POST" -> "write";
            case "PUT", "PATCH" -> "write";
            case "DELETE" -> "delete";
            default -> "read";
        };

        // Handle special actions from URL patterns
        if (path.contains("/assign"))
            action = "assign";
        if (path.contains("/export"))
            action = "export";

        String module = extractModuleFromPath(path);

        log.debug("Extracted - Module: {}, Action: {}", module, action);

        return new ModuleAction(module, action);
    }

    private String extractModuleFromPath(String path) {
        String[] parts = path.split("/");

        // Find the resource name (after /api/v1/)
        String resource = parts.length > 3 ? parts[3] : "unknown";

        // Map resource names to module names
        return switch (resource) {
            case "users", "user" -> "user";
            case "tenants", "tenant" -> "tenant";
            case "roles", "role" -> "admin";
            case "permissions", "permission" -> "admin";
            case "leads", "lead" -> "lead";
            case "contacts", "contact" -> "contact";
            case "accounts", "account" -> "account";
            case "deals", "deal" -> "deal";
            case "activities", "activity" -> "activity";
            case "calls", "call" -> "call";
            case "tasks", "task" -> "task";
            case "meetings", "meeting" -> "meeting";
            case "reports", "report" -> "report";
            case "workflows", "workflow" -> "workflow";
            // Tenant-scoped configuration endpoints belong to their parent CRM
            // module and are governed by that module's existing permissions.
            case "lead-statuses", "lead-sources", "lead-custom-fields" -> "lead";
            case "contact-custom-fields" -> "contact";
            case "account-custom-fields" -> "account";
            case "deal-stages", "deal-custom-fields" -> "deal";
            // Demo provisioning reuses workflow permissions: tenant ADMIN
            // holds workflow:read/write/delete, matching the endpoints' intent.
            case "demo-data" -> "workflow";
            // Analytics endpoints are governed by the existing report
            // permissions (report:read / report:export).
            case "analytics" -> "report";
            default -> {
                // For unknown resources, return the resource name without trailing 's'
                String result = resource.endsWith("s") ? resource.substring(0, resource.length() - 1) : resource;
                yield result;
            }
        };
    }

    record ModuleAction(String module, String action) {
    }
}
