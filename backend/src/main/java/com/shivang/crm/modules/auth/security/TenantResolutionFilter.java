package com.shivang.crm.modules.auth.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tenant Resolution Filter — runs AFTER JwtAuthenticationFilter.
 * Per SKILL-02 + SKILL-04: extracts tenant_id from JWT claim and
 * sets it in TenantContext (ThreadLocal).
 *
 * Filter chain order: JwtAuthFilter → TenantResolutionFilter → Controller
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TenantContext tenantContext;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getCredentials() instanceof String token) {

                String tenantId = jwtService.extractTenantId(token);
                if (tenantId != null && !tenantId.isEmpty()) {
                    tenantContext.setTenantId(tenantId);
                    log.debug("Tenant context set: {}", tenantId);
                } else {
                   // Platform user - set null (not "PLATFORM")
                tenantContext.setTenantId(null);
                log.debug("Platform user - no tenant context");
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear to prevent thread-local leaks
            tenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/refresh");
    }
}
