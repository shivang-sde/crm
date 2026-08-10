package com.shivang.crm.modules.auth.security;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
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

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RbacFilter extends OncePerRequestFilter {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final TenantContext tenantContext;

    // List of modules that require RBAC checking (from your seed data)
    private static final Set<String> PROTECTED_MODULES = Set.of(
            "lead", "contact", "account", "deal", "activity", "entitlement",
            "report", "workflow", "user", "tenant", "call", "task", "meeting", "offering");

    // List of paths that should be excluded from RBAC (internal, public, etc.)
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/v1/auth", "/actuator", "/api/v1/public", "/swagger", "/v3/api-docs");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        log.debug("=== RBAC Filter Debug ===");
        log.debug("Request URI: {}", request.getRequestURI());
        log.debug("Request Method: {}", request.getMethod());

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
            // Extract info from request
            String method = request.getMethod();
            String path = request.getRequestURI();

            // Extract module and action from path and method
            ModuleAction moduleAction = extractModuleAction(path, method);
            String module = moduleAction.module();
            String action = moduleAction.action();

            log.debug("Extracted Module: {}, Action: {}", module, action);

            // If module is not in protected modules, skip RBAC check
            if (!isProtectedModule(module)) {
                log.debug("Module '{}' is not protected, skipping RBAC check", module);
                filterChain.doFilter(request, response);
                return;
            }

            String principal = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            UUID userId = UUID.fromString(principal);
            UUID tenantId = null;

            if (tenantContext.getTenantId() != null) {
                tenantId = tenantContext.getTenantId();
            }

            // Check permission
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
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                    "{\"success\":false,\"error\":{\"code\":\"ACCESS_DENIED\",\"message\":\"%s\"}}",
                    e.getMessage()));
            log.error("RBAC filter error", e);
        }
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

    private boolean isProtectedModule(String module) {
        return PROTECTED_MODULES.contains(module);
    }

    private ModuleAction extractModuleAction(String path, String method) {

        // Check if this is a module that needs special handling
        String module = extractModuleFromPath(path);

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