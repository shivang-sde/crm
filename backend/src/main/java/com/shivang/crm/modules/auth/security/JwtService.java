package com.shivang.crm.modules.auth.security;

import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs) {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    /**
     * Generate an access token with user_id, tenant_id, and role_id claims.
     * Per SKILL-04: NO permissions in JWT — only identifiers.
     */
    public String generateAccessToken(UUID userId, UUID tenantId, UUID roleId, String roleName) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);

        JwtBuilder builder = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiry);

        if (tenantId != null) {
            builder.claim("tenant_id", tenantId.toString());

        }
        builder.claim("level", tenantId == null ? "PLATFORM" : "TENANT");

        if (roleId != null) {
            builder.claim("role_id", roleId.toString());
            builder.claim("role", roleName);
        }

        return builder.signWith(signingKey).compact();
    }

    public String extractRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * Validate and parse the JWT. Returns claims if valid, throws on failure.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extract user ID (subject) from token.
     */
    public String extractUserId(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * Extract tenant_id claim from token.
     */
    public String extractTenantId(String token) {
        return parseToken(token).get("tenant_id", String.class);
    }

    /**
     * Extract role_id claim from token.
     */
    public String extractRoleId(String token) {
        return parseToken(token).get("role_id", String.class);
    }

    /**
     * Check if the token is valid (signature + not expired).
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get remaining seconds until token expiry.
     * Used for Redis blacklist TTL during logout.
     */
    public long getRemainingExpirySeconds(String token) {
        try {
            Claims claims = parseToken(token);
            long expiryMs = claims.getExpiration().getTime();
            long nowMs = System.currentTimeMillis();
            long remaining = (expiryMs - nowMs) / 1000;
            return Math.max(remaining, 0);
        } catch (JwtException e) {
            return 0;
        }
    }

    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    public String extractUserLevel(String token) {
    return parseToken(token).get("level", String.class);
    }
}
