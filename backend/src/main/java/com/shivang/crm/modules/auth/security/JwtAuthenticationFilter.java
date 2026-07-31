package com.shivang.crm.modules.auth.security;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        /*
         * No access token was supplied.
         *
         * Continue normally because:
         * - public endpoints may not require authentication
         * - protected endpoints will later be handled by the
         *   configured AuthenticationEntryPoint and return 401
         */
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7).trim();

        if (token.isBlank()) {
            rejectUnauthorized(
                    request,
                    response,
                    new BadCredentialsException("Bearer token is empty")
            );
            return;
        }

        try {
            if (tokenBlacklistService.isBlacklisted(token)) {
                log.debug("Rejected blacklisted access token");

                rejectUnauthorized(
                        request,
                        response,
                        new BadCredentialsException("Access token has been revoked")
                );
                return;
            }

            /*
             * Ideally, isTokenValid should throw ExpiredJwtException or
             * another JwtException for invalid tokens.
             */
            if (!jwtService.isTokenValid(token)) {
                log.debug("Rejected invalid access token");

                rejectUnauthorized(
                        request,
                        response,
                        new BadCredentialsException("Invalid access token")
                );
                return;
            }

            String userId = jwtService.extractUserId(token);
            String role = jwtService.extractRole(token);

            if (userId == null || userId.isBlank()) {
                rejectUnauthorized(
                        request,
                        response,
                        new BadCredentialsException(
                                "Access token does not contain a valid user identifier"
                        )
                );
                return;
            }

            if (role == null || role.isBlank()) {
                rejectUnauthorized(
                        request,
                        response,
                        new BadCredentialsException(
                                "Access token does not contain a valid role"
                        )
                );
                return;
            }

            log.debug("Authenticated JWT userId={}, role={}", userId, role);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String authority = role.startsWith("ROLE_")
                        ? role
                        : "ROLE_" + role;

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                token,
                                Collections.singletonList(
                                        new SimpleGrantedAuthority(authority)
                                )
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException exception) {
            log.debug("Access token expired: {}", exception.getMessage());

            rejectUnauthorized(
                    request,
                    response,
                    new CredentialsExpiredException(
                            "Access token has expired",
                            exception
                    )
            );

        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("JWT processing failed: {}", exception.getMessage());

            rejectUnauthorized(
                    request,
                    response,
                    new BadCredentialsException(
                            "Invalid access token",
                            exception
                    )
            );
        }
    }

    private void rejectUnauthorized(
            HttpServletRequest request,
            HttpServletResponse response,
            RuntimeException exception
    ) throws IOException {

        SecurityContextHolder.clearContext();

        authenticationEntryPoint.commence(
                request,
                response,
                exception instanceof org.springframework.security.core.AuthenticationException
                        ? (org.springframework.security.core.AuthenticationException) exception
                        : new BadCredentialsException(
                                exception.getMessage(),
                                exception
                        )
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/forgot-password")
                || path.startsWith("/api/v1/webhooks/connectors/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/");
    }
}