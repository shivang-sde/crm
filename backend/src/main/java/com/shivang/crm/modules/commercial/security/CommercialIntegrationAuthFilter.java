package com.shivang.crm.modules.commercial.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.commercial.entity.CommercialApiKey;
import com.shivang.crm.modules.commercial.service.CommercialApiKeyService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommercialIntegrationAuthFilter extends OncePerRequestFilter {

    private final CommercialApiKeyService apiKeyService;
    private final TenantContext tenantContext;
    private final ObjectMapper objectMapper;

    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String HEADER_ALT = "X-Commercial-Token";
    private static final String COMMERCIAL_PREFIX = "/api/v1/integrations/commercial/";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        boolean isCommercial = path.startsWith(COMMERCIAL_PREFIX);

        // Also intercept commercial api-key management endpoints
        if (path.startsWith("/api/v1/admin/commercial/")) {
            isCommercial = true;
        }

        if (!isCommercial) {
            filterChain.doFilter(request, response);
            return;
        }

        // For commercial integration endpoints, try API-key auth first
        String rawKey = extractKey(request);

        if (rawKey == null || rawKey.isBlank()) {
            // Allow JWT for CRM user viewing PDF via GET /integrations/commercial/documents/* (no API key)
            String authHeader = request.getHeader("Authorization");
            boolean isJwt = authHeader != null && authHeader.startsWith("Bearer ") && authHeader.substring(7).trim().contains(".");
            if (isJwt && "GET".equalsIgnoreCase(request.getMethod()) && path.startsWith(COMMERCIAL_PREFIX)) {
                filterChain.doFilter(request, response);
                return;
            }
            // No API key; let JWT filter handle? But commercial endpoints require API key explicitly.
            // Allow unauthenticated to fall through so SecurityConfig returns 401 via entry point.
            // However we can optionally check if already authenticated via JWT – commercial should NOT use JWT.
            // So we reject here if no key.
            // Exception: admin key management endpoint uses JWT + admin check, so skip rejection for /admin/commercial
            if (path.startsWith("/api/v1/admin/commercial/")) {
                filterChain.doFilter(request, response);
                return;
            }
            log.warn("Commercial integration auth failure: missing API key for path {}", path);
            writeUnauthorized(response, request, "Missing X-API-Key header");
            return;
        }

        Optional<CommercialApiKey> validated = apiKeyService.validate(rawKey);
        if (validated.isEmpty()) {
            log.warn("Commercial integration auth failure: invalid API key for path {}", path);
            writeUnauthorized(response, request, "Invalid integration API key");
            return;
        }

        CommercialApiKey key = validated.get();
        // Tenant is derived from key; never trust request body/header tenantId
        tenantContext.setTenantId(key.getTenantId().toString());
        // Synthetic user identity for audit; store as integration principal
        // Use tenantId as marker; actual createdBy will be resolved to a real user in service layer
        log.info("Commercial integration authenticated for tenant {}", key.getTenantId());

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                "commercial-integration:" + key.getTenantId(),
                rawKey,
                List.of(new SimpleGrantedAuthority("ROLE_INTEGRATION"))
            );
        // Mark tenantId as details for downstream
        authentication.setDetails(key.getTenantId().toString());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Do not clear tenantContext here; TenantResolutionFilter's finally will clear.
            // But ensure SecurityContext cleared if needed handled by framework.
        }
    }

    private String extractKey(HttpServletRequest request) {
        String v = request.getHeader(HEADER_API_KEY);
        if (v != null && !v.isBlank()) return v.trim();
        v = request.getHeader(HEADER_ALT);
        if (v != null && !v.isBlank()) return v.trim();
        // Also support Authorization: Bearer <key> fallback for convenience
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            // Heuristic: commercial keys are 64 hex chars; JWTs have dots. Avoid treating JWT as API key
            if (!token.contains(".")) {
                return token;
            }
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, HttpServletRequest request, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("success", false);
        body.put("status", 401);
        body.put("error", "UNAUTHORIZED");
        body.put("message", message);
        body.put("path", request.getRequestURI());
        // Minimal FastJSON-like? Use objectMapper
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
