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
 * Resolves authenticated user, tenant, role and level from the JWT.
 *
 * Filter order:
 * JwtAuthenticationFilter
 * -> TenantResolutionFilter
 * -> RbacFilter
 * -> Controller
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
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getCredentials() instanceof String token) {

                String userId = jwtService.extractUserId(token);
                String tenantId = jwtService.extractTenantId(token);
                String roleId = jwtService.extractRoleId(token);
                String role = jwtService.extractRole(token);
                String userLevel = jwtService.extractUserLevel(token);

                /*
                 * Fallback because JwtAuthenticationFilter stores
                 * the user ID as the Authentication principal.
                 */
                if ((userId == null || userId.isBlank())
                        && authentication.getPrincipal() != null) {
                    userId = authentication.getPrincipal().toString();
                }

                tenantContext.setUserId(userId);
                tenantContext.setTenantId(tenantId);
                tenantContext.setRoleId(roleId);
                tenantContext.setRole(role);
                tenantContext.setUserLevel(userLevel);

                log.info(
                        "Request context set: tenantId={}, userId={}, roleId={}, role={}, level={}",
                        tenantContext.getTenantId(),
                        tenantContext.getUserId(),
                        tenantContext.getRoleId(),
                        tenantContext.getRole(),
                        tenantContext.getUserLevel()
                );
            }

            filterChain.doFilter(request, response);

        } finally {
            tenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh");
    }
}